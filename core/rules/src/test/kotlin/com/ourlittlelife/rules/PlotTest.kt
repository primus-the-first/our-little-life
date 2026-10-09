package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlotTest {

    private val plot = StarterPlot.create()

    @Test
    fun `the plot is 24 by 24 tiles`() {
        assertEquals(24, plot.size)
    }

    @Test
    fun `every object sits fully inside the plot`() {
        plot.objects.forEach { obj ->
            assertTrue(obj.x >= 0 && obj.y >= 0, "${obj.id} starts outside the plot")
            assertTrue(obj.x + obj.kind.width <= plot.size && obj.y + obj.kind.height <= plot.size, "${obj.id} runs off the plot")
        }
    }

    @Test
    fun `no two objects overlap`() {
        val taken = mutableMapOf<Pair<Int, Int>, String>()
        plot.objects.forEach { obj ->
            obj.tiles().forEach { tile ->
                val other = taken.put(tile, obj.id)
                assertNull(other, "${obj.id} overlaps $other at $tile")
            }
        }
    }

    @Test
    fun `object ids are unique`() {
        assertEquals(plot.objects.size, plot.objects.map { it.id }.toSet().size)
    }

    @Test
    fun `the starter camp has one of each camp object`() {
        listOf(ObjectKind.TENT, ObjectKind.CAMPFIRE, ObjectKind.WASHBASIN, ObjectKind.WORKBENCH, ObjectKind.CHEST, ObjectKind.SIGNPOST)
            .forEach { kind -> assertEquals(1, plot.objects.count { it.kind == kind }, "expected one $kind") }
    }

    @Test
    fun `the starter plot has enough to gather`() {
        assertTrue(plot.objects.count { it.kind == ObjectKind.TREE } >= 8)
        assertTrue(plot.objects.count { it.kind == ObjectKind.ROCK } >= 4)
        assertTrue(plot.objects.count { it.kind == ObjectKind.BUSH } >= 4)
    }

    @Test
    fun `a tap on any tile of a big object finds it`() {
        val tent = plot.objects.single { it.kind == ObjectKind.TENT }
        assertEquals(tent, plot.objectAt(tent.x, tent.y))
        assertEquals(tent, plot.objectAt(tent.x + 1, tent.y + 1))
    }

    @Test
    fun `an empty tile has no object`() {
        val empty = (0 until plot.size).flatMap { x -> (0 until plot.size).map { y -> x to y } }
            .first { (x, y) -> plot.objectAt(x, y) == null }
        assertNull(plot.objectAt(empty.first, empty.second))
        assertNull(plot.objectAt(-1, 0))
        assertNull(plot.objectAt(0, plot.size))
    }

    @Test
    fun `every starter action is offered by some object`() {
        val offered = ObjectKind.entries.flatMap { ObjectActions.forKind(it) }.toSet()
        StarterActions.all.forEach { assertTrue(it in offered, "${it.name} can't be reached from any object") }
    }

    @Test
    fun `gathering spots offer nothing until gathering exists`() {
        listOf(ObjectKind.TREE, ObjectKind.ROCK, ObjectKind.BUSH)
            .forEach { assertTrue(ObjectActions.forKind(it).isEmpty()) }
    }
}
