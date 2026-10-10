package com.ourlittlelife.rules

import kotlinx.serialization.Serializable

/** Things that stand inside a room. Sizes are in interior tiles. */
enum class FurnitureKind(val label: String, val width: Int, val height: Int) {
    BED("Double bed", 2, 2),
}

@Serializable
data class Furniture(val id: String, val kind: FurnitureKind, val x: Int, val y: Int) {

    fun covers(tileX: Int, tileY: Int): Boolean =
        tileX in x until x + kind.width && tileY in y until y + kind.height

    fun tiles(): List<Pair<Int, Int>> =
        (x until x + kind.width).flatMap { tx -> (y until y + kind.height).map { ty -> tx to ty } }
}

/** Where a player is when they're inside a room: which room, and where on its floor. */
@Serializable
data class Inside(val roomId: String, val x: Int, val y: Int) {
    val tile: Pair<Int, Int> get() = x to y
}

/** What each piece of furniture offers when tapped. */
object FurnitureActions {
    fun forKind(kind: FurnitureKind): List<Action> = when (kind) {
        FurnitureKind.BED -> listOf(Building.napInBed)
    }
}

/**
 * Going inside rooms. Each room has its own floor, twice as many tiles each way as it takes up
 * on the map, with the door in the middle of the bottom wall.
 */
object Interiors {

    fun width(kind: RoomKind): Int = kind.width * 2

    fun height(kind: RoomKind): Int = kind.height * 2

    fun door(kind: RoomKind): Pair<Int, Int> = width(kind) / 2 to height(kind) - 1

    /** What a newly built room already has in it: the cabin comes with its bed. */
    fun starterFurniture(kind: RoomKind): List<Furniture> = when (kind) {
        RoomKind.CABIN -> listOf(Furniture("bed", FurnitureKind.BED, 1, 1))
        else -> emptyList()
    }

    /** Just outside the room on the map, below the door. */
    fun doorstep(room: Room): Pair<Int, Int> = room.x + room.kind.width / 2 to room.y + room.kind.height

    fun furnitureAt(room: Room, x: Int, y: Int): Furniture? = room.furniture.firstOrNull { it.covers(x, y) }

    /** Inside the walls, with no furniture on it and nobody standing there. */
    fun isFree(room: Room, tile: Pair<Int, Int>, occupied: Set<Pair<Int, Int>> = emptySet()): Boolean {
        val (x, y) = tile
        return x in 0 until width(room.kind) && y in 0 until height(room.kind) && furnitureAt(room, x, y) == null && tile !in occupied
    }

    fun path(room: Room, from: Pair<Int, Int>, to: Pair<Int, Int>, occupied: Set<Pair<Int, Int>> = emptySet()): List<Pair<Int, Int>>? =
        Walking.path(from, to) { isFree(room, it, occupied) }

    fun spotBeside(room: Room, piece: Furniture, from: Pair<Int, Int>, occupied: Set<Pair<Int, Int>> = emptySet()): Pair<Int, Int>? =
        Walking.spotBeside(piece.tiles(), from) { isFree(room, it, occupied) }

    fun whoIsIn(world: World, roomId: String): List<Player> = world.players.values.filter { it.inside?.roomId == roomId }

    /** Steps inside through the door. Null if the room isn't built or you're asleep. */
    fun enter(world: World, playerId: String, roomId: String): World? {
        val player = world.player(playerId)
        val room = world.camp.plot.rooms.firstOrNull { it.id == roomId } ?: return null
        if (player.character.asleep) return null
        val (x, y) = door(room.kind)
        return world.withPlayer(player.copy(inside = Inside(roomId, x, y)))
    }

    /** Steps back out onto the doorstep, or stays where they were outside if something blocks it. */
    fun leave(world: World, playerId: String): World {
        val player = world.player(playerId)
        val room = world.camp.plot.rooms.firstOrNull { it.id == player.inside?.roomId }
        val step = room?.let(::doorstep)?.takeIf { Walking.isWalkable(world.camp.plot, it) } ?: player.tile
        return world.withPlayer(player.copy(inside = null, tileX = step.first, tileY = step.second))
    }

    fun moveTo(world: World, playerId: String, tile: Pair<Int, Int>): World {
        val player = world.player(playerId)
        val inside = player.inside ?: return world
        return world.withPlayer(player.copy(inside = inside.copy(x = tile.first, y = tile.second)))
    }
}
