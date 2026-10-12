package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FurnishingTest {

    private val yard = WorldMap.YARD

    /** Jo and Maya, a cabin with its bed at (1, 1), and a well-stocked chest. */
    private val world: World = run {
        val fresh = assertIs<JoinResult.Joined>(Worlds.join(Worlds.create("w", "KQ7XMB", Mode.PARTNERS, "jo", "Jo"), "maya", "Maya")).world
        val stocked = fresh.camp.copy(storage = Storage().plus(Resource.entries.associateWith { 500 }))
        val built = assertIs<BuildResult.Done>(stocked.build(Character("Jo"), RoomKind.CABIN, yard.x + 7, yard.y + 14)).camp
        fresh.copy(camp = built)
    }
    private val cabin = world.camp.plot.rooms.single()

    private fun room(w: World): Room = w.camp.plot.rooms.single()

    @Test
    fun `some furniture is made from materials and some is bought with coins`() {
        val crafted = FurnitureKind.entries.filter { !it.bought }
        val bought = FurnitureKind.entries.filter { it.bought }
        assertTrue(crafted.size >= 3 && bought.size >= 3)
        assertTrue(crafted.all { kind -> kind.costs.isNotEmpty() && Resource.COINS !in kind.costs })
        assertTrue(bought.all { kind -> kind.costs.keys == setOf(Resource.COINS) })
    }

    @Test
    fun `every category has something in it`() {
        FurnitureCategory.entries.forEach { category ->
            assertTrue(FurnitureKind.entries.any { it.category == category }, "nothing in $category")
        }
    }

    @Test
    fun `placing a piece takes its cost from the chest`() {
        val placed = assertIs<PlaceResult.Done>(Interiors.place(world, cabin.id, FurnitureKind.SOFA, 5, 2)).world
        val sofa = room(placed).furniture.single { it.kind == FurnitureKind.SOFA }
        assertEquals(5 to 2, sofa.x to sofa.y)
        assertEquals(500 - FurnitureKind.SOFA.costs.getValue(Resource.COINS), placed.camp.storage[Resource.COINS])
    }

    @Test
    fun `two of the same piece get different ids`() {
        val one = assertIs<PlaceResult.Done>(Interiors.place(world, cabin.id, FurnitureKind.CHAIR, 5, 2)).world
        val two = assertIs<PlaceResult.Done>(Interiors.place(one, cabin.id, FurnitureKind.CHAIR, 7, 2)).world
        val chairs = room(two).furniture.filter { it.kind == FurnitureKind.CHAIR }
        assertEquals(2, chairs.map { it.id }.toSet().size)
    }

    @Test
    fun `a piece has to fit on the floor, clear of other furniture and the door`() {
        val door = Interiors.door(RoomKind.CABIN)
        assertEquals(FurniturePlacement.OffFloor, Interiors.check(cabin, FurnitureKind.SOFA, Interiors.width(RoomKind.CABIN) - 1, 0))
        assertEquals(FurniturePlacement.Blocked, Interiors.check(cabin, FurnitureKind.CHAIR, 2, 2))
        assertEquals(FurniturePlacement.BlocksDoor, Interiors.check(cabin, FurnitureKind.CHAIR, door.first, door.second))
        assertEquals(FurniturePlacement.Ok, Interiors.check(cabin, FurnitureKind.CHAIR, 5, 2))
    }

    @Test
    fun `nothing can go where someone is standing`() {
        val inside = assertNotNull(Interiors.enter(world, "jo", cabin.id)).let { Interiors.moveTo(it, "jo", 5 to 2) }
        assertIs<PlaceResult.CantPlace>(Interiors.place(inside, cabin.id, FurnitureKind.CHAIR, 5, 2))
    }

    @Test
    fun `short of coins or materials, nothing is placed or spent`() {
        val broke = world.copy(camp = world.camp.copy(storage = Storage()))
        val result = assertIs<PlaceResult.Missing>(Interiors.place(broke, cabin.id, FurnitureKind.STOVE, 5, 2))
        assertEquals(FurnitureKind.STOVE.costs, result.shortfall)
    }

    @Test
    fun `moving a piece is free, and putting it away gives its cost back`() {
        val placed = assertIs<PlaceResult.Done>(Interiors.place(world, cabin.id, FurnitureKind.TABLE, 5, 2)).world
        val table = room(placed).furniture.single { it.kind == FurnitureKind.TABLE }
        val moved = assertIs<PlaceResult.Done>(Interiors.move(placed, cabin.id, table.id, 6, 3)).world
        assertEquals(6 to 3, room(moved).furniture.single { it.id == table.id }.let { it.x to it.y })
        assertEquals(placed.camp.storage, moved.camp.storage)

        // A piece can be nudged onto tiles it already covers.
        assertIs<PlaceResult.Done>(Interiors.move(moved, cabin.id, table.id, 7, 3))

        val away = Interiors.putAway(moved, cabin.id, table.id)
        assertNull(room(away).furniture.firstOrNull { it.id == table.id })
        assertEquals(world.camp.storage, away.camp.storage)
    }

    @Test
    fun `rugs can be walked on, other furniture can't`() {
        val placed = assertIs<PlaceResult.Done>(Interiors.place(world, cabin.id, FurnitureKind.RUG, 5, 4)).world
        val placedRoom = room(placed)
        assertTrue(Interiors.isFree(placedRoom, 5 to 4))
        assertTrue(!Interiors.isFree(placedRoom, 1 to 1))
    }

    @Test
    fun `furniture brings things to do, and decor is just for looks`() {
        assertTrue(FurnitureActions.forKind(FurnitureKind.STOVE).isNotEmpty())
        assertTrue(FurnitureActions.forKind(FurnitureKind.SOFA).isNotEmpty())
        assertTrue(FurnitureActions.forKind(FurnitureKind.PLANT).isEmpty())
        assertTrue(FurnitureActions.forKind(FurnitureKind.RUG).isEmpty())
    }

    @Test
    fun `the sofa is more social with your partner in the room`() {
        val placed = assertIs<PlaceResult.Done>(Interiors.place(world, cabin.id, FurnitureKind.SOFA, 5, 2)).world
        val sofa = room(placed).furniture.single { it.kind == FurnitureKind.SOFA }
        val sit = FurnitureActions.forKind(FurnitureKind.SOFA).first()
        val half = Needs.full().plus(mapOf(Need.SOCIAL to -50))
        val tired = placed.withPlayer(placed.player("jo").let { it.copy(character = it.character.copy(needs = half)) })
        val alone = assertNotNull(Interiors.enter(tired, "jo", cabin.id))
        val together = assertNotNull(Interiors.enter(alone, "maya", cabin.id))

        val socialAlone = assertNotNull(Interiors.use(alone, "jo", sofa.id, sit)).player("jo").character.needs[Need.SOCIAL]
        val socialTogether = assertNotNull(Interiors.use(together, "jo", sofa.id, sit)).player("jo").character.needs[Need.SOCIAL]
        assertEquals(Interiors.COMPANY_SOCIAL, socialTogether - socialAlone)
    }

    @Test
    fun `using furniture needs you to be in that room and awake`() {
        val bed = cabin.furniture.single()
        assertNull(Interiors.use(world, "jo", bed.id, Building.napInBed))
        val inside = assertNotNull(Interiors.enter(world, "jo", cabin.id))
        assertNotNull(Interiors.use(inside, "jo", bed.id, Building.napInBed))
        assertNull(Interiors.use(Worlds.sleep(inside, "jo", 0), "jo", bed.id, Building.napInBed))
    }
}
