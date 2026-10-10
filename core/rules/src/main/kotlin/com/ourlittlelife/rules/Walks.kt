package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import com.ourlittlelife.rules.Need.FUN
import com.ourlittlelife.rules.Need.SOCIAL

/**
 * The forest trail. Walk it alone from the trailhead sign, or ask your partner:
 * they get the invite, and saying yes sends you both round the loop together.
 */
object Walks {

    /** Social for walking the loop together. */
    const val TOGETHER_SOCIAL = 25

    val alone = Action("walk_trail", "Walk the trail", 60, mapOf(FUN to 12, ENERGY to -4))
    val together = Action("walk_together", "Walk together", 60, mapOf(FUN to 12, SOCIAL to TOGETHER_SOCIAL, ENERGY to -4))

    /** Where a walk finishes: back at the trailhead, on the path out of the yard. */
    val END = 31 to 45

    /** Where the second walker finishes, one step behind. */
    val BEHIND_END = 31 to 46

    /** The loop in walking order: west along the top, down the far side, east along the bottom, and back up. */
    val trail: List<Pair<Int, Int>> = buildList {
        for (x in 31 downTo 10) add(x to 49)
        for (y in 50..59) add(10 to y)
        for (x in 11..31) add(x to 59)
        for (y in 58 downTo 50) add(31 to y)
    }

    /** Asks the other player for a walk. Null when there's nobody awake to ask, or you're asleep. */
    fun invite(world: World, from: String): World? {
        val me = world.player(from)
        val other = world.players.values.firstOrNull { it.id != from } ?: return null
        if (me.character.asleep || other.character.asleep) return null
        return world.withPlayer(me.copy(activity = Activity.WALK_INVITE))
    }

    /** The invite waiting for [forPlayer], if their partner has asked. */
    fun waitingInvite(world: World, forPlayer: String): Player? =
        world.players.values.firstOrNull { it.id != forPlayer && it.activity == Activity.WALK_INVITE }

    /** Either of you can call it off: "Not now", or cancelling your own invite. */
    fun decline(world: World, by: String): World {
        val inviter = waitingInvite(world, by) ?: world.player(by).takeIf { it.activity == Activity.WALK_INVITE } ?: return world
        return world.withPlayer(inviter.copy(activity = null))
    }

    /**
     * Saying yes: an hour round the loop for both of you, ending back at the trailhead.
     * Null if there's no invite, or either of you is asleep. Anyone indoors comes out for it.
     */
    fun accept(world: World, by: String): World? {
        val inviter = waitingInvite(world, by) ?: return null
        val me = world.player(by)
        val mine = Rules.perform(me.character, together) as? ActionResult.Done ?: return null
        val theirs = Rules.perform(inviter.character, together) as? ActionResult.Done ?: return null
        return world
            .withPlayer(me.copy(character = mine.character, tileX = END.first, tileY = END.second, activity = null, inside = null))
            .withPlayer(inviter.copy(character = theirs.character, tileX = BEHIND_END.first, tileY = BEHIND_END.second, activity = null, inside = null))
    }
}
