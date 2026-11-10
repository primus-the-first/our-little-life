package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NeighboursTest {

    private val together = Worlds.create("w", "KQ7XMB", Mode.BESTIES, "jo", "Jo")
    private val alone = Worlds.create("w", "KQ7XMB", Mode.BESTIES, "jo", "Jo", neighbours = true)
    private val street = assertIs<JoinResult.Joined>(Worlds.join(alone, "maya", "Maya")).world

    private val campKinds = listOf(ObjectKind.TENT, ObjectKind.CAMPFIRE, ObjectKind.WASHBASIN, ObjectKind.WORKBENCH, ObjectKind.CHEST)

    private fun World.give(id: String, vararg amounts: Pair<Resource, Int>) =
        copy(chests = chests + (id to (chests[id] ?: Storage()).plus(amounts.toMap())))

    private fun treeIn(world: World, lot: Area) = world.camp.plot.objects.first { it.kind == ObjectKind.TREE && lot.contains(it.x, it.y) }

    @Test
    fun `a neighbours world has two lots side by side, the host in the west one`() {
        assertEquals(WorldMap.WEST_LOT, alone.homes["jo"])
        assertTrue(WorldMap.YARD.contains(WorldMap.WEST_LOT) && WorldMap.YARD.contains(WorldMap.EAST_LOT))
        assertEquals(WorldMap.YARD.width, WorldMap.WEST_LOT.width + WorldMap.EAST_LOT.width)
    }

    @Test
    fun `each lot has its own camp, and nothing overlaps`() {
        val objects = alone.camp.plot.objects
        for (lot in listOf(WorldMap.WEST_LOT, WorldMap.EAST_LOT)) {
            for (kind in campKinds) {
                assertEquals(1, objects.count { it.kind == kind && it.tiles().all { (x, y) -> lot.contains(x, y) } }, "$kind in $lot")
            }
        }
        val tiles = objects.flatMap { it.tiles() }
        assertEquals(tiles.size, tiles.toSet().size)
        assertEquals(objects.size, objects.map { it.id }.toSet().size)
    }

    @Test
    fun `each lot has clear ground for a whole home`() {
        for (lot in listOf(WorldMap.WEST_LOT, WorldMap.EAST_LOT)) {
            val clear = Area(lot.x + 1, lot.y + 13, 10, 11)
            assertTrue(alone.camp.plot.objects.none { o -> o.tiles().any { (x, y) -> clear.contains(x, y) } }, "$lot")
        }
    }

    @Test
    fun `whoever joins gets the east lot and starts by their own fire`() {
        assertEquals(WorldMap.EAST_LOT, street.homes["maya"])
        val maya = street.player("maya")
        assertTrue(WorldMap.EAST_LOT.contains(maya.tileX, maya.tileY))
        assertTrue(WorldMap.WEST_LOT.contains(street.player("jo").tileX, street.player("jo").tileY))
        assertNull(street.camp.plot.objectAt(maya.tileX, maya.tileY))
    }

    @Test
    fun `living together is unchanged`() {
        assertTrue(together.homes.isEmpty())
        assertEquals(together, Neighbours.asPlayer(together, "jo"))
        assertEquals(1, together.camp.plot.objects.count { it.kind == ObjectKind.TENT })
    }

    @Test
    fun `each neighbour fills their own chest`() {
        val tree = treeIn(street, WorldMap.WEST_LOT)
        val after = assertNotNull(Neighbours.act(street, "jo") { Together.use(it, "jo", tree, Gathering.chop) })
        assertEquals(6, after.chests.getValue("jo")[Resource.WOOD])
        assertEquals(0, (after.chests["maya"] ?: Storage())[Resource.WOOD])
        assertEquals(6, Neighbours.asPlayer(after, "jo").camp.storage[Resource.WOOD])
        assertEquals(0, Neighbours.asPlayer(after, "maya").camp.storage[Resource.WOOD])
    }

    @Test
    fun `what's saved keeps no acting player's view`() {
        val tree = treeIn(street, WorldMap.WEST_LOT)
        val after = assertNotNull(Neighbours.act(street, "jo") { Together.use(it, "jo", tree, Gathering.chop) })
        assertNull(after.camp.plot.lot)
        assertEquals(Storage(), after.camp.storage)
    }

    @Test
    fun `covering for your neighbour mid-change leaves both chests right`() {
        val rich = street.give("jo", Resource.WOOD to 6).give("maya", Resource.STONE to 4)
        val after = assertNotNull(Neighbours.act(rich, "jo") { mine ->
            Neighbours.act(mine, "maya") { theirs -> theirs.copy(camp = theirs.camp.copy(storage = theirs.camp.storage.plus(mapOf(Resource.STONE to 1)))) }
        })
        assertEquals(6, after.chests.getValue("jo")[Resource.WOOD])
        assertEquals(0, after.chests.getValue("jo")[Resource.STONE])
        assertEquals(5, after.chests.getValue("maya")[Resource.STONE])
        assertNull(after.camp.plot.lot)
    }

    @Test
    fun `a neighbour's garden is theirs to plant and pick`() {
        val garden = Room("garden", RoomKind.GARDEN, WorldMap.EAST_LOT.x + 2, WorldMap.EAST_LOT.y + 14, beds = listOf(Bed(Crop.CARROT, grown = 2)))
        val grown = street.copy(camp = street.camp.copy(plot = street.camp.plot.copy(rooms = listOf(garden))))
        assertNull(Neighbours.act(grown, "jo") { Gardening.harvest(it, "jo", "garden", 0) })
        val picked = assertNotNull(Neighbours.act(grown, "maya") { Gardening.harvest(it, "maya", "garden", 0) })
        assertEquals(Crop.CARROT.yield, picked.chests.getValue("maya")[Resource.CARROT])
        val open = Neighbours.setHelpYourself(grown, "maya", true)
        assertNotNull(Neighbours.act(open, "jo") { Gardening.harvest(it, "jo", "garden", 0) })
    }

    @Test
    fun `something that can't happen changes nothing`() {
        assertNull(Neighbours.act(street, "jo") { null })
    }

    @Test
    fun `trees on your neighbour's lot are theirs`() {
        val theirs = treeIn(street, WorldMap.EAST_LOT)
        assertFalse(Neighbours.mayGather(street, "jo", theirs))
        assertNull(Neighbours.act(street, "jo") { Together.use(it, "jo", theirs, Gathering.chop) })
    }

    @Test
    fun `unless they say help yourself`() {
        val theirs = treeIn(street, WorldMap.EAST_LOT)
        val open = Neighbours.setHelpYourself(street, "maya", true)
        assertTrue(Neighbours.mayGather(open, "jo", theirs))
        val after = assertNotNull(Neighbours.act(open, "jo") { Together.use(it, "jo", theirs, Gathering.chop) })
        assertEquals(6, after.chests.getValue("jo")[Resource.WOOD])
    }

    @Test
    fun `the wild belongs to everyone, and so does a neighbour's fire`() {
        val wild = street.camp.plot.objects.first { it.kind == ObjectKind.TREE && !WorldMap.YARD.contains(it.x, it.y) }
        assertTrue(Neighbours.mayGather(street, "jo", wild))
        val theirFire = street.camp.plot.objects.first { it.kind == ObjectKind.CAMPFIRE && WorldMap.EAST_LOT.contains(it.x, it.y) }
        assertTrue(Neighbours.mayGather(street, "jo", theirFire))
    }

    @Test
    fun `you build on your own lot only`() {
        val rich = street.give("jo", Resource.PLANKS to 100).give("maya", Resource.PLANKS to 100)
        val jo = Neighbours.asPlayer(rich, "jo")
        val west = WorldMap.WEST_LOT
        val east = WorldMap.EAST_LOT
        val spot = (west.y..west.y + west.height - 5).firstNotNullOf { y ->
            (west.x..west.x + west.width - 6).firstOrNull { x -> Building.check(jo.camp.plot, RoomKind.CABIN, x, y) == Placement.Ok }?.let { it to y }
        }
        assertEquals(Placement.OffPlot, Building.check(jo.camp.plot, RoomKind.CABIN, spot.first + (east.x - west.x), spot.second))

        val built = assertNotNull(Neighbours.act(rich, "jo") { w ->
            (w.camp.build(w.player("jo").character, RoomKind.CABIN, spot.first, spot.second) as? BuildResult.Done)?.let { w.copy(camp = it.camp) }
        })
        assertEquals(60, built.chests.getValue("jo")[Resource.PLANKS])
        assertEquals(100, built.chests.getValue("maya")[Resource.PLANKS])
        assertTrue(Neighbours.owns(built, "jo", built.camp.plot.rooms.single()))
        assertFalse(Neighbours.owns(built, "maya", built.camp.plot.rooms.single()))
    }

    @Test
    fun `each neighbour builds their own cabin`() {
        val rich = street.give("jo", Resource.PLANKS to 100).give("maya", Resource.PLANKS to 100)
        fun cabin(world: World, id: String): World {
            val view = Neighbours.asPlayer(world, id)
            val lot = world.homes.getValue(id)
            val (x, y) = (lot.y..lot.y + lot.height - 5).firstNotNullOf { y ->
                (lot.x..lot.x + lot.width - 6).firstOrNull { x -> Building.check(view.camp.plot, RoomKind.CABIN, x, y) == Placement.Ok }?.let { it to y }
            }
            return assertNotNull(Neighbours.act(world, id) { w ->
                (w.camp.build(w.player(id).character, RoomKind.CABIN, x, y) as? BuildResult.Done)?.let { w.copy(camp = it.camp) }
            })
        }
        val both = cabin(cabin(rich, "jo"), "maya")
        val rooms = both.camp.plot.rooms
        assertEquals(2, rooms.size)
        assertNotEquals(rooms[0].id, rooms[1].id)
        // Jo already has a cabin, but Maya's lot is still waiting for hers before any other room.
        assertEquals(Placement.AlreadyBuilt, Building.check(Neighbours.asPlayer(both, "jo").camp.plot, RoomKind.CABIN, WorldMap.WEST_LOT.x, WorldMap.WEST_LOT.y))
    }

    @Test
    fun `a roof over your own head is what makes your night restful`() {
        val rich = street.give("jo", Resource.PLANKS to 100)
        val view = Neighbours.asPlayer(rich, "jo")
        val lot = WorldMap.WEST_LOT
        val (x, y) = (lot.y..lot.y + lot.height - 5).firstNotNullOf { y ->
            (lot.x..lot.x + lot.width - 6).firstOrNull { x -> Building.check(view.camp.plot, RoomKind.CABIN, x, y) == Placement.Ok }?.let { it to y }
        }
        var world = assertNotNull(Neighbours.act(rich, "jo") { w ->
            (w.camp.build(w.player("jo").character, RoomKind.CABIN, x, y) as? BuildResult.Done)?.let { w.copy(camp = it.camp) }
        })
        val tired = Needs.full().plus(mapOf(Need.ENERGY to -80))
        world = world.players.keys.fold(world) { w, id -> w.withPlayer(w.player(id).let { it.copy(character = it.character.copy(needs = tired)) }) }
        world = Worlds.advanceIfReady(Worlds.sleep(Worlds.sleep(world, "jo", 0), "maya", 0))
        assertEquals(100, world.player("jo").character.needs[Need.ENERGY])
        assertTrue(world.player("maya").character.needs[Need.ENERGY] < 100)
    }

    @Test
    fun `a neighbours world survives saving`() {
        val rich = street.give("jo", Resource.WOOD to 7)
        assertEquals(rich, Worlds.decode(Worlds.encode(rich)))
    }

    @Test
    fun `your own camp is the one on your lot`() {
        val mine = assertNotNull(Neighbours.mine(street, "maya", ObjectKind.CAMPFIRE))
        assertTrue(WorldMap.EAST_LOT.contains(mine.x, mine.y))
        assertNotNull(Neighbours.mine(together, "jo", ObjectKind.CAMPFIRE))
    }
}
