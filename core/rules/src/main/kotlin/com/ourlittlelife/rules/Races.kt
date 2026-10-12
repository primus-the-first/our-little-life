package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import com.ourlittlelife.rules.Need.FUN

/** How a lap went: the world after it, and how [lapMillis] compares with your best and your partner's. */
data class RaceOutcome(
    val world: World,
    val lapMillis: Long,
    val personalBest: Boolean,
    val previousBest: Long?,
    val partnerBest: Long?,
) {
    val beatPartner: Boolean get() = partnerBest != null && lapMillis < partnerBest
}

/**
 * Laps of the racecourse. You run one on your own, against your partner's best lap as a ghost,
 * so it works whether or not you're both playing at once. A new personal best pays coins.
 */
object Races {

    val lap = Action("race_lap", "Run a lap", 5, mapOf(FUN to 15, ENERGY to -10))

    /** Coins for a new personal best, paid into the shared chest. */
    const val BEST_PRIZE = 20

    /** Nobody can run a lap faster than this; quicker times are ignored. */
    const val FASTEST_LAP_MILLIS = 8_000L

    /** Stamina at the start line: rested runners last longer. */
    fun stamina(character: Character): Int = 60 + character.needs[ENERGY] * 2 / 5

    /** Records a finished lap. Null if the time is impossible or the runner is asleep. */
    fun finish(world: World, playerId: String, millis: Long): RaceOutcome? {
        if (millis < FASTEST_LAP_MILLIS) return null
        val runner = world.player(playerId)
        val done = Rules.perform(runner.character, lap) as? ActionResult.Done ?: return null
        val previous = runner.bestLapMillis
        val best = previous == null || millis < previous
        val partnerBest = world.players.values.firstOrNull { it.id != playerId }?.bestLapMillis

        var next = world.withPlayer(runner.copy(character = done.character, bestLapMillis = if (best) millis else previous))
        if (best) next = next.copy(camp = next.camp.copy(storage = next.camp.storage.plus(mapOf(Resource.COINS to BEST_PRIZE))))
        return RaceOutcome(next, millis, best, previous, partnerBest)
    }
}
