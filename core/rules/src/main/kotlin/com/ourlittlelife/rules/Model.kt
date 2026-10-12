package com.ourlittlelife.rules

import kotlinx.serialization.Serializable

/** The five needs every character has. Higher is always better, so HUNGER shows as "Fullness": eating fills it up. */
enum class Need(val label: String) {
    ENERGY("Energy"),
    HUNGER("Fullness"),
    FUN("Fun"),
    SOCIAL("Social"),
    HYGIENE("Hygiene"),
}

/** Need levels, each from 0 (empty) to 100 (full). Every change is clamped to that range. */
@Serializable
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

/** In-game time. Day 1 is a Monday. Mornings start at 7am; [minute] keeps counting past midnight until you sleep. */
@Serializable
data class Clock(val day: Int, val minute: Int) {

    /** Past bedtime: the clock doesn't stop, but it's time to think about sleep. */
    val late: Boolean get() = minute >= Rules.BEDTIME

    val weekday: String get() = WEEKDAYS[(day - 1) % 7]

    /** Monday to Friday are workdays. */
    val isWorkday: Boolean get() = (day - 1) % 7 < 5

    companion object {
        private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        fun startOfDay(day: Int): Clock = Clock(day, Rules.DAY_START)
    }
}

/**
 * Something a character can spend time on. Effects are added on top of the normal hourly drop;
 * costs are taken from the shared chest and yields go into it.
 */
data class Action(
    val id: String,
    val name: String,
    val minutes: Int,
    val effects: Map<Need, Int> = emptyMap(),
    val yields: Map<Resource, Int> = emptyMap(),
    val costs: Map<Resource, Int> = emptyMap(),
)

/** Materials: raw ones are gathered on the plot, refined ones are made at the workbench. */
enum class Resource(val label: String, val refined: Boolean = false) {
    WOOD("Wood"),
    STONE("Stone"),
    FIBER("Fiber"),
    PLANKS("Planks", refined = true),
    BRICKS("Bricks", refined = true),
    CLOTH("Cloth", refined = true),
    COINS("Coins"),
}

/** The shared storage chest: how much of each resource the pair has. */
@Serializable
data class Storage(private val amounts: Map<Resource, Int> = emptyMap()) {

    operator fun get(resource: Resource): Int = amounts[resource] ?: 0

    // Zero amounts are dropped, so an empty chest always equals Storage().
    fun plus(added: Map<Resource, Int>): Storage =
        Storage(Resource.entries.associateWith { this[it] + (added[it] ?: 0) }.filterValues { it != 0 })

    /** Takes [taken] out of the chest. Call [shortfall] first: the chest never goes below zero. */
    fun minus(taken: Map<Resource, Int>): Storage {
        require(shortfall(taken).isEmpty()) { "Not enough in the chest for $taken" }
        return Storage(Resource.entries.associateWith { this[it] - (taken[it] ?: 0) }.filterValues { it != 0 })
    }

    /** How much more of each resource is needed to cover [needed]; empty when there's enough. */
    fun shortfall(needed: Map<Resource, Int>): Map<Resource, Int> =
        needed.mapValues { (resource, amount) -> amount - this[resource] }.filterValues { it > 0 }
}

@Serializable
data class Character(
    val name: String,
    val needs: Needs = Needs.full(),
    val clock: Clock = Clock.startOfDay(1),
    val asleep: Boolean = false,
)

sealed interface ActionResult {
    data class Done(val character: Character) : ActionResult
    data object Asleep : ActionResult
}
