package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import kotlinx.serialization.Serializable

/** Gathering wood, stone and fiber. Each spot gives a few times a day and grows back overnight. */
object Gathering {
    val chop = Action("chop_wood", "Chop wood", 30, mapOf(ENERGY to -6), mapOf(Resource.WOOD to 6))
    val breakRock = Action("break_rock", "Break the rock", 30, mapOf(ENERGY to -8), mapOf(Resource.STONE to 4))
    val pickFiber = Action("pick_fiber", "Pick fiber", 15, mapOf(ENERGY to -2), mapOf(Resource.FIBER to 5))

    /** How many times a day each kind of spot can be gathered from. */
    fun usesPerDay(kind: ObjectKind): Int = when (kind) {
        ObjectKind.TREE -> 4
        ObjectKind.ROCK -> 3
        ObjectKind.BUSH -> 2
        else -> Int.MAX_VALUE
    }
}

sealed interface UseResult {
    data class Done(val character: Character, val camp: Camp) : UseResult

    /** This spot has given all it can today. */
    data object UsedUp : UseResult

    /** The chest is short of these amounts. */
    data class Missing(val shortfall: Map<Resource, Int>) : UseResult

    data class Refused(val reason: ActionResult) : UseResult
}

/** The shared world on one plot: the land, the storage chest, and what has been gathered today. */
@Serializable
data class Camp(
    val plot: Plot,
    val storage: Storage = Storage(),
    private val usesToday: Map<String, Int> = emptyMap(),
) {

    fun usesLeft(obj: PlotObject): Int {
        val perDay = Gathering.usesPerDay(obj.kind)
        return if (perDay == Int.MAX_VALUE) perDay else perDay - (usesToday[obj.id] ?: 0)
    }

    /** A character uses an object on the plot. Nothing changes unless the action succeeds. */
    fun use(character: Character, obj: PlotObject, action: Action): UseResult {
        require(action in ObjectActions.forKind(obj.kind)) { "${obj.kind.label} doesn't offer ${action.name}" }
        val limited = Gathering.usesPerDay(obj.kind) != Int.MAX_VALUE
        if (limited && usesLeft(obj) <= 0) return UseResult.UsedUp
        val short = storage.shortfall(action.costs)
        if (short.isNotEmpty()) return UseResult.Missing(short)

        return when (val result = Rules.perform(character, action)) {
            is ActionResult.Done -> UseResult.Done(
                character = result.character,
                camp = copy(
                    storage = storage.minus(action.costs).plus(action.yields),
                    usesToday = if (limited) usesToday + (obj.id to (usesToday[obj.id] ?: 0) + 1) else usesToday,
                ),
            )
            else -> UseResult.Refused(result)
        }
    }

    /** Overnight, every tree, rock and bush grows back. The chest keeps what's in it. */
    fun nextDay(): Camp = copy(usesToday = emptyMap())

    /** True once the cabin stands: nights are spent in a real bed. */
    val hasRoof: Boolean get() = plot.rooms.any { it.kind == RoomKind.CABIN }

    /** Builds a room with materials from the chest. Nothing is spent if it can't go there. */
    fun build(character: Character, kind: RoomKind, x: Int, y: Int): BuildResult {
        val placement = Building.check(plot, kind, x, y)
        if (placement != Placement.Ok) return BuildResult.CantPlace(placement)
        val short = storage.shortfall(kind.costs)
        if (short.isNotEmpty()) return BuildResult.Missing(short)

        return when (val result = Rules.perform(character, Building.actionFor(kind))) {
            is ActionResult.Done -> BuildResult.Done(
                character = result.character,
                camp = copy(
                    storage = storage.minus(kind.costs),
                    plot = plot.copy(rooms = plot.rooms + Room(kind.name.lowercase(), kind, x, y)),
                ),
            )
            else -> BuildResult.Refused(result)
        }
    }

    /** Uses a piece of furniture inside a room, such as the cabin's bed. */
    fun useFurniture(character: Character, room: Room, piece: Furniture, action: Action): UseResult {
        require(piece in room.furniture) { "${piece.kind.label} isn't in the ${room.kind.label}" }
        require(action in FurnitureActions.forKind(piece.kind)) { "${piece.kind.label} doesn't offer ${action.name}" }
        val short = storage.shortfall(action.costs)
        if (short.isNotEmpty()) return UseResult.Missing(short)
        return when (val result = Rules.perform(character, action)) {
            is ActionResult.Done -> UseResult.Done(result.character, copy(storage = storage.minus(action.costs).plus(action.yields)))
            else -> UseResult.Refused(result)
        }
    }

    /** Uses something a room offers, such as the cabin's bed. */
    fun useRoom(character: Character, room: Room, action: Action): UseResult {
        require(action in RoomActions.forKind(room.kind)) { "${room.kind.label} doesn't offer ${action.name}" }
        return when (val result = Rules.perform(character, action)) {
            is ActionResult.Done -> UseResult.Done(result.character, this)
            else -> UseResult.Refused(result)
        }
    }
}
