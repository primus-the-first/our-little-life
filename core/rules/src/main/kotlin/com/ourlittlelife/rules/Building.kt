package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import com.ourlittlelife.rules.Resource.BRICKS
import com.ourlittlelife.rules.Resource.CLOTH
import com.ourlittlelife.rules.Resource.PLANKS
import kotlinx.serialization.Serializable

/** The rooms a home can grow into. Sizes are in tiles; costs and times are starting values. */
enum class RoomKind(val label: String, val width: Int, val height: Int, val costs: Map<Resource, Int>, val minutes: Int) {
    CABIN("Cabin", 6, 5, mapOf(PLANKS to 40), 6 * 60),
    KITCHEN("Kitchen", 4, 5, mapOf(PLANKS to 30, BRICKS to 20), 6 * 60),
    BATHROOM("Bathroom", 3, 4, mapOf(PLANKS to 20, BRICKS to 30), 5 * 60),
    LIVING_ROOM("Living room", 6, 5, mapOf(PLANKS to 50, CLOTH to 20), 7 * 60),
    BEDROOM("Second bedroom", 4, 5, mapOf(PLANKS to 40, CLOTH to 20), 6 * 60),
    GARDEN("Garden", 5, 4, mapOf(PLANKS to 20, BRICKS to 10), 4 * 60),
}

@Serializable
data class Room(val id: String, val kind: RoomKind, val x: Int, val y: Int) {

    fun covers(tileX: Int, tileY: Int): Boolean =
        tileX in x until x + kind.width && tileY in y until y + kind.height

    fun tiles(): List<Pair<Int, Int>> =
        (x until x + kind.width).flatMap { tx -> (y until y + kind.height).map { ty -> tx to ty } }
}

/** Whether a room can go somewhere, and if not, why. */
sealed interface Placement {
    data object Ok : Placement
    data object OffPlot : Placement
    data object Blocked : Placement
    data object CabinFirst : Placement
    data object NotConnected : Placement
    data object AlreadyBuilt : Placement
}

sealed interface BuildResult {
    data class Done(val character: Character, val camp: Camp) : BuildResult
    data class CantPlace(val reason: Placement) : BuildResult
    data class Missing(val shortfall: Map<Resource, Int>) : BuildResult
    data class Refused(val reason: ActionResult) : BuildResult
}

object Building {

    val napInBed = Action("nap_bed", "Nap in the bed", 120, mapOf(ENERGY to 40))

    /** Building a room is a long day's work. */
    fun actionFor(kind: RoomKind): Action =
        Action("build_${kind.name.lowercase()}", "Build the ${kind.label.lowercase()}", kind.minutes, mapOf(ENERGY to -15), costs = kind.costs)

    /**
     * Rooms snap together on the grid: the cabin comes first, each kind is built once,
     * nothing may be in the way, and every later room shares a wall with one already built.
     */
    fun check(plot: Plot, kind: RoomKind, x: Int, y: Int): Placement {
        if (x < 0 || y < 0 || x + kind.width > plot.size || y + kind.height > plot.size) return Placement.OffPlot
        if (plot.rooms.any { it.kind == kind }) return Placement.AlreadyBuilt
        if (kind != RoomKind.CABIN && plot.rooms.none { it.kind == RoomKind.CABIN }) return Placement.CabinFirst

        val footprint = Room("new", kind, x, y).tiles()
        val blocked = footprint.any { (tx, ty) -> plot.objectAt(tx, ty) != null || plot.roomAt(tx, ty) != null }
        if (blocked) return Placement.Blocked

        if (plot.rooms.isNotEmpty()) {
            val touches = footprint.any { (tx, ty) ->
                listOf(tx - 1 to ty, tx + 1 to ty, tx to ty - 1, tx to ty + 1).any { (nx, ny) -> plot.roomAt(nx, ny) != null }
            }
            if (!touches) return Placement.NotConnected
        }
        return Placement.Ok
    }
}

/** What each room offers when tapped. */
object RoomActions {
    fun forKind(kind: RoomKind): List<Action> = when (kind) {
        RoomKind.CABIN -> listOf(Building.napInBed)
        else -> emptyList()
    }
}
