package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BuildingTest {

    private val emptyLand = Plot(24, emptyList())
    private val jo = Character("Jo")
    private val enoughForEverything = Storage().plus(mapOf(Resource.PLANKS to 200, Resource.BRICKS to 100, Resource.CLOTH to 100))

    private fun Camp.built(kind: RoomKind, x: Int, y: Int, character: Character = jo): BuildResult.Done =
        assertIs<BuildResult.Done>(build(character, kind, x, y))

    @Test
    fun `the cabin has to come first`() {
        assertEquals(Placement.CabinFirst, Building.check(emptyLand, RoomKind.KITCHEN, 5, 5))
        assertEquals(Placement.Ok, Building.check(emptyLand, RoomKind.CABIN, 5, 5))
    }

    @Test
    fun `rooms have to fit on the plot`() {
        assertEquals(Placement.OffPlot, Building.check(emptyLand, RoomKind.CABIN, 20, 5))
        assertEquals(Placement.OffPlot, Building.check(emptyLand, RoomKind.CABIN, -1, 5))
        assertEquals(Placement.OffPlot, Building.check(emptyLand, RoomKind.CABIN, 5, 21))
    }

    @Test
    fun `trees, rocks and camp objects are in the way`() {
        val plot = StarterPlot.create()
        val tree = plot.objects.first { it.kind == ObjectKind.TREE }
        assertEquals(Placement.Blocked, Building.check(plot, RoomKind.CABIN, tree.x, tree.y))
        assertEquals(Placement.Ok, Building.check(plot, RoomKind.CABIN, 8, 3))
    }

    @Test
    fun `building the cabin takes 40 planks and 6 hours`() {
        val result = Camp(emptyLand, Storage().plus(mapOf(Resource.PLANKS to 45))).built(RoomKind.CABIN, 5, 5)
        assertEquals(5, result.camp.storage[Resource.PLANKS])
        assertEquals(Rules.DAY_START + 6 * 60, result.character.clock.minute)
        assertEquals(listOf(Room("cabin", RoomKind.CABIN, 5, 5)), result.camp.plot.rooms)
    }

    @Test
    fun `without enough planks nothing is built`() {
        val camp = Camp(emptyLand, Storage().plus(mapOf(Resource.PLANKS to 15)))
        assertEquals(BuildResult.Missing(mapOf(Resource.PLANKS to 25)), camp.build(jo, RoomKind.CABIN, 5, 5))
    }

    @Test
    fun `a bad spot is refused before anything is spent`() {
        val camp = Camp(emptyLand, enoughForEverything)
        assertEquals(BuildResult.CantPlace(Placement.OffPlot), camp.build(jo, RoomKind.CABIN, 22, 22))
    }

    @Test
    fun `later rooms have to join an existing room`() {
        val withCabin = Camp(emptyLand, enoughForEverything).built(RoomKind.CABIN, 5, 5)
        val plot = withCabin.camp.plot
        assertEquals(Placement.NotConnected, Building.check(plot, RoomKind.KITCHEN, 15, 15))
        // The cabin spans x 5..10, so a kitchen starting at x 11 shares its wall.
        assertEquals(Placement.Ok, Building.check(plot, RoomKind.KITCHEN, 11, 5))
    }

    @Test
    fun `rooms can't overlap`() {
        val plot = Camp(emptyLand, enoughForEverything).built(RoomKind.CABIN, 5, 5).camp.plot
        assertEquals(Placement.Blocked, Building.check(plot, RoomKind.KITCHEN, 9, 5))
    }

    @Test
    fun `each kind of room is built once`() {
        val plot = Camp(emptyLand, enoughForEverything).built(RoomKind.CABIN, 5, 5).camp.plot
        assertEquals(Placement.AlreadyBuilt, Building.check(plot, RoomKind.CABIN, 11, 5))
    }

    @Test
    fun `a tap inside a room finds it`() {
        val plot = Camp(emptyLand, enoughForEverything).built(RoomKind.CABIN, 5, 5).camp.plot
        assertEquals(RoomKind.CABIN, plot.roomAt(7, 7)?.kind)
        assertNull(plot.roomAt(4, 4))
    }

    @Test
    fun `sleeping under a real roof restores all your energy`() {
        val tired = Rules.sleep(Character("Jo", needs = Needs.full().plus(mapOf(Need.ENERGY to -60))))
        val inTent = Rules.advanceDay(listOf(tired)).single()
        val inCabin = Rules.advanceDay(listOf(tired), restful = true).single()
        assertEquals(100, inTent.needs[Need.ENERGY]) // 40 + 70, capped
        val veryTired = Rules.sleep(Character("Jo", needs = Needs.of(Need.ENERGY to 10)))
        assertEquals(80, Rules.advanceDay(listOf(veryTired)).single().needs[Need.ENERGY])
        assertEquals(100, Rules.advanceDay(listOf(veryTired), restful = true).single().needs[Need.ENERGY])
        assertEquals(100, inCabin.needs[Need.ENERGY])
    }

    @Test
    fun `the cabin has a bed to nap in`() {
        assertEquals(listOf(Building.napInBed), RoomActions.forKind(RoomKind.CABIN))
    }
}
