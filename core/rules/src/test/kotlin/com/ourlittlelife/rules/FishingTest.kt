package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FishingTest {

    private val solo = Worlds.create("w", "KQ7XMB", Mode.PARTNERS, "jo", "Jo")
    private val pair = assertIs<JoinResult.Joined>(Worlds.join(solo, "maya", "Maya")).world
    private val plot = pair.camp.plot

    @Test
    fun `there's a fishing spot by the pond that you can reach`() {
        val spot = plot.objects.single { it.kind == ObjectKind.FISHING_SPOT }
        val nextToWater = spot.tiles().any { (x, y) ->
            listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).any { (nx, ny) -> plot.terrainAt(nx, ny) == Terrain.WATER }
        }
        assertTrue(nextToWater, "the fishing spot isn't on the shore")
        assertNotNull(Walking.spotBeside(plot, spot, from = pair.player("jo").tile))
        assertEquals(listOf(Fishing.cast), ObjectActions.forKind(ObjectKind.FISHING_SPOT))
    }

    @Test
    fun `a sloppy catch is usually a minnow, a perfect one can be a golden carp`() {
        assertEquals(FishKind.MINNOW, Fishing.pick(roll = 0, quality = 0f))
        assertEquals(FishKind.GOLDEN_CARP, Fishing.pick(roll = 99, quality = 1f))
        // Better reeling shifts the odds towards rarer fish.
        val sloppy = (0..99).count { Fishing.pick(it, 0.1f) != FishKind.MINNOW }
        val clean = (0..99).count { Fishing.pick(it, 0.9f) != FishKind.MINNOW }
        assertTrue(clean > sloppy)
    }

    @Test
    fun `landing a fish puts it in the chest, logs it, and takes a little time`() {
        val caught = assertNotNull(Fishing.land(pair, "jo", FishKind.TROUT))
        assertEquals(1, caught.camp.storage[Resource.FISH])
        assertEquals(1, caught.player("jo").fishLog[FishKind.TROUT])
        assertEquals(Rules.DAY_START + Fishing.cast.minutes, caught.player("jo").character.clock.minute)
    }

    @Test
    fun `a golden carp is worth coins too`() {
        val caught = assertNotNull(Fishing.land(pair, "jo", FishKind.GOLDEN_CARP))
        assertEquals(FishKind.GOLDEN_CARP.coins, caught.camp.storage[Resource.COINS])
    }

    @Test
    fun `one that gets away still passes the time, but nothing is caught`() {
        val missed = assertNotNull(Fishing.miss(pair, "jo"))
        assertEquals(0, missed.camp.storage[Resource.FISH])
        assertEquals(Rules.DAY_START + Fishing.cast.minutes, missed.player("jo").character.clock.minute)
        assertTrue(missed.player("jo").fishLog.isEmpty())
    }

    @Test
    fun `nobody fishes in their sleep`() {
        assertNull(Fishing.land(Worlds.sleep(pair, "jo", 0), "jo", FishKind.MINNOW))
        assertNull(Fishing.miss(Worlds.sleep(pair, "jo", 0), "jo"))
    }

    @Test
    fun `fishing side by side is a little social`() {
        val spot = plot.objects.single { it.kind == ObjectKind.FISHING_SPOT }
        fun placed(mayaAt: Pair<Int, Int>) = pair
            .withPlayer(pair.player("jo").copy(tileX = spot.x + 1, tileY = spot.y))
            .withPlayer(pair.player("maya").copy(tileX = mayaAt.first, tileY = mayaAt.second))
            .let { w -> w.players.values.fold(w) { acc, p -> acc.withPlayer(p.copy(character = p.character.copy(needs = p.character.needs.plus(mapOf(Need.SOCIAL to -50))))) } }
        val together = assertNotNull(Fishing.land(placed(spot.x + 2 to spot.y), "jo", FishKind.MINNOW)).player("jo").character.needs[Need.SOCIAL]
        val alone = assertNotNull(Fishing.land(placed(40 to 40), "jo", FishKind.MINNOW)).player("jo").character.needs[Need.SOCIAL]
        assertEquals(Fishing.COMPANY_SOCIAL, together - alone)
    }

    @Test
    fun `fish can be grilled at the campfire, and cooking uses them up`() {
        assertTrue(Fishing.grill in ObjectActions.forKind(ObjectKind.CAMPFIRE))
        val fire = plot.objects.single { it.kind == ObjectKind.CAMPFIRE }
        val jo = pair.player("jo").character
        assertIs<UseResult.Missing>(pair.camp.use(jo, fire, Fishing.grill))
        val stocked = pair.camp.copy(storage = pair.camp.storage.plus(mapOf(Resource.FISH to 2)))
        val grilled = assertIs<UseResult.Done>(stocked.use(jo, fire, Fishing.grill))
        assertEquals(1, grilled.camp.storage[Resource.FISH])
    }

    @Test
    fun `the stove cooks a fish supper, paid for from the chest`() {
        assertTrue(Fishing.supper in FurnitureActions.forKind(FurnitureKind.STOVE))
        val yard = WorldMap.YARD
        val stocked = pair.camp.copy(storage = Storage().plus(Resource.entries.associateWith { 500 }))
        val built = assertIs<BuildResult.Done>(stocked.build(Character("Jo"), RoomKind.CABIN, yard.x + 7, yard.y + 14)).camp
        val world = pair.copy(camp = built)
        val cabin = world.camp.plot.rooms.single()
        val withStove = assertIs<PlaceResult.Done>(Interiors.place(world, cabin.id, FurnitureKind.STOVE, 5, 2)).world
        val stove = withStove.camp.plot.rooms.single().furniture.single { it.kind == FurnitureKind.STOVE }
        val inside = assertNotNull(Interiors.enter(withStove, "jo", cabin.id))
        val ate = assertNotNull(Interiors.use(inside, "jo", stove.id, Fishing.supper))
        assertEquals(500 - Fishing.supper.costs.getValue(Resource.FISH), ate.camp.storage[Resource.FISH])

        val noFish = inside.copy(camp = inside.camp.copy(storage = inside.camp.storage.minus(mapOf(Resource.FISH to 500))))
        assertNull(Interiors.use(noFish, "jo", stove.id, Fishing.supper))
    }
}
