package com.ourlittlelife.game.day

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ourlittlelife.rules.Action
import com.ourlittlelife.rules.ActionResult
import com.ourlittlelife.rules.Character
import com.ourlittlelife.rules.Rules
import com.ourlittlelife.rules.StarterActions

/**
 * Phase 1 prototype: one character, one phone, no saving yet.
 * In the shared world the day only moves on when the partner sleeps too.
 */
class DayViewModel : ViewModel() {

    var character by mutableStateOf(Character("Jo"))
        private set

    var message by mutableStateOf<String?>(null)
        private set

    val actions: List<Action> = StarterActions.all

    fun perform(action: Action) {
        when (val result = Rules.perform(character, action)) {
            is ActionResult.Done -> {
                character = result.character
                message = null
            }
            is ActionResult.NotEnoughTime ->
                message = "Not enough time left today for that. Try something shorter, or go to bed."
            ActionResult.Asleep ->
                message = "${character.name} is asleep."
        }
    }

    fun sleep() {
        character = Rules.sleep(character)
        message = null
    }

    fun startNextDay() {
        character = Rules.advanceDay(listOf(character)).single()
        message = null
    }
}
