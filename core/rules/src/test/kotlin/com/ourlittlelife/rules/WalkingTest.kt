package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalkingTest {

    private val empty = Plot(10, emptyList())

    /** Every step moves exactly one tile up, down, left or right. */
    private fun assertConnected(start: Pair<Int, Int>, path: List<Pair<Int, Int>>) {
        var at = start
        path.forEach { step ->
            val distance = Math.abs(step.first - at.first) + Math.abs(step.second - at.second)
            assertEquals(1, distance, "jumped from $at to $step")
            at = step
        }
    }

    @Test
    fun `walking to the next tile is one step`() {
        assertEquals(listOf(3 to 2), Walking.path(empty, 2 to 2, 3 to 2))
    }

    @Test
    fun `walking to where you stand takes no steps`() {
        assertEquals(emptyList(), Walking.path(empty, 4 to 4, 4 to 4))
    }

    @Test
    fun `the path is as short as possible on open ground`() {
        val path = Walking.path(empty, 0 to 0, 3 to 4)!!
        assertEquals(7, path.size)
        assertEquals(3 to 4, path.last())
        assertConnected(0 to 0, path)
    }

    @Test
    fun `paths go around trees`() {
        val plot = Plot(10, listOf(PlotObject("tree", ObjectKind.TREE, 3, 2)))
        val path = Walking.path(plot, 2 to 2, 4 to 2)!!
        assertTrue((3 to 2) !in path)
        assertEquals(4, path.size)
        assertConnected(2 to 2, path)
    }

    @Test
    fun `you can't walk onto a tree, off the plot, or onto another character`() {
        val plot = Plot(10, listOf(PlotObject("tree", ObjectKind.TREE, 3, 2)))
        assertNull(Walking.path(plot, 0 to 0, 3 to 2))
        assertNull(Walking.path(plot, 0 to 0, 10 to 2))
        assertNull(Walking.path(plot, 0 to 0, 5 to 5, occupied = setOf(5 to 5)))
    }

    @Test
    fun `other characters are walked around`() {
        val path = Walking.path(empty, 2 to 2, 4 to 2, occupied = setOf(3 to 2))!!
        assertTrue((3 to 2) !in path)
    }

    @Test
    fun `room floors are walkable`() {
        val plot = Plot(12, emptyList(), rooms = listOf(Room("cabin", RoomKind.CABIN, 2, 2)))
        val path = assertNotNull(Walking.path(plot, 0 to 0, 4 to 4))
        assertEquals(4 to 4, path.last())
    }

    @Test
    fun `a spot walled in on every side can't be reached`() {
        val rocks = listOf(1 to 0, 0 to 1, 2 to 1, 1 to 2).mapIndexed { i, (x, y) -> PlotObject("rock-$i", ObjectKind.ROCK, x, y) }
        assertNull(Walking.path(Plot(5, rocks), 4 to 4, 1 to 1))
    }

    @Test
    fun `to use something you stand on the nearest free tile beside it`() {
        val tent = PlotObject("tent", ObjectKind.TENT, 4, 4)
        val plot = Plot(10, listOf(tent))
        val spot = Walking.spotBeside(plot, tent, from = 0 to 0)!!
        assertTrue(spot == 3 to 4 || spot == 4 to 3, "expected the near corner, got $spot")
        assertNull(plot.objectAt(spot.first, spot.second))
    }

    @Test
    fun `if you are already beside it you stay put`() {
        val tree = PlotObject("tree", ObjectKind.TREE, 5, 5)
        assertEquals(5 to 6, Walking.spotBeside(Plot(10, listOf(tree)), tree, from = 5 to 6))
    }

    @Test
    fun `the starter camp can walk to every tree, rock and bush`() {
        val plot = StarterPlot.create()
        plot.objects.forEach { obj ->
            assertNotNull(Walking.spotBeside(plot, obj, from = 11 to 11), "can't reach ${obj.id}")
        }
    }
}
