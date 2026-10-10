package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.SOCIAL
import kotlin.math.abs

/** Moments that are better shared: being close to your partner while you do them adds social. */
object Together {

    /** Within this many steps counts as together. */
    const val NEARBY_TILES = 3

    /** Social for stargazing with someone awake nearby. */
    const val NEARBY_SOCIAL = 15

    /** Social when they're stargazing too. */
    const val TOGETHER_SOCIAL = 25

    fun nearby(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean =
        abs(a.first - b.first) + abs(a.second - b.second) <= NEARBY_TILES

    /** Marks the player as looking at the stars, so their partner can see it and come over. */
    fun startStargazing(world: World, playerId: String): World =
        world.withPlayer(world.player(playerId).copy(activity = Activity.STARGAZING))

    /**
     * Finishes stargazing: an hour of fun, plus social if someone awake is nearby (more if they're stargazing too).
     * Returns null when it can't happen (no time left, or asleep). Always clears the activity when it does.
     */
    fun stargaze(world: World, playerId: String): World? {
        val player = world.player(playerId)
        val done = Rules.perform(player.character, StarterActions.stargaze) as? ActionResult.Done ?: return null
        val social = socialBonus(world, player)
        val character = done.character.copy(needs = done.character.needs.plus(mapOf(SOCIAL to social)))
        return world.withPlayer(player.copy(character = character, activity = null))
    }

    /** How much social stargazing would add right now, for showing on screen before it happens. */
    fun socialBonus(world: World, player: Player): Int {
        val company = world.players.values.filter { it.id != player.id && !it.character.asleep && nearby(it.tile, player.tile) }
        return when {
            company.any { it.activity == Activity.STARGAZING } -> TOGETHER_SOCIAL
            company.isNotEmpty() -> NEARBY_SOCIAL
            else -> 0
        }
    }
}
