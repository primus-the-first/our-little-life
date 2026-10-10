package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import com.ourlittlelife.rules.Need.FUN
import com.ourlittlelife.rules.Need.HUNGER
import com.ourlittlelife.rules.Need.HYGIENE
import com.ourlittlelife.rules.Need.SOCIAL

/**
 * The camp actions available on day 1, before anything is built.
 * These move to JSON under content/ in Phase 4.
 */
object StarterActions {
    val snack = Action("snack", "Eat a snack", 30, mapOf(HUNGER to 20))
    val cook = Action("cook_campfire", "Cook at the campfire", 60, mapOf(HUNGER to 45, FUN to 5))
    val wash = Action("wash_basin", "Wash at the basin", 30, mapOf(HYGIENE to 40))
    val nap = Action("nap_tent", "Nap in the tent", 120, mapOf(ENERGY to 25))
    val stargaze = Action("stargaze", "Look at the stars", 60, mapOf(FUN to 20))
    /** Coins for a day at work, paid into the shared chest. */
    const val WAGE = 80

    val work = Action("work", "Go to work", 8 * 60, mapOf(FUN to -5, SOCIAL to 10), mapOf(Resource.COINS to WAGE))

    val all: List<Action> = listOf(snack, cook, wash, nap, stargaze, work)
}
