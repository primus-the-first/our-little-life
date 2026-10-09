package com.ourlittlelife.rules

/** The five needs every character has. */
enum class Need(val label: String) {
    ENERGY("Energy"),
    HUNGER("Hunger"),
    FUN("Fun"),
    SOCIAL("Social"),
    HYGIENE("Hygiene"),
}

/** Need levels, each from 0 (empty) to 100 (full). Every change is clamped to that range. */
data class Needs(private val levels: Map<Need, Int>) {

    operator fun get(need: Need): Int = levels[need] ?: 0

    fun plus(changes: Map<Need, Int>): Needs =
        Needs(Need.entries.associateWith { (this[it] + (changes[it] ?: 0)).coerceIn(0, 100) })

    /** The average of all five needs; drives the character's mood word. */
    val mood: Int get() = Need.entries.sumOf { this[it] } / Need.entries.size

    companion object {
        fun full(): Needs = Needs(Need.entries.associateWith { 100 })

        fun of(vararg levels: Pair<Need, Int>): Needs =
            Needs(Need.entries.associateWith { 0 }).plus(levels.toMap())
    }
}

/** In-game time. Day 1 is a Monday; waking hours run from 7am to 11pm. */
data class Clock(val day: Int, val minute: Int) {

    val minutesLeft: Int get() = (Rules.DAY_END - minute).coerceAtLeast(0)

    val weekday: String get() = WEEKDAYS[(day - 1) % 7]

    /** Monday to Friday are workdays. */
    val isWorkday: Boolean get() = (day - 1) % 7 < 5

    companion object {
        private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        fun startOfDay(day: Int): Clock = Clock(day, Rules.DAY_START)
    }
}

/** Something a character can spend time on. Effects are added on top of the normal hourly drop. */
data class Action(
    val id: String,
    val name: String,
    val minutes: Int,
    val effects: Map<Need, Int> = emptyMap(),
)

data class Character(
    val name: String,
    val needs: Needs = Needs.full(),
    val clock: Clock = Clock.startOfDay(1),
    val asleep: Boolean = false,
)

sealed interface ActionResult {
    data class Done(val character: Character) : ActionResult
    data class NotEnoughTime(val minutesLeft: Int) : ActionResult
    data object Asleep : ActionResult
}
