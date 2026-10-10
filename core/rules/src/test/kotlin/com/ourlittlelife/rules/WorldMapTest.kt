package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldMapTest {

    private val plot = WorldMap.expand(StarterPlot.create())
    private val yard = WorldMap.YARD
    private val home = yard.x + 11 to yard.y + 11

    @Test
    fun `the world is 64 by 64 with the old plot as your yard in the middle`() {
        assertEquals(64, plot.size)
        assertEquals(yard, plot.yard)
        assertEquals(StarterPlot.SIZE, yard.width)
        assertEquals(StarterPlot.SIZE, yard.height)
        val tent = plot.objects.single { it.kind == ObjectKind.TENT }
        assertEquals(yard.x + 9 to yard.y + 9, tent.x to tent.y)
    }

    @Test
    fun `the yard is plain grass, so nothing changes for building there`() {
        for (x in yard.x until yard.x + yard.width) for (y in yard.y until yard.y + yard.height) {
            assertEquals(Terrain.GRASS, plot.terrainAt(x, y), "($x, $y)")
        }
    }

    @Test
    fun `the east side is kept for the racecourse`() {
        val course = WorldMap.RACECOURSE
        assertTrue(course.x >= yard.x + yard.width, "racecourse overlaps the yard")
        assertTrue(course.width >= 12 && course.height >= 40, "too small for a track")
        assertEquals(Terrain.TRACK, plot.terrainAt(course.x + 1, course.y + 1))
        assertTrue(plot.objects.none { obj -> obj.tiles().any { (x, y) -> course.contains(x, y) } }, "something stands on the racecourse")
    }

    @Test
    fun `the pond and racecourse can't be walked on, paths and the hill can`() {
        assertFalse(Walking.isWalkable(plot, WorldMap.POND.x + 1 to WorldMap.POND.y + 1))
        assertFalse(Walking.isWalkable(plot, WorldMap.RACECOURSE.x + 2 to WorldMap.RACECOURSE.y + 2))
        assertTrue(Walking.isWalkable(plot, WorldMap.HILL.x + 1 to WorldMap.HILL.y + WorldMap.HILL.height - 1))
        val path = WorldMap.PATHS.first()
        assertTrue(Walking.isWalkable(plot, path.x to path.y))
    }

    @Test
    fun `nothing stands in the water, on a path, or on another object`() {
        val taken = mutableMapOf<Pair<Int, Int>, String>()
        plot.objects.forEach { obj ->
            obj.tiles().forEach { (x, y) ->
                assertTrue(x in 0 until plot.size && y in 0 until plot.size, "${obj.id} is off the map")
                assertTrue(plot.terrainAt(x, y) == Terrain.GRASS || plot.terrainAt(x, y) == Terrain.HILL, "${obj.id} stands on ${plot.terrainAt(x, y)}")
                assertNull(taken.put(x to y, obj.id), "${obj.id} overlaps at ($x, $y)")
            }
        }
        assertEquals(plot.objects.size, plot.objects.map { it.id }.toSet().size, "object ids repeat")
    }

    @Test
    fun `everything out there can be reached from home`() {
        plot.objects.forEach { obj -> assertNotNull(Walking.spotBeside(plot, obj, from = home), "can't reach ${obj.id}") }
        assertNotNull(Walking.path(plot, home, WorldMap.PATHS.last().let { it.x to it.y }), "can't walk the trail")
    }

    @Test
    fun `the forest has plenty more to gather, and the hill has a bench for stargazing`() {
        val wildTrees = plot.objects.count { it.kind == ObjectKind.TREE && !yard.contains(it.x, it.y) }
        assertTrue(wildTrees >= 20, "only $wildTrees trees in the wild")
        val bench = plot.objects.single { it.kind == ObjectKind.BENCH }
        assertTrue(WorldMap.HILL.contains(bench.x, bench.y))
        assertTrue(StarterActions.stargaze in ObjectActions.forKind(ObjectKind.BENCH))
    }

    @Test
    fun `rooms can only go in your yard`() {
        val outside = Building.check(plot, RoomKind.CABIN, yard.x - 8, yard.y + 4)
        assertEquals(Placement.OffPlot, outside)
        val straddling = Building.check(plot, RoomKind.CABIN, yard.x + yard.width - 3, yard.y + 1)
        assertEquals(Placement.OffPlot, straddling)
        assertEquals(Placement.Ok, Building.check(plot, RoomKind.CABIN, yard.x + 7, yard.y + 14))
    }

    @Test
    fun `an old 24 by 24 world moves into the yard, people and rooms included`() {
        val old = Worlds.decode(Worlds.encode(oldWorld()))
        val jo = old.player("jo")
        assertEquals(64, old.camp.plot.size)
        assertEquals(yard.x + 11 to yard.y + 11, jo.tile)
        assertEquals(yard.x + 7 to yard.y + 14, old.camp.plot.rooms.single().let { it.x to it.y })
        assertEquals(2, old.camp.usesLeft(old.camp.plot.objects.first { it.id == "tree-1" }))
    }

    @Test
    fun `upgrading twice changes nothing`() {
        val once = WorldMap.upgrade(oldWorld())
        assertEquals(once, WorldMap.upgrade(once))
    }

    @Test
    fun `a new world starts in the big map, by the fire`() {
        val world = Worlds.create("w", "KQ7XMB", Mode.BESTIES, "jo", "Jo")
        assertEquals(plot, world.camp.plot)
        assertEquals(home, world.player("jo").tile)
        val joined = assertIs<JoinResult.Joined>(Worlds.join(world, "maya", "Maya")).world
        assertTrue(Walking.isWalkable(plot, joined.player("maya").tile))
    }

    /** A world saved before the map grew: the 24×24 plot, a cabin, Jo by the fire, a tree used twice. */
    private fun oldWorld(): World {
        val jo = Character("Jo")
        var camp = Camp(StarterPlot.create(), Storage().plus(mapOf(Resource.PLANKS to 200)))
        val tree = camp.plot.objects.first { it.id == "tree-1" }
        repeat(2) { camp = assertIs<UseResult.Done>(camp.use(jo, tree, Gathering.chop)).camp }
        camp = assertIs<BuildResult.Done>(camp.build(jo, RoomKind.CABIN, 7, 14)).camp
        return World("w", "KQ7XMB", Mode.BESTIES, camp, mapOf("jo" to Player("jo", jo, 11, 11)))
    }
}
