package com.ourlittlelife.game.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ourlittlelife.rules.SaveGame
import com.ourlittlelife.rules.Walking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ourlittlelife.game.day.describeShortfall
import com.ourlittlelife.rules.Action
import com.ourlittlelife.rules.ActionResult
import com.ourlittlelife.rules.BuildResult
import com.ourlittlelife.rules.Building
import com.ourlittlelife.rules.Camp
import com.ourlittlelife.rules.Character
import com.ourlittlelife.rules.ObjectActions
import com.ourlittlelife.rules.Placement
import com.ourlittlelife.rules.Plot
import com.ourlittlelife.rules.PlotObject
import com.ourlittlelife.rules.Resource
import com.ourlittlelife.rules.Room
import com.ourlittlelife.rules.RoomActions
import com.ourlittlelife.rules.RoomKind
import com.ourlittlelife.rules.Rules
import com.ourlittlelife.rules.StarterPlot
import com.ourlittlelife.rules.UseResult

/** What the player tapped on the plot. */
sealed interface Selection {
    data class Thing(val obj: PlotObject) : Selection
    data class InRoom(val room: Room) : Selection
    data class Ground(val x: Int, val y: Int) : Selection
}

/**
 * Phase 1 prototype: one playable character on the starter plot, saved on this phone.
 * Maya stands by the fire as a stand-in until the shared world arrives in Phase 2.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SaveStore(application.filesDir)
    private val saved: SaveGame? = store.load()

    // Saves are written one at a time, in order, off the main thread.
    private val saveThread = Dispatchers.IO.limitedParallelism(1)

    var camp by mutableStateOf(saved?.camp ?: Camp(StarterPlot.create()))
        private set

    val plot: Plot get() = camp.plot

    var character by mutableStateOf(saved?.character ?: Character("Jo"))
        private set

    /** Where Jo stands, in tiles. Jo walks over to whatever was just used. */
    var characterTile by mutableStateOf(saved?.let { it.tileX to it.tileY } ?: (11 to 11))
        private set

    /** Writes the current game to disk. Called after every change that matters. */
    private fun persist() {
        val game = SaveGame(character = character, camp = camp, tileX = characterTile.first, tileY = characterTile.second)
        viewModelScope.launch(saveThread) { store.save(game) }
    }

    val partnerName = "Maya"
    val partnerTile = 13 to 11

    var selection by mutableStateOf<Selection?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    fun actionsFor(obj: PlotObject): List<Action> = ObjectActions.forKind(obj.kind)

    fun actionsFor(room: Room): List<Action> = RoomActions.forKind(room.kind)

    /** The rooms worth offering on empty ground: just the cabin until it's built, then everything not built yet. */
    fun buildOptions(): List<RoomKind> =
        if (!camp.hasRoof) listOf(RoomKind.CABIN) else RoomKind.entries.filter { kind -> plot.rooms.none { it.kind == kind } }

    fun placement(kind: RoomKind, x: Int, y: Int): Placement = Building.check(plot, kind, x, y)

    /** In build mode a tap on open ground previews a room there instead of walking. */
    var buildMode by mutableStateOf(false)
        private set

    fun toggleBuildMode() {
        buildMode = !buildMode
        selection = null
    }

    private var walkJob: Job? = null

    /** Taps: things open their actions; open ground and room floors are walked to (or built on, in build mode). */
    fun tapTile(x: Int, y: Int) {
        if (x !in 0 until plot.size || y !in 0 until plot.size) return
        val thing = plot.objectAt(x, y)
        val room = plot.roomAt(x, y)
        when {
            thing != null -> selection = Selection.Thing(thing)
            buildMode && room == null -> selection = Selection.Ground(x, y)
            character.asleep -> message = "${character.name} is asleep. Start the next day first."
            else -> walkTo(x to y) {
                // Arriving in a room with something to do there opens it.
                if (room != null && actionsFor(room).isNotEmpty()) selection = Selection.InRoom(room)
            }
        }
    }

    fun dismissSelection() {
        selection = null
    }

    /**
     * Walks Jo tile by tile along the shortest route, then runs [onArrival].
     * A new tap cancels a walk in progress. Walking is free: it uses no game time.
     */
    private fun walkTo(target: Pair<Int, Int>, onArrival: () -> Unit = {}) {
        val route = Walking.path(plot, characterTile, target, occupied = setOf(partnerTile))
        if (route == null) {
            message = "${character.name} can't get there."
            return
        }
        walkJob?.cancel()
        walkJob = viewModelScope.launch {
            for (step in route) {
                characterTile = step
                delay(STEP_MILLIS)
            }
            persist()
            onArrival()
        }
    }

    /** Checks the action first, so Jo never walks over for something that can't happen, then walks and does it. */
    fun perform(obj: PlotObject, action: Action) {
        selection = null
        when (val result = camp.use(character, obj, action)) {
            is UseResult.Done -> {
                val spot = Walking.spotBeside(plot, obj, characterTile, occupied = setOf(partnerTile))
                if (spot == null) {
                    message = "${character.name} can't reach the ${obj.kind.label.lowercase()}."
                    return
                }
                walkTo(spot) {
                    character = result.character
                    camp = result.camp
                    message = action.yields.entries.firstOrNull()?.let { (resource, amount) ->
                        "+$amount ${resource.label.lowercase()} in the chest."
                    }
                    persist()
                }
            }
            UseResult.UsedUp ->
                message = "That ${obj.kind.label.lowercase()} needs to grow back. Try another, or come back tomorrow."
            is UseResult.Missing ->
                message = describeShortfall(result.shortfall) + " in the chest."
            is UseResult.Refused -> message = refusal(result.reason)
        }
    }

    fun perform(room: Room, action: Action) {
        when (val result = camp.useRoom(character, room, action)) {
            is UseResult.Done -> {
                character = result.character
                camp = result.camp
                message = null
                selection = null
                persist()
            }
            is UseResult.Refused -> message = refusal(result.reason)
            else -> Unit
        }
    }

    fun build(kind: RoomKind, x: Int, y: Int) {
        when (val result = camp.build(character, kind, x, y)) {
            is BuildResult.Done -> {
                character = result.character
                camp = result.camp
                buildMode = false
                message = if (kind == RoomKind.CABIN) {
                    "The cabin is finished. Tonight you sleep in a real bed."
                } else {
                    "The ${kind.label.lowercase()} is finished."
                }
                selection = null
                persist()
            }
            is BuildResult.Missing -> message = describeShortfall(result.shortfall) + " in the chest."
            is BuildResult.CantPlace -> message = "That spot doesn't work. Try somewhere else."
            is BuildResult.Refused -> message = refusal(result.reason)
        }
    }

    fun clearMessage() {
        message = null
    }

    /** Debug builds only: skip the grind when testing building. */
    fun debugFillChest() {
        camp = camp.copy(storage = camp.storage.plus(Resource.entries.associateWith { 100 }))
        message = "Debug: +100 of everything."
        persist()
    }

    fun sleep() {
        character = Rules.sleep(character)
        selection = null
        message = null
        persist()
    }

    fun startNextDay() {
        character = Rules.advanceDay(listOf(character), restful = camp.hasRoof).single()
        camp = camp.nextDay()
        // Wake up in the cabin once there is one, otherwise outside the tent.
        characterTile = plot.rooms.firstOrNull { it.kind == RoomKind.CABIN }?.let { it.x + 1 to it.y + 1 } ?: (11 to 11)
        message = "Trees, rocks and bushes have grown back."
        persist()
    }

    private fun refusal(reason: ActionResult): String = when (reason) {
        is ActionResult.NotEnoughTime -> "Not enough time left today for that. Try something shorter, or go to bed."
        else -> "${character.name} is asleep."
    }

    private companion object {
        /** How long each step takes on screen. */
        const val STEP_MILLIS = 140L
    }
}
