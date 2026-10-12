package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import com.ourlittlelife.rules.Need.FUN
import com.ourlittlelife.rules.Need.HUNGER
import com.ourlittlelife.rules.Need.HYGIENE
import com.ourlittlelife.rules.Resource.BRICKS
import com.ourlittlelife.rules.Resource.CLOTH
import com.ourlittlelife.rules.Resource.COINS
import com.ourlittlelife.rules.Resource.PLANKS
import kotlinx.serialization.Serializable

/** How the furniture tray is sorted. */
enum class FurnitureCategory(val label: String) {
    SEATING("Seating"),
    BEDS("Beds"),
    KITCHEN("Kitchen"),
    WASHING("Washing"),
    DECOR("Decor"),
}

/**
 * Things that stand inside a room. Sizes are in interior tiles. Simple pieces are made from
 * materials in the chest; nicer ones are bought with coins. Costs are starting values.
 */
enum class FurnitureKind(
    val label: String,
    val width: Int,
    val height: Int,
    val category: FurnitureCategory,
    val costs: Map<Resource, Int>,
    val walkable: Boolean = false,
) {
    BED("Double bed", 2, 2, FurnitureCategory.BEDS, mapOf(PLANKS to 30, CLOTH to 20)),
    CHAIR("Chair", 1, 1, FurnitureCategory.SEATING, mapOf(PLANKS to 6)),
    SOFA("Sofa", 2, 1, FurnitureCategory.SEATING, mapOf(COINS to 120)),
    TABLE("Table", 2, 1, FurnitureCategory.KITCHEN, mapOf(PLANKS to 16)),
    STOVE("Stove", 1, 1, FurnitureCategory.KITCHEN, mapOf(COINS to 150)),
    WASHSTAND("Washstand", 1, 1, FurnitureCategory.WASHING, mapOf(BRICKS to 10, PLANKS to 4)),
    BOOKSHELF("Bookshelf", 1, 1, FurnitureCategory.DECOR, mapOf(PLANKS to 12)),
    RUG("Rug", 3, 2, FurnitureCategory.DECOR, mapOf(CLOTH to 10), walkable = true),
    PLANT("Potted plant", 1, 1, FurnitureCategory.DECOR, mapOf(COINS to 40)),
    CANDLES("Candle stand", 1, 1, FurnitureCategory.DECOR, mapOf(COINS to 50)),
    ;

    /** Bought with coins rather than made from materials. */
    val bought: Boolean get() = COINS in costs
}

/** Whether a piece of furniture can go somewhere, and if not, why. */
sealed interface FurniturePlacement {
    data object Ok : FurniturePlacement
    data object OffFloor : FurniturePlacement
    data object Blocked : FurniturePlacement
    data object BlocksDoor : FurniturePlacement
}

sealed interface PlaceResult {
    data class Done(val world: World) : PlaceResult
    data class CantPlace(val reason: FurniturePlacement) : PlaceResult
    data class Missing(val shortfall: Map<Resource, Int>) : PlaceResult
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

/** What each piece of furniture offers when tapped. Decor is just for looks. */
object FurnitureActions {
    val sit = Action("sit_chair", "Sit down", 10, mapOf(ENERGY to 3, FUN to 2))
    val sitTogether = Action("sit_sofa", "Sit on the sofa", 30, mapOf(ENERGY to 4, FUN to 8))
    val meal = Action("meal_table", "Have a meal", 30, mapOf(HUNGER to 35, FUN to 3))
    val cook = Action("cook_stove", "Cook a proper meal", 45, mapOf(HUNGER to 60, FUN to 5))
    val wash = Action("wash_stand", "Wash up", 15, mapOf(HYGIENE to 40))
    val read = Action("read_book", "Read a book", 45, mapOf(FUN to 15))

    fun forKind(kind: FurnitureKind): List<Action> = when (kind) {
        FurnitureKind.BED -> listOf(Building.napInBed)
        FurnitureKind.CHAIR -> listOf(sit)
        FurnitureKind.SOFA -> listOf(sitTogether)
        FurnitureKind.TABLE -> listOf(meal)
        FurnitureKind.STOVE -> listOf(cook)
        FurnitureKind.WASHSTAND -> listOf(wash)
        FurnitureKind.BOOKSHELF -> listOf(read)
        FurnitureKind.RUG, FurnitureKind.PLANT, FurnitureKind.CANDLES -> emptyList()
    }

    /** Pieces that are nicer with your partner in the room. */
    val shared: Set<FurnitureKind> = setOf(FurnitureKind.SOFA, FurnitureKind.TABLE)
}

/**
 * Going inside rooms. Each room has its own floor, twice as many tiles each way as it takes up
 * on the map, with the door in the middle of the bottom wall.
 */
object Interiors {

    /** Extra social for using the sofa or table with your partner in the room. */
    const val COMPANY_SOCIAL = 15

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

    /** Inside the walls, with nothing in the way (rugs don't count) and nobody standing there. */
    fun isFree(room: Room, tile: Pair<Int, Int>, occupied: Set<Pair<Int, Int>> = emptySet()): Boolean {
        val (x, y) = tile
        val inWay = room.furniture.any { !it.kind.walkable && it.covers(x, y) }
        return x in 0 until width(room.kind) && y in 0 until height(room.kind) && !inWay && tile !in occupied
    }

    /**
     * Whether [kind] fits with its top-left corner at ([x], [y]): on the floor, clear of the door,
     * other furniture (except [moving], the piece being moved) and anyone in [occupied].
     */
    fun check(room: Room, kind: FurnitureKind, x: Int, y: Int, moving: String? = null, occupied: Set<Pair<Int, Int>> = emptySet()): FurniturePlacement {
        val footprint = Furniture("new", kind, x, y).tiles()
        if (footprint.any { (tx, ty) -> tx !in 0 until width(room.kind) || ty !in 0 until height(room.kind) }) return FurniturePlacement.OffFloor
        if (door(room.kind) in footprint) return FurniturePlacement.BlocksDoor
        val others = room.furniture.filter { it.id != moving }
        if (footprint.any { tile -> tile in occupied || others.any { it.covers(tile.first, tile.second) } }) return FurniturePlacement.Blocked
        return FurniturePlacement.Ok
    }

    /** Puts a new piece in a room, paid for from the chest. */
    fun place(world: World, roomId: String, kind: FurnitureKind, x: Int, y: Int): PlaceResult {
        val room = world.camp.plot.rooms.firstOrNull { it.id == roomId } ?: return PlaceResult.CantPlace(FurniturePlacement.OffFloor)
        val placement = check(room, kind, x, y, occupied = standingIn(world, roomId))
        if (placement != FurniturePlacement.Ok) return PlaceResult.CantPlace(placement)
        val short = world.camp.storage.shortfall(kind.costs)
        if (short.isNotEmpty()) return PlaceResult.Missing(short)

        val taken = room.furniture.map { it.id }.toSet()
        val id = generateSequence(1) { it + 1 }.map { "${kind.name.lowercase()}-$it" }.first { it !in taken }
        val placed = withRoom(world, room.copy(furniture = room.furniture + Furniture(id, kind, x, y)))
        return PlaceResult.Done(placed.copy(camp = placed.camp.copy(storage = placed.camp.storage.minus(kind.costs))))
    }

    /** Moves a piece somewhere else in the same room. Free. */
    fun move(world: World, roomId: String, pieceId: String, x: Int, y: Int): PlaceResult {
        val room = world.camp.plot.rooms.firstOrNull { it.id == roomId } ?: return PlaceResult.CantPlace(FurniturePlacement.OffFloor)
        val piece = room.furniture.firstOrNull { it.id == pieceId } ?: return PlaceResult.CantPlace(FurniturePlacement.OffFloor)
        val placement = check(room, piece.kind, x, y, moving = pieceId, occupied = standingIn(world, roomId))
        if (placement != FurniturePlacement.Ok) return PlaceResult.CantPlace(placement)
        return PlaceResult.Done(withRoom(world, room.copy(furniture = room.furniture.map { if (it.id == pieceId) it.copy(x = x, y = y) else it })))
    }

    /** Takes a piece out of the room and puts what it cost back in the chest, so changing your mind is free. */
    fun putAway(world: World, roomId: String, pieceId: String): World {
        val room = world.camp.plot.rooms.firstOrNull { it.id == roomId } ?: return world
        val piece = room.furniture.firstOrNull { it.id == pieceId } ?: return world
        val emptied = withRoom(world, room.copy(furniture = room.furniture - piece))
        return emptied.copy(camp = emptied.camp.copy(storage = emptied.camp.storage.plus(piece.kind.costs)))
    }

    /**
     * Uses a piece of furniture in the room you're in. The sofa and the table give extra social
     * when your partner is in there and awake. Null if you're not in that room, or you're asleep.
     */
    fun use(world: World, playerId: String, pieceId: String, action: Action): World? {
        val player = world.player(playerId)
        val room = world.camp.plot.rooms.firstOrNull { it.id == player.inside?.roomId } ?: return null
        val piece = room.furniture.firstOrNull { it.id == pieceId } ?: return null
        val done = world.camp.useFurniture(player.character, room, piece, action) as? UseResult.Done ?: return null
        val company = piece.kind in FurnitureActions.shared &&
            whoIsIn(world, room.id).any { it.id != playerId && !it.character.asleep }
        val character = if (company) done.character.copy(needs = done.character.needs.plus(mapOf(Need.SOCIAL to COMPANY_SOCIAL))) else done.character
        return world.copy(camp = done.camp).withPlayer(player.copy(character = character))
    }

    private fun standingIn(world: World, roomId: String): Set<Pair<Int, Int>> =
        whoIsIn(world, roomId).mapNotNull { it.inside?.tile }.toSet()

    private fun withRoom(world: World, room: Room): World {
        val plot = world.camp.plot
        return world.copy(camp = world.camp.copy(plot = plot.copy(rooms = plot.rooms.map { if (it.id == room.id) room else it })))
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
