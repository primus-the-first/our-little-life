package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.FUN
import com.ourlittlelife.rules.Need.HUNGER
import com.ourlittlelife.rules.Need.SOCIAL
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Quick ways to say hello. Hugs and high fives need you side by side; a wave carries further. [joy] is fun added. */
@Serializable
enum class Gesture(val label: String, val past: String, val social: Int, val joy: Int, val reach: Int) {
    WAVE("Wave", "waved at you", social = 3, joy = 0, reach = 8),
    HIGH_FIVE("High five", "gave you a high five", social = 5, joy = 3, reach = 1),
    HUG("Hug", "hugged you", social = 10, joy = 0, reach = 1),
}

/** The latest gesture someone received. [count] goes up with each one, so their phone can tell a new one arrived. */
@Serializable
data class Greeting(val from: String, val gesture: Gesture, val count: Int)

/** Moments that are better shared: being close to your partner while you do them adds social. */
object Together {

    /** Within this many steps counts as together. */
    const val NEARBY_TILES = 3

    /** Social for stargazing with someone awake nearby. */
    const val NEARBY_SOCIAL = 15

    /** Social when they're stargazing too. */
    const val TOGETHER_SOCIAL = 25

    fun nearby(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean = steps(a, b) <= NEARBY_TILES

    private fun steps(a: Pair<Int, Int>, b: Pair<Int, Int>): Int = abs(a.first - b.first) + abs(a.second - b.second)

    val stories = Action("fire_stories", "Tell stories by the fire", 45, mapOf(FUN to 10))
    val picnic = Action("picnic", "Have a picnic", 60, mapOf(HUNGER to 30, FUN to 10))

    /** Extra social for doing these with your partner awake and close by. */
    val companyBonus: Map<Action, Int> = mapOf(stories to 20, picnic to 25, Outdoors.sit to 10)

    /**
     * How many steps apart two players are: on the map if both are outdoors, on the floor if both
     * are in the same room, and null if one is inside and the other isn't (or they're in different rooms).
     */
    fun distance(a: Player, b: Player): Int? = when {
        a.inside == null && b.inside == null -> steps(a.tile, b.tile)
        a.inside != null && b.inside != null && a.inside.roomId == b.inside.roomId -> steps(a.inside.tile, b.inside.tile)
        else -> null
    }

    /**
     * [from] greets the other player. Both get the social (and fun) the first time each gesture is
     * used in a day; after that it's still sent, just for the fun of it. Null if there's nobody to
     * greet, either of you is asleep, or they're out of reach.
     */
    fun greet(world: World, from: String, gesture: Gesture): World? {
        val me = world.player(from)
        val other = world.players.values.firstOrNull { it.id != from } ?: return null
        if (me.character.asleep || other.character.asleep) return null
        val apart = distance(me, other) ?: return null
        if (apart > gesture.reach) return null

        val counts = gesture !in me.gesturesToday
        val lift = if (counts) mapOf(SOCIAL to gesture.social, FUN to gesture.joy) else emptyMap()
        fun cheer(p: Player) = p.character.copy(needs = p.character.needs.plus(lift))
        val greeting = Greeting(from, gesture, (other.greeting?.count ?: 0) + 1)
        return world
            .withPlayer(me.copy(character = cheer(me), gesturesToday = if (counts) me.gesturesToday + gesture else me.gesturesToday))
            .withPlayer(other.copy(character = cheer(other), greeting = greeting))
    }

    /**
     * Uses something outdoors: gathers, cooks, sits, and so on, exactly as the camp rules say, plus
     * extra social for the shared moments in [companyBonus]. Null if it can't happen.
     */
    fun use(world: World, playerId: String, obj: PlotObject, action: Action): World? {
        val player = world.player(playerId)
        val done = world.camp.use(player.character, obj, action) as? UseResult.Done ?: return null
        val bonus = companyBonus[action]?.takeIf { hasCompany(world, player) } ?: 0
        val character = if (bonus > 0) done.character.copy(needs = done.character.needs.plus(mapOf(SOCIAL to bonus))) else done.character
        return world.copy(camp = done.camp).withPlayer(player.copy(character = character))
    }

    /** Someone awake, outdoors and within a few steps of [player]. */
    fun hasCompany(world: World, player: Player): Boolean = world.players.values.any {
        it.id != player.id && !it.character.asleep && player.inside == null && it.inside == null && nearby(it.tile, player.tile)
    }

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
        val company = world.players.values.filter {
            it.id != player.id && !it.character.asleep && it.inside == null && player.inside == null && nearby(it.tile, player.tile)
        }
        return when {
            company.any { it.activity == Activity.STARGAZING } -> TOGETHER_SOCIAL
            company.isNotEmpty() -> NEARBY_SOCIAL
            else -> 0
        }
    }
}
