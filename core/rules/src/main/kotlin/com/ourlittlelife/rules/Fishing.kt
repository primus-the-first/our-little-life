package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import com.ourlittlelife.rules.Need.FUN
import com.ourlittlelife.rules.Need.HUNGER
import com.ourlittlelife.rules.Need.SOCIAL
import kotlinx.serialization.Serializable

/** What lives in the pond, from common to rare. [weight] is how often it bites; [coins] is what a rare one is worth. */
@Serializable
enum class FishKind(val label: String, val weight: Int, val coins: Int = 0) {
    MINNOW("Minnow", 55),
    PERCH("Perch", 28),
    TROUT("Trout", 13),
    GOLDEN_CARP("Golden carp", 4, coins = 25),
}

/**
 * Fishing at the pond. The phone runs the cast, the bite and the reel; these rules decide what was
 * caught and what it's worth. Fish go in the chest, for grilling at the campfire or a supper on the stove.
 */
object Fishing {

    /** One cast, landed or not. */
    val cast = Action("fish", "Fish", 15, mapOf(FUN to 6, ENERGY to -2))

    val grill = Action("grill_fish", "Grill a fish", 20, mapOf(HUNGER to 50, FUN to 5), costs = mapOf(Resource.FISH to 1))
    val supper = Action("fish_supper", "Cook a fish supper", 45, mapOf(HUNGER to 80, FUN to 10), costs = mapOf(Resource.FISH to 2))

    /** Extra social for fishing with your partner close by. */
    const val COMPANY_SOCIAL = 5

    /**
     * Which fish bit, from a [roll] of 0 to 99 and how cleanly it was reeled in ([quality], 0 to 1).
     * Clean reeling makes minnows rarer and the trout and golden carp likelier.
     */
    fun pick(roll: Int, quality: Float): FishKind {
        val q = quality.coerceIn(0f, 1f)
        val weights = FishKind.entries.associateWith { kind ->
            when (kind) {
                FishKind.MINNOW -> kind.weight * (1 - 0.7f * q)
                FishKind.PERCH -> kind.weight.toFloat()
                FishKind.TROUT -> kind.weight * (1 + q)
                FishKind.GOLDEN_CARP -> kind.weight * (1 + 3 * q)
            }
        }
        var point = roll.coerceIn(0, 99) / 100f * weights.values.sum()
        for ((kind, weight) in weights) {
            if (point < weight) return kind
            point -= weight
        }
        return FishKind.entries.last()
    }

    /** A fish landed: into the chest (with coins for a rare one), into your log. Null if you're asleep. */
    fun land(world: World, playerId: String, kind: FishKind): World? {
        val after = spendCast(world, playerId) ?: return null
        val player = after.player(playerId)
        val stored = after.camp.storage.plus(mapOf(Resource.FISH to 1, Resource.COINS to kind.coins))
        val log = player.fishLog + (kind to (player.fishLog[kind] ?: 0) + 1)
        return after.copy(camp = after.camp.copy(storage = stored)).withPlayer(player.copy(fishLog = log))
    }

    /** It got away: the time still passes. Null if you're asleep. */
    fun miss(world: World, playerId: String): World? = spendCast(world, playerId)

    private fun spendCast(world: World, playerId: String): World? {
        val player = world.player(playerId)
        val done = Rules.perform(player.character, cast) as? ActionResult.Done ?: return null
        val company = Together.hasCompany(world, player)
        val character = if (company) done.character.copy(needs = done.character.needs.plus(mapOf(SOCIAL to COMPANY_SOCIAL))) else done.character
        return world.withPlayer(player.copy(character = character))
    }
}
