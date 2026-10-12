package com.ourlittlelife.game.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ourlittlelife.game.data.WorldSource
import com.ourlittlelife.game.day.describeShortfall
import com.ourlittlelife.rules.Action
import com.ourlittlelife.rules.Activity
import com.ourlittlelife.rules.ActionResult
import com.ourlittlelife.rules.BuildResult
import com.ourlittlelife.rules.Building
import com.ourlittlelife.rules.Camp
import com.ourlittlelife.rules.Character
import com.ourlittlelife.rules.Furniture
import com.ourlittlelife.rules.Gesture
import com.ourlittlelife.rules.FurnitureActions
import com.ourlittlelife.rules.FurnitureKind
import com.ourlittlelife.rules.FurniturePlacement
import com.ourlittlelife.rules.PlaceResult
import com.ourlittlelife.rules.RaceOutcome
import com.ourlittlelife.rules.Races
import com.ourlittlelife.rules.Interiors
import com.ourlittlelife.rules.ObjectActions
import com.ourlittlelife.rules.Placement
import com.ourlittlelife.rules.Player
import com.ourlittlelife.rules.Plot
import com.ourlittlelife.rules.PlotObject
import com.ourlittlelife.rules.Resource
import com.ourlittlelife.rules.Room
import com.ourlittlelife.rules.StarterActions
import com.ourlittlelife.rules.Terrain
import com.ourlittlelife.rules.Together
import com.ourlittlelife.rules.RoomActions
import com.ourlittlelife.rules.RoomKind
import com.ourlittlelife.rules.UseResult
import com.ourlittlelife.rules.Walking
import com.ourlittlelife.rules.Walks
import com.ourlittlelife.rules.World
import com.ourlittlelife.rules.Worlds
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** A piece of furniture in hand while furnishing: a new one to buy or make, or one being [moving]. */
data class Holding(val kind: FurnitureKind, val x: Int, val y: Int, val moving: String? = null)

/** What the player tapped on the plot. */
sealed interface Selection {
    data class Thing(val obj: PlotObject) : Selection
    data class InRoom(val room: Room) : Selection
    data class Ground(val x: Int, val y: Int) : Selection
    data class Person(val playerId: String) : Selection
    data class Piece(val room: Room, val piece: Furniture) : Selection
}

/**
 * One player's view of a shared world. The world lives in Firestore; every change runs the
 * game rules on the latest saved world inside a transaction, so both phones always agree.
 * Walking is shown step by step on this phone and saved once, on arrival.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private lateinit var source: WorldSource
    private var myId = ""

    /** True for the solo game on this phone; false for a shared world. */
    val isSolo: Boolean get() = source.solo

    var world by mutableStateOf<World?>(null)
        private set

    /** True once the world has loaded and this player's character is in it. */
    val ready: Boolean get() = world?.players?.containsKey(myId) == true

    private val me: Player get() = world!!.player(myId)
    val camp: Camp get() = world!!.camp
    val plot: Plot get() = camp.plot
    val character: Character get() = me.character
    val worldCode: String get() = world!!.code

    /** Where this player is drawn. Runs ahead of the saved position while walking. */
    var characterTile by mutableStateOf(0 to 0)
        private set

    val partner: Player? get() = world?.players?.values?.firstOrNull { it.id != myId }
    val partnerName: String? get() = partner?.character?.name

    /** Where the partner is drawn: their moves arrive as a new position and are played back as a walk. */
    var partnerTile by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** The host wears the first outfit, whoever joins wears the second. */
    fun outfitFor(playerId: String): List<Sprite> =
        if (world?.players?.keys?.firstOrNull() == playerId) Sprites.jo else Sprites.maya

    val myOutfit: List<Sprite> get() = outfitFor(myId)

    var selection by mutableStateOf<Selection?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var buildMode by mutableStateOf(false)
        private set

    /** True while the night sky is open after choosing "Look at the stars". */
    var stargazing by mutableStateOf(false)
        private set

    /** Anyone in this world, for the person card. */
    fun playerFor(id: String): Player? = world?.players?.get(id)

    val myPlayerId: String get() = myId

    /** Social that closing the sky would add right now: more with company, most if they're stargazing too. */
    fun stargazeSocialPreview(): Int = world?.let { Together.socialBonus(it, it.player(myId).copy(tileX = characterTile.first, tileY = characterTile.second)) } ?: 0

    /** The room you're inside, or null when you're outdoors. */
    val insideRoom: Room? get() = world?.takeIf { ready }?.let { w -> me.inside?.let { inside -> w.camp.plot.rooms.firstOrNull { it.id == inside.roomId } } }

    /** Where you're drawn inside the room. Runs ahead of the saved spot while walking. */
    var insideTile by mutableStateOf(0 to 0)
        private set

    /** Your partner, if they're inside the same room as you. */
    val partnerInHere: Player? get() = partner?.takeIf { it.inside != null && it.inside?.roomId == me.inside?.roomId }

    /** True while the furniture tray is open inside a room. */
    var furnishing by mutableStateOf(false)
        private set

    /** The piece being placed or moved, if any. */
    var holding by mutableStateOf<Holding?>(null)
        private set

    /** True while the race screen is open. */
    var racing by mutableStateOf(false)
        private set

    /** How the lap just run went, shown at the finish. */
    var lastRace by mutableStateOf<RaceOutcome?>(null)
        private set

    val myBestLap: Long? get() = world?.takeIf { ready }?.let { me.bestLapMillis }

    val startStamina: Int get() = Races.stamina(character)

    /** True while walking the forest loop; the map follows you round it. */
    var touring by mutableStateOf(false)
        private set

    /** Your partner, if they've asked you for a walk and are waiting for an answer. */
    val walkInviteFrom: Player? get() = world?.takeIf { ready }?.let { Walks.waitingInvite(it, myId) }

    /** True while you've asked your partner for a walk and they haven't answered. */
    val askedForWalk: Boolean get() = ready && me.activity == Activity.WALK_INVITE

    private var clearedStaleActivity = false

    private var walkJob: Job? = null
    private var partnerJob: Job? = null
    private var attached = false

    fun attach(source: WorldSource, uid: String) {
        if (attached) return
        attached = true
        this.source = source
        myId = uid
        viewModelScope.launch {
            source.observe()
                .catch { message = "Lost touch with your world. Check your connection." }
                .collect(::onWorld)
        }
    }

    private fun onWorld(next: World) {
        val before = world?.takeIf { ready }?.player(myId)
        val previousDay = before?.character?.clock?.day
        world = next
        if (!ready) return

        // Someone greeted you since the last update.
        val now = me
        val greeting = now.greeting
        if (before != null && greeting != null && greeting.count > (before.greeting?.count ?: 0)) {
            val who = next.players[greeting.from]?.character?.name ?: "Someone"
            message = "$who ${greeting.gesture.past}."
        }

        // Your partner said yes to your walk: play it here too, with them leading.
        if (before?.activity == Activity.WALK_INVITE && now.activity == null && now.tile == Walks.BEHIND_END &&
            now.character.clock.minute > before.character.clock.minute
        ) {
            message = "${partnerName ?: "Your friend"} said yes. A walk round the forest: +12 fun, +${Walks.TOGETHER_SOCIAL} social."
            tour(withPartner = true, iLead = false, from = before.tile)
        }

        if (walkJob?.isActive != true) {
            characterTile = me.tile
            me.inside?.let { insideTile = it.tile }
        }

        // An activity left over from closing the app mid-moment is cleared once, so the partner isn't misled.
        if (!clearedStaleActivity) {
            clearedStaleActivity = true
            if (me.activity != null && !stargazing) commit { w -> w.withPlayer(w.player(myId).copy(activity = null)) }
        }
        val other = partner
        if (other == null) partnerTile = null else if (!touring) showPartnerAt(other.tile)

        val day = me.character.clock.day
        if (previousDay != null && day > previousDay) {
            message = "Good morning. Day $day has begun, and everything has grown back."
        }

        // If one of you has been waiting more than a day for the other, autopilot covers the missing day.
        val clockNow = System.currentTimeMillis()
        if (Worlds.autopilot(next, clockNow) != next) commit { Worlds.autopilot(it, clockNow) }
    }

    /** Plays the partner's move as a walk along the route they'd have taken. */
    private fun showPartnerAt(target: Pair<Int, Int>) {
        val from = partnerTile
        if (from == null || from == target) {
            partnerTile = target
            return
        }
        val route = Walking.path(plot, from, target, occupied = setOf(characterTile)) ?: listOf(target)
        partnerJob?.cancel()
        partnerJob = viewModelScope.launch {
            for (step in route) {
                partnerTile = step
                delay(WALK_STEP_MILLIS)
            }
        }
    }

    /**
     * Runs [change] on this phone straight away, then on the latest saved world in a transaction.
     * The saved result replaces the local guess, so a change that no longer applies quietly disappears.
     */
    private fun commit(onSaved: (applied: Boolean) -> Unit = {}, change: (World) -> World?) {
        val local = world ?: return
        change(local)?.let { world = it }
        viewModelScope.launch {
            try {
                // A transaction may run more than once; the last run is the one that counts.
                val applied = AtomicBoolean(false)
                val saved = source.update { w -> change(w).also { applied.set(it != null) } }
                world = saved
                onSaved(applied.get())
            } catch (e: Exception) {
                message = "Couldn't save that. Check your connection and try again."
            }
        }
    }

    private fun occupied(): Set<Pair<Int, Int>> = setOfNotNull(partnerTile)

    fun actionsFor(obj: PlotObject): List<Action> = ObjectActions.forKind(obj.kind)

    fun actionsFor(room: Room): List<Action> = RoomActions.forKind(room.kind)

    /** The rooms worth offering on empty ground: just the cabin until it's built, then everything not built yet. */
    fun buildOptions(): List<RoomKind> =
        if (!camp.hasRoof) listOf(RoomKind.CABIN) else RoomKind.entries.filter { kind -> plot.rooms.none { it.kind == kind } }

    fun placement(kind: RoomKind, x: Int, y: Int): Placement = Building.check(plot, kind, x, y)

    fun toggleBuildMode() {
        buildMode = !buildMode
        selection = null
    }

    /** Taps: things open their actions; open ground and room floors are walked to (or built on, in build mode). */
    fun tapTile(x: Int, y: Int) {
        if (!ready || x !in 0 until plot.size || y !in 0 until plot.size) return
        val thing = plot.objectAt(x, y)
        val room = plot.roomAt(x, y)
        val tapped = x to y
        when {
            tapped == characterTile -> selection = Selection.Person(myId)
            partner != null && tapped == partnerTile -> selection = Selection.Person(partner!!.id)
            thing != null -> selection = Selection.Thing(thing)
            plot.terrainAt(x, y) == Terrain.WATER -> message = "The pond. Fishing is on the way."
            plot.terrainAt(x, y) == Terrain.TRACK -> message = "The racecourse. Run a lap from the sign at the gate."
            buildMode && room == null -> selection = Selection.Ground(x, y)
            character.asleep -> message = "You're asleep. The day starts when everyone has gone to bed."
            else -> walkTo(x to y, saveOnArrival = true) {
                if (room != null && actionsFor(room).isNotEmpty()) selection = Selection.InRoom(room)
            }
        }
    }

    /** A quick hello: wave, high five or hug. */
    fun greet(gesture: Gesture) {
        selection = null
        val name = partnerName ?: return
        val counts = gesture !in me.gesturesToday
        message = when (gesture) {
            Gesture.WAVE -> "You waved at $name."
            Gesture.HIGH_FIVE -> "High five with $name."
            Gesture.HUG -> "You hugged $name."
        } + if (counts) " +${gesture.social} social." else ""
        commit(onSaved = { applied -> if (!applied) message = "$name is out of reach now." }) { w -> Together.greet(here(w), myId, gesture) }
    }

    /** Why a gesture can't happen right now, or null if it can. */
    fun gestureProblem(gesture: Gesture): String? {
        val other = partner ?: return "Nobody here yet"
        val apart = Together.distance(here(world!!).player(myId), other)
        return when {
            character.asleep -> "You're asleep"
            other.character.asleep -> "${other.character.name} is asleep"
            apart == null -> "You're not in the same place"
            apart > gesture.reach -> if (gesture.reach <= 1) "Stand right next to them" else "Get a bit closer"
            else -> null
        }
    }

    fun gestureUsedToday(gesture: Gesture): Boolean = ready && gesture in me.gesturesToday

    /** The world with this player where they're drawn right now, which runs ahead of the save while walking. */
    private fun here(w: World): World {
        val p = w.player(myId)
        return if (p.inside != null) Interiors.moveTo(w, myId, insideTile)
        else w.withPlayer(p.copy(tileX = characterTile.first, tileY = characterTile.second))
    }

    /** Why you can't ask for a walk right now, or null if you can. */
    fun walkProblem(): String? {
        val other = partner ?: return "Nobody to walk with yet"
        return when {
            character.asleep -> "You're asleep"
            other.character.asleep -> "${other.character.name} is asleep"
            else -> null
        }
    }

    /** Steps inside a room you've built. */
    fun goInside(room: Room) {
        selection = null
        if (character.asleep) return
        walkJob?.cancel()
        insideTile = Interiors.door(room.kind)
        commit { w -> Interiors.enter(w, myId, room.id) }
    }

    /** Back out onto the doorstep. */
    fun goOutside() {
        selection = null
        furnishing = false
        holding = null
        walkJob?.cancel()
        val room = insideRoom ?: return
        val step = Interiors.doorstep(room)
        if (Walking.isWalkable(plot, step)) characterTile = step
        commit { w -> Interiors.leave(w, myId) }
    }

    /** Taps inside: people open their card, furniture opens what it offers, the floor is walked to. */
    fun tapInside(x: Int, y: Int) {
        val room = insideRoom ?: return
        val tapped = x to y
        val piece = Interiors.furnitureAt(room, x, y)
        // While furnishing, a tap moves the piece in hand there, or picks up a piece to move or put away.
        if (furnishing) {
            val held = holding
            when {
                held != null -> holding = held.copy(x = x, y = y)
                piece != null -> selection = Selection.Piece(room, piece)
            }
            return
        }
        when {
            tapped == insideTile -> selection = Selection.Person(myId)
            partnerInHere?.inside?.tile == tapped -> selection = Selection.Person(partner!!.id)
            piece != null && actionsFor(piece).isNotEmpty() -> selection = Selection.Piece(room, piece)
            !Interiors.isFree(room, tapped) -> Unit
            character.asleep -> message = "You're asleep. The day starts when everyone has gone to bed."
            else -> walkInside(room, tapped) { commit { w -> Interiors.moveTo(w, myId, tapped) } }
        }
    }

    private fun walkInside(room: Room, target: Pair<Int, Int>, onArrival: () -> Unit) {
        val occupied = setOfNotNull(partnerInHere?.inside?.tile)
        val route = Interiors.path(room, insideTile, target, occupied) ?: return
        walkJob?.cancel()
        walkJob = viewModelScope.launch {
            for (step in route) {
                insideTile = step
                delay(WALK_STEP_MILLIS)
            }
            onArrival()
        }
    }

    fun actionsFor(piece: Furniture): List<Action> = FurnitureActions.forKind(piece.kind)

    /** Walks over to the piece of furniture, then uses it. */
    fun perform(room: Room, piece: Furniture, action: Action) {
        selection = null
        if (character.asleep) {
            message = refusal(ActionResult.Asleep)
            return
        }
        val spot = Interiors.spotBeside(room, piece, insideTile, setOfNotNull(partnerInHere?.inside?.tile)) ?: insideTile
        val company = piece.kind in FurnitureActions.shared && partnerInHere?.character?.asleep == false
        walkInside(room, spot) {
            commit(onSaved = { applied ->
                if (applied && company) message = "Nicer with ${partnerName ?: "company"}. +${Interiors.COMPANY_SOCIAL} social."
            }) { w -> Interiors.use(Interiors.moveTo(w, myId, spot), myId, piece.id, action) }
        }
    }

    /** Opens or closes the furniture tray. */
    fun toggleFurnishing() {
        furnishing = !furnishing
        holding = null
        selection = null
    }

    /** Picks a new piece from the tray; it appears in the middle of the room, ready to be tapped into place. */
    fun pick(kind: FurnitureKind) {
        val room = insideRoom ?: return
        holding = Holding(kind, (Interiors.width(room.kind) - kind.width) / 2, (Interiors.height(room.kind) - kind.height) / 2 - 1)
    }

    /** Picks up a piece that's already in the room, to put it somewhere else. */
    fun startMoving(piece: Furniture) {
        selection = null
        holding = Holding(piece.kind, piece.x, piece.y, moving = piece.id)
    }

    fun cancelHolding() {
        holding = null
    }

    /** Whether the piece in hand fits where it is. */
    fun holdingPlacement(): FurniturePlacement {
        val room = insideRoom ?: return FurniturePlacement.OffFloor
        val held = holding ?: return FurniturePlacement.OffFloor
        val standing = setOfNotNull(insideTile, partnerInHere?.inside?.tile)
        return Interiors.check(room, held.kind, held.x, held.y, moving = held.moving, occupied = standing)
    }

    /** Puts the piece in hand down: paid for if it's new, free if it's being moved. */
    fun placeHeld() {
        val room = insideRoom ?: return
        val held = holding ?: return
        val result = if (held.moving != null) Interiors.move(world!!, room.id, held.moving, held.x, held.y)
        else Interiors.place(world!!, room.id, held.kind, held.x, held.y)
        when (result) {
            is PlaceResult.Done -> {
                holding = null
                message = if (held.moving != null) "Moved." else "The ${held.kind.label.lowercase()} is in."
                commit { w ->
                    val fresh = if (held.moving != null) Interiors.move(w, room.id, held.moving, held.x, held.y)
                    else Interiors.place(w, room.id, held.kind, held.x, held.y)
                    (fresh as? PlaceResult.Done)?.world
                }
            }
            is PlaceResult.Missing -> message = describeShortfall(result.shortfall) + " in the chest."
            is PlaceResult.CantPlace -> message = "That spot doesn't work. Try somewhere else."
        }
    }

    /** Takes a piece out of the room. What it cost goes back in the chest. */
    fun putAway(piece: Furniture) {
        selection = null
        val room = insideRoom ?: return
        message = "Put away. Its cost is back in the chest."
        commit { w -> Interiors.putAway(w, room.id, piece.id).takeIf { it != w } }
    }

    fun dismissSelection() {
        selection = null
    }

    /** Walks step by step along the shortest route, then runs [onArrival]. A new tap cancels a walk. Walking is free. */
    private fun walkTo(target: Pair<Int, Int>, saveOnArrival: Boolean, onArrival: () -> Unit = {}) {
        val route = Walking.path(plot, characterTile, target, occupied = occupied())
        if (route == null) {
            message = "You can't get there."
            return
        }
        walkJob?.cancel()
        walkJob = viewModelScope.launch {
            for (step in route) {
                characterTile = step
                delay(WALK_STEP_MILLIS)
            }
            if (saveOnArrival) {
                val (x, y) = target
                commit { w -> w.withPlayer(w.player(myId).copy(tileX = x, tileY = y)) }
            }
            onArrival()
        }
    }

    /** Checks the action first, so you never walk over for something that can't happen, then walks over and does it. */
    fun perform(obj: PlotObject, action: Action) {
        selection = null
        when (val result = camp.use(character, obj, action)) {
            is UseResult.Done -> {
                val spot = Walking.spotBeside(plot, obj, characterTile, occupied = occupied())
                if (spot == null) {
                    message = "You can't reach the ${obj.kind.label.lowercase()}."
                    return
                }
                if (action == StarterActions.stargaze) {
                    walkTo(spot, saveOnArrival = false) { startStargazing(spot) }
                    return
                }
                if (action == Walks.alone) {
                    walkAlone(obj)
                    return
                }
                if (action == Races.lap) {
                    walkTo(spot, saveOnArrival = true) { racing = true }
                    return
                }
                walkTo(spot, saveOnArrival = false) {
                    val here = world!!.let { w -> w.withPlayer(w.player(myId).copy(tileX = spot.first, tileY = spot.second)) }
                    val bonus = Together.companyBonus[action]?.takeIf { Together.hasCompany(here, here.player(myId)) }
                    commit(onSaved = { applied -> reportUse(applied, obj, action, bonus) }) { w ->
                        Together.use(w.withPlayer(w.player(myId).copy(tileX = spot.first, tileY = spot.second)), myId, obj, action)
                    }
                }
            }
            UseResult.UsedUp ->
                message = "That ${obj.kind.label.lowercase()} needs to grow back. Try another, or come back tomorrow."
            is UseResult.Missing ->
                message = describeShortfall(result.shortfall) + " in the chest."
            is UseResult.Refused -> message = refusal(result.reason)
        }
    }

    private fun reportUse(applied: Boolean, obj: PlotObject, action: Action, bonus: Int? = null) {
        val yielded = action.yields.entries.firstOrNull()
        message = when {
            !applied -> "${partnerName ?: "Someone"} got to the ${obj.kind.label.lowercase()} first."
            bonus != null -> "Better with ${partnerName ?: "company"}. +$bonus social."
            yielded?.key == Resource.COINS -> "A day's work done. +${yielded.value} coins."
            yielded != null -> "+${yielded.value} ${yielded.key.label.lowercase()} in the chest."
            else -> null
        }
    }

    fun perform(room: Room, action: Action) {
        selection = null
        val check = camp.useRoom(character, room, action)
        if (check is UseResult.Refused) {
            message = refusal(check.reason)
            return
        }
        commit { w ->
            val p = w.player(myId)
            when (val fresh = w.camp.useRoom(p.character, room, action)) {
                is UseResult.Done -> w.copy(camp = fresh.camp).withPlayer(p.copy(character = fresh.character))
                else -> null
            }
        }
    }

    fun build(kind: RoomKind, x: Int, y: Int) {
        when (val check = camp.build(character, kind, x, y)) {
            is BuildResult.Done -> {
                selection = null
                buildMode = false
                message = if (kind == RoomKind.CABIN) "The cabin is finished. Tonight you sleep in a real bed." else "The ${kind.label.lowercase()} is finished."
                commit { w ->
                    val p = w.player(myId)
                    when (val fresh = w.camp.build(p.character, kind, x, y)) {
                        is BuildResult.Done -> w.copy(camp = fresh.camp).withPlayer(p.copy(character = fresh.character))
                        else -> null
                    }
                }
            }
            is BuildResult.Missing -> message = describeShortfall(check.shortfall) + " in the chest."
            is BuildResult.CantPlace -> message = "That spot doesn't work. Try somewhere else."
            is BuildResult.Refused -> message = refusal(check.reason)
        }
    }

    /** Going to bed. When everyone is asleep, the next day starts for both of you. */
    fun sleep() {
        selection = null
        walkJob?.cancel()
        val now = System.currentTimeMillis()
        val name = partnerName
        message = if (name != null) "Sleep well. The day starts when $name goes to bed too." else null
        commit { w -> Worlds.advanceIfReady(Worlds.sleep(w, myId, now)) }
    }

    /** Opens the sky and shows the partner you're stargazing, so they can come and join. */
    private fun startStargazing(spot: Pair<Int, Int>) {
        stargazing = true
        commit { w -> Together.startStargazing(w.withPlayer(w.player(myId).copy(tileX = spot.first, tileY = spot.second)), myId) }
    }

    /** Closing the sky: the hour passes, fun goes up, and social too if someone was with you. */
    fun finishStargazing() {
        stargazing = false
        val social = stargazeSocialPreview()
        val company = partnerName
        commit(onSaved = { applied ->
            message = when {
                !applied -> "You're asleep. The sky will be there tomorrow night."
                social > 0 && company != null -> "A lovely night with $company. +20 fun, +$social social."
                else -> "What a sky. +20 fun."
            }
        }) { w -> Together.stargaze(w, myId) }
    }

    /** A lap is over: show how it went straight away, and save it. */
    fun finishLap(millis: Long) {
        val local = world ?: return
        lastRace = Races.finish(local, myId, millis)
        commit { w -> Races.finish(w, myId, millis)?.world }
    }

    fun raceAgain() {
        lastRace = null
    }

    fun closeRace() {
        racing = false
        lastRace = null
    }

    /** Asks your partner for a walk. They see the invite on their map. */
    fun askForWalk() {
        selection = null
        val name = partnerName ?: return
        message = "You asked $name for a walk. They'll see it on their map."
        commit(onSaved = { applied -> if (!applied) message = "$name can't walk right now." }) { w -> Walks.invite(w, myId) }
    }

    /** "Not now" to their invite, or calling off your own. */
    fun declineWalk() {
        commit { w -> Walks.decline(w, myId).takeIf { it != w } }
    }

    /** Saying yes: you both head round the forest loop, you in front. */
    fun acceptWalk() {
        val local = world ?: return
        if (Walks.accept(local, myId) == null) {
            message = "That walk isn't on any more."
            return
        }
        val from = characterTile
        val name = partnerName ?: "your friend"
        commit(onSaved = { applied -> if (!applied) message = "That walk isn't on any more." }) { w -> Walks.accept(w, myId) }
        message = "Off round the forest with $name. +12 fun, +${Walks.TOGETHER_SOCIAL} social."
        tour(withPartner = true, iLead = true, from = from)
    }

    /** From the trailhead sign: round the loop on your own, then the hour is spent. */
    private fun walkAlone(sign: PlotObject) {
        tour(withPartner = false, iLead = true, from = characterTile) {
            commit(onSaved = { applied -> message = if (applied) "A quiet walk in the forest. +12 fun." else "You're asleep. The trail will be there tomorrow." }) { w ->
                val p = w.player(myId)
                when (val fresh = w.camp.use(p.character, sign, Walks.alone)) {
                    is UseResult.Done -> w.copy(camp = fresh.camp)
                        .withPlayer(p.copy(character = fresh.character, tileX = Walks.END.first, tileY = Walks.END.second))
                    else -> null
                }
            }
        }
    }

    /**
     * Plays a walk round the forest loop, ending back at the trailhead. With a partner, the
     * leader goes first and the other follows a step behind; both start from where they stand.
     */
    private fun tour(withPartner: Boolean, iLead: Boolean, from: Pair<Int, Int>, onDone: () -> Unit = {}) {
        val partnerFrom = partnerTile ?: from
        val leaderFrom = if (iLead) from else partnerFrom
        val followerFrom = if (iLead) partnerFrom else from
        val start = Walks.trail.first()
        val leader = (Walking.path(plot, leaderFrom, start) ?: listOf(start)) +
            Walks.trail.drop(1) + start + (Walking.path(plot, start, Walks.END) ?: listOf(Walks.END))
        val follower = (Walking.path(plot, followerFrom, leaderFrom) ?: emptyList()) + leaderFrom + leader.dropLast(1)
        // Line the two routes up at the end, so whoever is closer waits at the start for the other.
        val steps = if (withPartner) maxOf(leader.size, follower.size) else leader.size
        fun at(route: List<Pair<Int, Int>>, origin: Pair<Int, Int>, i: Int) = route.getOrNull(i - (steps - route.size)) ?: origin

        walkJob?.cancel()
        partnerJob?.cancel()
        touring = true
        walkJob = viewModelScope.launch {
            try {
                for (i in 0 until steps) {
                    characterTile = at(if (iLead) leader else follower, from, i)
                    if (withPartner) partnerTile = at(if (iLead) follower else leader, partnerFrom, i)
                    delay(WALK_STEP_MILLIS)
                }
            } finally {
                touring = false
            }
            if (ready) {
                characterTile = if (withPartner) me.tile else Walks.END
                partner?.let { showPartnerAt(it.tile) }
            }
            onDone()
        }
    }

    fun clearMessage() {
        message = null
    }

    /** Debug builds only: skip the grind when testing building. */
    fun debugFillChest() {
        message = "Debug: +100 of everything."
        commit { w -> w.copy(camp = w.camp.copy(storage = w.camp.storage.plus(Resource.entries.associateWith { 100 }))) }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun refusal(reason: ActionResult): String = "You're asleep. The day starts when everyone has gone to bed."
}

/** How long each step takes. The canvas glides a character across a tile in exactly this time. */
const val WALK_STEP_MILLIS = 140L
