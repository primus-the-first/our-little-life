package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import com.ourlittlelife.rules.Need.FUN
import com.ourlittlelife.rules.Need.HUNGER
import com.ourlittlelife.rules.Need.HYGIENE
import com.ourlittlelife.rules.Need.SOCIAL
import kotlin.math.roundToInt

/**
 * The day, the way The Sims does it: the clock runs while you play and needs drop as it goes,
 * actions take believable amounts of time, and the night has no hard stop. Run out of energy and
 * you fall asleep; once everyone is asleep, the next morning comes. Numbers are starting values.
 */
object Rules {

    const val DAY_START = 7 * 60

    /** "Late": the clock keeps going after this, but the day button starts to nudge you to bed. */
    const val BEDTIME = 23 * 60

    /** How much each need drops per waking hour. */
    val decayPerHour: Map<Need, Int> = mapOf(
        ENERGY to 4,
        HUNGER to 6,
        FUN to 3,
        SOCIAL to 2,
        HYGIENE to 3,
    )

    /** What one night does to each need. */
    val overnight: Map<Need, Int> = mapOf(
        ENERGY to 70,
        HUNGER to -10,
        HYGIENE to -5,
    )

    fun decay(minutes: Int): Map<Need, Int> =
        decayPerHour.mapValues { (_, perHour) -> -(perHour * minutes / 60.0).roundToInt() }

    /** Does [action]: its time passes, needs drop for that time, and its effects land. */
    fun perform(character: Character, action: Action): ActionResult {
        if (character.asleep) return ActionResult.Asleep
        val changes = sum(decay(action.minutes), action.effects)
        return ActionResult.Done(
            character.copy(
                needs = character.needs.plus(changes),
                clock = character.clock.copy(minute = character.clock.minute + action.minutes),
            ),
        )
    }

    /**
     * Time passing while you're up and about but not doing anything in particular. The clock runs
     * on past midnight (still the same day until you sleep); needs drop as it goes.
     */
    fun passTime(character: Character, minutes: Int): Character {
        if (character.asleep || minutes <= 0) return character
        return character.copy(
            needs = character.needs.plus(decay(minutes)),
            clock = character.clock.copy(minute = character.clock.minute + minutes),
        )
    }

    /** Ends this character's turn. Going to bed early is allowed; unused hours are simply lost. */
    fun sleep(character: Character): Character = character.copy(asleep = true)

    /** The world moves to the next day only when every character is asleep. */
    fun canAdvance(characters: List<Character>): Boolean =
        characters.isNotEmpty() && characters.all { it.asleep }

    /** [restful] is true once there's a real roof: a night in a bed restores all energy. */
    fun advanceDay(characters: List<Character>, restful: Boolean = false): List<Character> {
        require(canAdvance(characters)) { "Everyone must be asleep before the day advances" }
        return characters.map { wakeUp(it, restful) }
    }

    private fun wakeUp(character: Character, restful: Boolean): Character = character.copy(
        needs = character.needs.plus(if (restful) overnight + (ENERGY to 100) else overnight),
        clock = Clock.startOfDay(character.clock.day + 1),
        asleep = false,
    )

    private fun sum(a: Map<Need, Int>, b: Map<Need, Int>): Map<Need, Int> =
        Need.entries.associateWith { (a[it] ?: 0) + (b[it] ?: 0) }
}
