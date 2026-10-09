package com.ourlittlelife.game.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ourlittlelife.game.day.describeShortfall
import com.ourlittlelife.rules.Action
import com.ourlittlelife.rules.ActionResult
import com.ourlittlelife.rules.Camp
import com.ourlittlelife.rules.Character
import com.ourlittlelife.rules.ObjectActions
import com.ourlittlelife.rules.Plot
import com.ourlittlelife.rules.PlotObject
import com.ourlittlelife.rules.Rules
import com.ourlittlelife.rules.StarterPlot
import com.ourlittlelife.rules.UseResult

/**
 * Phase 1 prototype: one playable character on the starter plot, one phone, no saving yet.
 * Maya stands by the fire as a stand-in until the shared world arrives in Phase 2.
 */
class GameViewModel : ViewModel() {

    var camp by mutableStateOf(Camp(StarterPlot.create()))
        private set

    val plot: Plot get() = camp.plot

    var character by mutableStateOf(Character("Jo"))
        private set

    /** Where Jo stands, in tiles. Jo walks over to whatever was just used. */
    var characterTile by mutableStateOf(11 to 11)
        private set

    val partnerName = "Maya"
    val partnerTile = 13 to 11

    var selected by mutableStateOf<PlotObject?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    fun actionsFor(obj: PlotObject): List<Action> = ObjectActions.forKind(obj.kind)

    fun tapTile(x: Int, y: Int) {
        selected = plot.objectAt(x, y)
    }

    fun dismissSelection() {
        selected = null
    }

    fun perform(obj: PlotObject, action: Action) {
        when (val result = camp.use(character, obj, action)) {
            is UseResult.Done -> {
                character = result.character
                camp = result.camp
                characterTile = spotNextTo(obj) ?: characterTile
                message = action.yields.entries.firstOrNull()?.let { (resource, amount) ->
                    "+$amount ${resource.label.lowercase()} in the chest."
                }
                selected = null
            }
            UseResult.UsedUp ->
                message = "That ${obj.kind.label.lowercase()} needs to grow back. Try another, or come back tomorrow."
            is UseResult.Missing ->
                message = describeShortfall(result.shortfall) + " in the chest."
            is UseResult.Refused -> message = when (result.reason) {
                is ActionResult.NotEnoughTime -> "Not enough time left today for that. Try something shorter, or go to bed."
                else -> "${character.name} is asleep."
            }
        }
    }

    fun clearMessage() {
        message = null
    }

    fun sleep() {
        character = Rules.sleep(character)
        selected = null
        message = null
    }

    fun startNextDay() {
        character = Rules.advanceDay(listOf(character)).single()
        camp = camp.nextDay()
        characterTile = 11 to 11
        message = "Trees, rocks and bushes have grown back."
    }

    /** The free tile just below an object (where you'd stand to use it), if there is one. */
    private fun spotNextTo(obj: PlotObject): Pair<Int, Int>? {
        val below = obj.x to obj.y + obj.kind.height
        val free = plot.objectAt(below.first, below.second) == null && below != partnerTile
        return if (free && below.second < plot.size) below else null
    }
}
