package com.ourlittlelife.rules

/**
 * Getting around the plot: one tile at a time, up, down, left or right.
 * Objects block the way; room floors don't. Tiles are (x, y) pairs.
 */
object Walking {

    private val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    /** Free to stand on: inside the plot, not water or fenced off, nothing built on it, and nobody else standing there. */
    fun isWalkable(plot: Plot, tile: Pair<Int, Int>, occupied: Set<Pair<Int, Int>> = emptySet()): Boolean {
        val (x, y) = tile
        return x in 0 until plot.size && y in 0 until plot.size && !plot.terrainAt(x, y).blocks &&
            plot.objectAt(x, y) == null && tile !in occupied
    }

    /**
     * The shortest route from [from] to [to], not counting the starting tile.
     * Empty when you're already there; null when [to] can't be reached.
     */
    fun path(
        plot: Plot,
        from: Pair<Int, Int>,
        to: Pair<Int, Int>,
        occupied: Set<Pair<Int, Int>> = emptySet(),
    ): List<Pair<Int, Int>>? = path(from, to) { isWalkable(plot, it, occupied) }

    /** The shortest route on any grid where [isFree] says which tiles can be stood on. */
    fun path(from: Pair<Int, Int>, to: Pair<Int, Int>, isFree: (Pair<Int, Int>) -> Boolean): List<Pair<Int, Int>>? {
        if (from == to) return emptyList()
        if (!isFree(to)) return null
        val cameFrom = explore(from, isFree) { it == to } ?: return null
        return routeTo(to, from, cameFrom)
    }

    /** The free tile beside [obj] that's quickest to walk to from [from], or null if it's walled in. */
    fun spotBeside(
        plot: Plot,
        obj: PlotObject,
        from: Pair<Int, Int>,
        occupied: Set<Pair<Int, Int>> = emptySet(),
    ): Pair<Int, Int>? = spotBeside(obj.tiles(), from) { isWalkable(plot, it, occupied) }

    /** The free tile next to [footprint] that's quickest to reach from [from], on any grid. */
    fun spotBeside(footprint: List<Pair<Int, Int>>, from: Pair<Int, Int>, isFree: (Pair<Int, Int>) -> Boolean): Pair<Int, Int>? {
        val taken = footprint.toSet()
        val beside = taken
            .flatMap { (x, y) -> directions.map { (dx, dy) -> x + dx to y + dy } }
            .filter { it !in taken && isFree(it) }
            .toSet()
        if (from in beside) return from

        var found: Pair<Int, Int>? = null
        explore(from, isFree) { tile -> (tile in beside).also { if (it) found = tile } }
        return found
    }

    /**
     * Breadth-first search outward from [start] until [isGoal] says stop.
     * Returns how each tile was reached, or null if no goal was found.
     */
    private fun explore(
        start: Pair<Int, Int>,
        isFree: (Pair<Int, Int>) -> Boolean,
        isGoal: (Pair<Int, Int>) -> Boolean,
    ): Map<Pair<Int, Int>, Pair<Int, Int>>? {
        val cameFrom = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>>()
        val queue = ArrayDeque(listOf(start))
        val seen = mutableSetOf(start)
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            for ((dx, dy) in directions) {
                val next = x + dx to y + dy
                if (next in seen || !isFree(next)) continue
                seen += next
                cameFrom[next] = x to y
                if (isGoal(next)) return cameFrom
                queue.addLast(next)
            }
        }
        return null
    }

    private fun routeTo(
        goal: Pair<Int, Int>,
        start: Pair<Int, Int>,
        cameFrom: Map<Pair<Int, Int>, Pair<Int, Int>>,
    ): List<Pair<Int, Int>> {
        val route = ArrayDeque<Pair<Int, Int>>()
        var at = goal
        while (at != start) {
            route.addFirst(at)
            at = cameFrom.getValue(at)
        }
        return route.toList()
    }
}
