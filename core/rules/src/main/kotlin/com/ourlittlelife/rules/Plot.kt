package com.ourlittlelife.rules

/** Everything that can stand on the plot. Sizes are in tiles. */
enum class ObjectKind(val label: String, val width: Int = 1, val height: Int = 1) {
    TENT("Tent", width = 2, height = 2),
    CAMPFIRE("Campfire"),
    WASHBASIN("Washbasin"),
    WORKBENCH("Workbench"),
    CHEST("Storage chest"),
    SIGNPOST("Path to town"),
    TREE("Oak tree"),
    ROCK("Rock"),
    BUSH("Berry bush"),
}

data class PlotObject(val id: String, val kind: ObjectKind, val x: Int, val y: Int) {

    fun covers(tileX: Int, tileY: Int): Boolean =
        tileX in x until x + kind.width && tileY in y until y + kind.height

    fun tiles(): List<Pair<Int, Int>> =
        (x until x + kind.width).flatMap { tx -> (y until y + kind.height).map { ty -> tx to ty } }
}

/** The shared piece of land, [size] × [size] tiles, with x running right and y running down. */
data class Plot(val size: Int, val objects: List<PlotObject>) {

    fun objectAt(tileX: Int, tileY: Int): PlotObject? = objects.firstOrNull { it.covers(tileX, tileY) }
}

/** Day 1: a small camp in the middle of bare land, with trees, rocks and bushes to gather from. */
object StarterPlot {
    const val SIZE = 24

    fun create(): Plot {
        val objects = mutableListOf<PlotObject>()
        fun place(kind: ObjectKind, vararg spots: Pair<Int, Int>) {
            spots.forEachIndexed { i, (x, y) ->
                val id = if (spots.size == 1) kind.name.lowercase() else "${kind.name.lowercase()}-${i + 1}"
                objects += PlotObject(id, kind, x, y)
            }
        }

        place(ObjectKind.TENT, 9 to 9)
        place(ObjectKind.CAMPFIRE, 12 to 11)
        place(ObjectKind.WASHBASIN, 14 to 9)
        place(ObjectKind.WORKBENCH, 8 to 12)
        place(ObjectKind.CHEST, 9 to 12)
        place(ObjectKind.SIGNPOST, 11 to 22)

        place(
            ObjectKind.TREE,
            3 to 3, 6 to 2, 18 to 3, 20 to 6, 2 to 8,
            21 to 12, 4 to 16, 17 to 18, 20 to 20, 2 to 20,
        )
        place(ObjectKind.ROCK, 16 to 14, 17 to 15, 5 to 13, 6 to 19, 15 to 4)
        place(ObjectKind.BUSH, 7 to 6, 15 to 7, 19 to 15, 3 to 12, 13 to 17)

        return Plot(SIZE, objects)
    }
}

/** Which actions each kind of object offers when tapped. */
object ObjectActions {
    fun forKind(kind: ObjectKind): List<Action> = when (kind) {
        ObjectKind.TENT -> listOf(StarterActions.nap)
        ObjectKind.CAMPFIRE -> listOf(StarterActions.cook, StarterActions.snack, StarterActions.stargaze)
        ObjectKind.WASHBASIN -> listOf(StarterActions.wash)
        ObjectKind.SIGNPOST -> listOf(StarterActions.work)
        ObjectKind.TREE -> listOf(Gathering.chop)
        ObjectKind.ROCK -> listOf(Gathering.breakRock)
        ObjectKind.BUSH -> listOf(Gathering.pickFiber)
        // Crafting arrives with the workbench; the chest just shows what's stored.
        ObjectKind.WORKBENCH, ObjectKind.CHEST -> emptyList()
    }
}
