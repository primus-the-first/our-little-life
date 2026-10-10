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
import com.ourlittlelife.rules.ActionResult
import com.ourlittlelife.rules.BuildResult
import com.ourlittlelife.rules.Building
import com.ourlittlelife.rules.Camp
import com.ourlittlelife.rules.Character
import com.ourlittlelife.rules.ObjectActions
import com.ourlittlelife.rules.Placement
import com.ourlittlelife.rules.Player
import com.ourlittlelife.rules.Plot
import com.ourlittlelife.rules.PlotObject
import com.ourlittlelife.rules.Resource
import com.ourlittlelife.rules.Room
import com.ourlittlelife.rules.RoomActions
import com.ourlittlelife.rules.RoomKind
import com.ourlittlelife.rules.UseResult
import com.ourlittlelife.rules.Walking
import com.ourlittlelife.rules.World
import com.ourlittlelife.rules.Worlds
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** What the player tapped on the plot. */
sealed interface Selection {
    data class Thing(val obj: PlotObject) : Selection
    data class InRoom(val room: Room) : Selection
    data class Ground(val x: Int, val y: Int) : Selection
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
    var characterTile by mutableStateOf(11 to 11)
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
        val previousDay = world?.takeIf { ready }?.player(myId)?.character?.clock?.day
        world = next
        if (!ready) return

        if (walkJob?.isActive != true) characterTile = me.tile
        val other = partner
        if (other == null) partnerTile = null else showPartnerAt(other.tile)

        val day = me.character.clock.day
        if (previousDay != null && day > previousDay) {
            message = "Good morning. Day $day has begun, and everything has grown back."
        }

        // If one of you has been waiting more than a day for the other, autopilot covers the missing day.
        val now = System.currentTimeMillis()
        if (Worlds.autopilot(next, now) != next) commit { Worlds.autopilot(it, now) }
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
        when {
            thing != null -> selection = Selection.Thing(thing)
            buildMode && room == null -> selection = Selection.Ground(x, y)
            character.asleep -> message = "You're asleep. The day starts when everyone has gone to bed."
            else -> walkTo(x to y, saveOnArrival = true) {
                if (room != null && actionsFor(room).isNotEmpty()) selection = Selection.InRoom(room)
            }
        }
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
                walkTo(spot, saveOnArrival = false) {
                    commit(onSaved = { applied -> reportUse(applied, obj, action) }) { w ->
                        val p = w.player(myId)
                        when (val fresh = w.camp.use(p.character, obj, action)) {
                            is UseResult.Done -> w.copy(camp = fresh.camp)
                                .withPlayer(p.copy(character = fresh.character, tileX = spot.first, tileY = spot.second))
                            else -> null
                        }
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

    private fun reportUse(applied: Boolean, obj: PlotObject, action: Action) {
        val yielded = action.yields.entries.firstOrNull()
        message = when {
            !applied -> "${partnerName ?: "Someone"} got to the ${obj.kind.label.lowercase()} first."
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

    fun clearMessage() {
        message = null
    }

    /** Debug builds only: skip the grind when testing building. */
    fun debugFillChest() {
        message = "Debug: +100 of everything."
        commit { w -> w.copy(camp = w.camp.copy(storage = w.camp.storage.plus(Resource.entries.associateWith { 100 }))) }
    }

    private fun refusal(reason: ActionResult): String = when (reason) {
        is ActionResult.NotEnoughTime -> "Not enough time left today for that. Try something shorter, or go to bed."
        else -> "You're asleep. The day starts when everyone has gone to bed."
    }
}

/** How long each step takes. The canvas glides a character across a tile in exactly this time. */
const val WALK_STEP_MILLIS = 140L
