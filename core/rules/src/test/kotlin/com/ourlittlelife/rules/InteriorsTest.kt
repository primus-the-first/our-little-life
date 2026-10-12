package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteriorsTest {

    private val jo = Character("Jo")
    private val yard = WorldMap.YARD

    /** A shared world with the cabin built in the yard, at the same spot used in WorldMapTest. */
    private val world: World = run {
        val fresh = assertIs<JoinResult.Joined>(Worlds.join(Worlds.create("w", "KQ7XMB", Mode.PARTNERS, "jo", "Jo"), "maya", "Maya")).world
        val stocked = fresh.camp.copy(storage = Storage().plus(mapOf(Resource.PLANKS to 100)))
        val built = assertIs<BuildResult.Done>(stocked.build(jo, RoomKind.CABIN, yard.x + 7, yard.y + 14)).camp
        fresh.copy(camp = built)
    }
    private val cabin = world.camp.plot.rooms.single()

    @Test
    fun `work pays coins into the shared chest`() {
        assertEquals(Resource.COINS to StarterActions.WAGE, StarterActions.work.yields.entries.single().toPair())
        val sign = world.camp.plot.objects.single { it.kind == ObjectKind.SIGNPOST }
        val paid = assertIs<UseResult.Done>(world.camp.use(jo, sign, StarterActions.work)).camp
        assertEquals(StarterActions.WAGE, paid.storage[Resource.COINS])
    }

    @Test
    fun `inside a room is a bigger grid, with the door in the middle of the bottom wall`() {
        assertEquals(12, Interiors.width(RoomKind.CABIN))
        assertEquals(10, Interiors.height(RoomKind.CABIN))
        assertEquals(6 to 9, Interiors.door(RoomKind.CABIN))
    }

    @Test
    fun `the cabin comes with a bed, and older saves get one too`() {
        val bed = cabin.furniture.single()
        assertEquals(FurnitureKind.BED, bed.kind)
        assertEquals(bed, Interiors.furnitureAt(cabin, bed.x + 1, bed.y + 1))
        val old = Worlds.decode(Worlds.encode(world).replace(Regex(",\"furniture\":\\[[^\\]]*]"), ""))
        assertEquals(cabin.furniture, old.camp.plot.rooms.single().furniture)
        assertTrue(Room("garden", RoomKind.GARDEN, 0, 0).furniture.isEmpty())
    }

    @Test
    fun `going in puts you at the door, and coming out puts you on the doorstep`() {
        val inside = assertNotNull(Interiors.enter(world, "jo", cabin.id))
        assertEquals(Inside(cabin.id, 6, 9), inside.player("jo").inside)
        assertEquals(listOf("jo"), Interiors.whoIsIn(inside, cabin.id).map { it.id })

        val out = Interiors.leave(inside, "jo")
        assertNull(out.player("jo").inside)
        assertEquals(Interiors.doorstep(cabin), out.player("jo").tile)
        assertTrue(Walking.isWalkable(out.camp.plot, Interiors.doorstep(cabin)))
    }

    @Test
    fun `you can't go into a room that isn't there, or while asleep`() {
        assertNull(Interiors.enter(world, "jo", "kitchen"))
        assertNull(Interiors.enter(Worlds.sleep(world, "jo", 0), "jo", cabin.id))
    }

    @Test
    fun `walking inside goes round the furniture and stays within the walls`() {
        val bed = cabin.furniture.single()
        val door = Interiors.door(RoomKind.CABIN)
        assertTrue(Interiors.isFree(cabin, door))
        assertTrue(!Interiors.isFree(cabin, bed.x to bed.y))
        assertTrue(!Interiors.isFree(cabin, -1 to 0))
        assertTrue(!Interiors.isFree(cabin, 12 to 0))
        val route = assertNotNull(Interiors.path(cabin, door, 0 to 0))
        assertTrue(route.none { Interiors.furnitureAt(cabin, it.first, it.second) != null })
        val spot = assertNotNull(Interiors.spotBeside(cabin, bed, door))
        assertTrue(Interiors.isFree(cabin, spot))
    }

    @Test
    fun `moving inside is saved, and both of you can be in the same room`() {
        val both = Interiors.enter(assertNotNull(Interiors.enter(world, "jo", cabin.id)), "maya", cabin.id)!!
        val moved = Interiors.moveTo(both, "jo", 4 to 4)
        assertEquals(Inside(cabin.id, 4, 4), moved.player("jo").inside)
        assertEquals(setOf("jo", "maya"), Interiors.whoIsIn(moved, cabin.id).map { it.id }.toSet())
    }

    @Test
    fun `the bed offers a nap`() {
        val bed = cabin.furniture.single()
        assertEquals(listOf(Building.napInBed), FurnitureActions.forKind(FurnitureKind.BED))
        val done = assertIs<UseResult.Done>(world.camp.useFurniture(jo, cabin, bed, Building.napInBed))
        assertEquals(Rules.DAY_START + Building.napInBed.minutes, done.character.clock.minute)
    }
}
