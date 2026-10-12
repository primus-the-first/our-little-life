package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GardeningTest {

    private val yard = WorldMap.YARD

    /** Jo and Maya with a cabin and a garden beside it, and a chest full of everything. */
    private val world: World = run {
        val fresh = assertIs<JoinResult.Joined>(Worlds.join(Worlds.create("w", "KQ7XMB", Mode.PARTNERS, "jo", "Jo"), "maya", "Maya")).world
        val jo = Character("Jo")
        var camp = fresh.camp.copy(storage = Storage().plus(Resource.entries.associateWith { 100 }))
        camp = assertIs<BuildResult.Done>(camp.build(jo, RoomKind.CABIN, yard.x + 7, yard.y + 14)).camp
        camp = assertIs<BuildResult.Done>(camp.build(jo, RoomKind.GARDEN, yard.x + 12, yard.y + 19)).camp
        fresh.copy(camp = camp)
    }
    private val garden = world.camp.plot.rooms.single { it.kind == RoomKind.GARDEN }

    private fun beds(w: World) = w.camp.plot.rooms.single { it.kind == RoomKind.GARDEN }.beds

    /** Everyone sleeps, and the next morning comes. */
    private fun overnight(w: World) = Worlds.advanceIfReady(Worlds.sleep(Worlds.sleep(w, "jo", 0), "maya", 0))

    @Test
    fun `a garden has six empty beds, and other rooms have none`() {
        assertEquals(Gardening.BEDS, garden.beds.size)
        assertTrue(garden.beds.all { it.crop == null })
        assertTrue(world.camp.plot.rooms.single { it.kind == RoomKind.CABIN }.beds.isEmpty())
    }

    @Test
    fun `planting buys the seeds with coins`() {
        val planted = assertIs<GardenResult.Done>(Gardening.plant(world, "jo", garden.id, 0, Crop.CARROT)).world
        assertEquals(Crop.CARROT, beds(planted)[0].crop)
        assertEquals(100 - Crop.CARROT.seedCoins, planted.camp.storage[Resource.COINS])
        assertTrue(planted.player("jo").character.clock.minute > world.player("jo").character.clock.minute)
    }

    @Test
    fun `you can't plant in a bed that's already growing something, or without the coins`() {
        val planted = assertIs<GardenResult.Done>(Gardening.plant(world, "jo", garden.id, 0, Crop.CARROT)).world
        assertEquals(GardenResult.BedTaken, Gardening.plant(planted, "jo", garden.id, 0, Crop.TOMATO))
        val broke = world.copy(camp = world.camp.copy(storage = Storage()))
        assertIs<GardenResult.Missing>(Gardening.plant(broke, "jo", garden.id, 1, Crop.PUMPKIN))
    }

    @Test
    fun `watered beds grow a day overnight, dry ones don't`() {
        val planted = assertIs<GardenResult.Done>(Gardening.plant(world, "jo", garden.id, 0, Crop.CARROT)).world
        val dry = overnight(planted)
        assertEquals(0, beds(dry)[0].grown)

        val watered = assertNotNull(Gardening.water(planted, "maya", garden.id))
        assertTrue(beds(watered)[0].watered)
        val nextDay = overnight(watered)
        assertEquals(1, beds(nextDay)[0].grown)
        assertFalse(beds(nextDay)[0].watered)
    }

    @Test
    fun `watering needs something planted`() {
        assertNull(Gardening.water(world, "jo", garden.id))
    }

    @Test
    fun `a crop is ready after its days, and harvesting fills the chest and clears the bed`() {
        var w = assertIs<GardenResult.Done>(Gardening.plant(world, "jo", garden.id, 2, Crop.CARROT)).world
        repeat(Crop.CARROT.days) {
            assertNull(Gardening.harvest(w, "jo", garden.id, 2), "harvested too early")
            w = overnight(assertNotNull(Gardening.water(w, "jo", garden.id)))
        }
        assertTrue(Gardening.ready(beds(w)[2]))
        val before = w.camp.storage[Resource.CARROT]
        val picked = assertNotNull(Gardening.harvest(w, "jo", garden.id, 2))
        assertEquals(before + Crop.CARROT.yield, picked.camp.storage[Resource.CARROT])
        assertNull(beds(picked)[2].crop)
    }

    @Test
    fun `a ripe crop stops growing and waits for you`() {
        var w = assertIs<GardenResult.Done>(Gardening.plant(world, "jo", garden.id, 0, Crop.CARROT)).world
        repeat(Crop.CARROT.days + 2) { w = overnight(Gardening.water(w, "jo", garden.id) ?: w) }
        assertEquals(Crop.CARROT.days, beds(w)[0].grown)
    }

    @Test
    fun `vegetables cook into proper food`() {
        assertTrue(Cooking.roast in ObjectActions.forKind(ObjectKind.CAMPFIRE))
        assertTrue(Cooking.stew in FurnitureActions.forKind(FurnitureKind.STOVE))
        assertTrue(Cooking.pie in FurnitureActions.forKind(FurnitureKind.STOVE))
        assertTrue(Cooking.stew.costs.keys.containsAll(listOf(Resource.FISH, Resource.CARROT, Resource.TOMATO)))
        assertEquals(mapOf(Resource.PUMPKIN to 1), Cooking.pie.costs)
        val fire = world.camp.plot.objects.single { it.kind == ObjectKind.CAMPFIRE }
        val roasted = assertIs<UseResult.Done>(world.camp.use(world.player("jo").character, fire, Cooking.roast))
        assertEquals(100 - 1, roasted.camp.storage[Resource.CARROT])
    }

    @Test
    fun `older saves get beds in their garden`() {
        val stripped = Worlds.decode(Worlds.encode(world).replace(Regex(",\"beds\":\\[[^\\]]*]"), ""))
        assertEquals(Gardening.BEDS, beds(stripped).size)
    }
}
