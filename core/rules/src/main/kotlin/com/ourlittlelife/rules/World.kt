package com.ourlittlelife.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** What the two players are to each other. It decides which joint activities exist. */
@Serializable
enum class Mode(val label: String) {
    BESTIES("Besties"),
    ROOMMATES("Roommates"),
    PARTNERS("Partners"),
}

/** Something a player is in the middle of, shown to their partner. */
@Serializable
enum class Activity(val label: String) {
    STARGAZING("Stargazing"),
    WALK_INVITE("Hoping for a walk"),
}

/** One person in a world: their character, where they stand, what they're doing, and when they went to bed (epoch millis). */
@Serializable
data class Player(
    val id: String,
    val character: Character,
    val tileX: Int,
    val tileY: Int,
    val sleptAt: Long? = null,
    val activity: Activity? = null,
    val inside: Inside? = null,
    val greeting: Greeting? = null,
    val gesturesToday: List<Gesture> = emptyList(),
) {
    val tile: Pair<Int, Int> get() = tileX to tileY
}

/** The shared world two players keep together: one camp, up to two players, joined with [code]. */
@Serializable
data class World(
    val id: String,
    val code: String,
    val mode: Mode,
    val camp: Camp,
    val players: Map<String, Player>,
) {
    val day: Int get() = players.values.maxOf { it.character.clock.day }

    fun player(id: String): Player = players.getValue(id)

    fun withPlayer(player: Player): World = copy(players = players + (player.id to player))
}

sealed interface JoinResult {
    data class Joined(val world: World) : JoinResult
    data class AlreadyIn(val world: World) : JoinResult
    data object Full : JoinResult
}

object Worlds {

    /** No O, 0, I, 1 or L, so a code read out loud or typed from a screenshot can't be misread. */
    const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val MAX_PLAYERS = 2

    /** How long someone can stay away after their partner goes to bed before autopilot covers their day. */
    const val AUTOPILOT_AFTER_MILLIS = 24 * 60 * 60 * 1000L

    /** Where players appear: beside the campfire, one on each side. */
    private val spawnTiles = listOf(11 to 11, 13 to 11).map { (x, y) -> x + WorldMap.YARD.x to y + WorldMap.YARD.y }

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun newCode(random: Random = Random.Default): String =
        (1..6).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    fun create(id: String, code: String, mode: Mode, hostId: String, hostName: String): World {
        val (x, y) = spawnTiles.first()
        return World(id, code, mode, Camp(WorldMap.expand(StarterPlot.create())), mapOf(hostId to Player(hostId, Character(hostName), x, y)))
    }

    /** The second player joins on the world's current day, at the free spot by the fire. */
    fun join(world: World, playerId: String, name: String): JoinResult {
        if (playerId in world.players) return JoinResult.AlreadyIn(world)
        if (world.players.size >= MAX_PLAYERS) return JoinResult.Full
        val taken = world.players.values.map { it.tile }.toSet()
        val (x, y) = spawnTiles.firstOrNull { it !in taken } ?: spawnTiles.last()
        val character = Character(name, clock = Clock.startOfDay(world.day))
        return JoinResult.Joined(world.withPlayer(Player(playerId, character, x, y)))
    }

    fun sleep(world: World, playerId: String, now: Long): World {
        val player = world.player(playerId)
        return world.withPlayer(player.copy(character = Rules.sleep(player.character), sleptAt = now))
    }

    /** When everyone in the world is asleep, the next day starts for all of them and the plot regrows. */
    fun advanceIfReady(world: World): World {
        val ids = world.players.keys.toList()
        val characters = ids.map { world.player(it).character }
        if (!Rules.canAdvance(characters)) return world
        val woken = Rules.advanceDay(characters, restful = world.camp.hasRoof)
        val players = ids.zip(woken).associate { (id, character) ->
            id to world.player(id).copy(character = character, sleptAt = null, gesturesToday = emptyList())
        }
        return world.copy(players = players, camp = world.camp.nextDay())
    }

    /**
     * If one player went to bed more than a day ago and the other still hasn't played,
     * the absent player's character eats, washes and goes to bed, and the day moves on.
     */
    fun autopilot(world: World, now: Long): World {
        val sleepers = world.players.values.filter { it.character.asleep && it.sleptAt != null }
        if (sleepers.isEmpty() || sleepers.size == world.players.size) return world
        val waitingSince = sleepers.minOf { it.sleptAt!! }
        if (now - waitingSince < AUTOPILOT_AFTER_MILLIS) return world

        var covered = world
        world.players.values.filter { !it.character.asleep }.forEach { absent ->
            covered = sleep(safeDay(covered, absent.id), absent.id, now)
        }
        return advanceIfReady(covered)
    }

    /** A simple, safe day: a hot meal at the campfire and a wash. Anything that can't happen is skipped. */
    private fun safeDay(world: World, playerId: String): World {
        var current = world
        val plan = listOf(ObjectKind.CAMPFIRE to StarterActions.cook, ObjectKind.WASHBASIN to StarterActions.wash)
        plan.forEach { (kind, action) ->
            val obj = current.camp.plot.objects.firstOrNull { it.kind == kind } ?: return@forEach
            val result = current.camp.use(current.player(playerId).character, obj, action)
            if (result is UseResult.Done) {
                current = current.copy(camp = result.camp).withPlayer(current.player(playerId).copy(character = result.character))
            }
        }
        return current
    }

    fun encode(world: World): String = json.encodeToString(World.serializer(), world)

    /** Older saves are moved into the bigger map as they load. */
    fun decode(text: String): World = WorldMap.upgrade(json.decodeFromString(World.serializer(), text))
}
