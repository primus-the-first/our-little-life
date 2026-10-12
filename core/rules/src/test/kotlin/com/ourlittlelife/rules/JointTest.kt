package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JointTest {

    private val solo = Worlds.create("w", "KQ7XMB", Mode.PARTNERS, "jo", "Jo")
    private val pair = assertIs<JoinResult.Joined>(Worlds.join(solo, "maya", "Maya")).world

    /** Jo at [jo], Maya at [maya], both on half social and half fun so changes are easy to read. */
    private fun world(jo: Pair<Int, Int>, maya: Pair<Int, Int>, mayaAsleep: Boolean = false): World {
        val half = Needs.full().plus(mapOf(Need.SOCIAL to -50, Need.FUN to -50))
        fun place(p: Player, at: Pair<Int, Int>, asleep: Boolean = false) =
            p.copy(character = p.character.copy(needs = half, asleep = asleep), tileX = at.first, tileY = at.second)
        return pair.withPlayer(place(pair.player("jo"), jo)).withPlayer(place(pair.player("maya"), maya, mayaAsleep))
    }

    private fun social(w: World, id: String) = w.player(id).character.needs[Need.SOCIAL]

    @Test
    fun `a wave reaches across the yard, and cheers you both up`() {
        val waved = assertNotNull(Together.greet(world(31 to 31, 36 to 31), "jo", Gesture.WAVE))
        assertEquals(50 + Gesture.WAVE.social, social(waved, "jo"))
        assertEquals(50 + Gesture.WAVE.social, social(waved, "maya"))
    }

    @Test
    fun `hugs and high fives need you right next to each other`() {
        assertNull(Together.greet(world(31 to 31, 33 to 31), "jo", Gesture.HUG))
        val hugged = assertNotNull(Together.greet(world(31 to 31, 32 to 31), "jo", Gesture.HUG))
        assertEquals(50 + Gesture.HUG.social, social(hugged, "maya"))
        val fived = assertNotNull(Together.greet(world(31 to 31, 31 to 32), "jo", Gesture.HIGH_FIVE))
        assertEquals(50 + Gesture.HIGH_FIVE.joy, fived.player("maya").character.needs[Need.FUN])
    }

    @Test
    fun `the other person finds out who greeted them, each time`() {
        val once = assertNotNull(Together.greet(world(31 to 31, 32 to 31), "jo", Gesture.HUG))
        assertEquals(Greeting("jo", Gesture.HUG, 1), once.player("maya").greeting)
        val twice = assertNotNull(Together.greet(once, "jo", Gesture.WAVE))
        assertEquals(Greeting("jo", Gesture.WAVE, 2), twice.player("maya").greeting)
    }

    @Test
    fun `each gesture counts for social once a day, though you can still send it`() {
        val first = assertNotNull(Together.greet(world(31 to 31, 32 to 31), "jo", Gesture.HUG))
        val again = assertNotNull(Together.greet(first, "jo", Gesture.HUG))
        assertEquals(social(first, "maya"), social(again, "maya"))
        assertEquals(2, again.player("maya").greeting?.count)

        val asleep = Worlds.sleep(Worlds.sleep(again, "jo", 0), "maya", 0)
        val tomorrow = Worlds.advanceIfReady(asleep)
        assertTrue(tomorrow.player("jo").gesturesToday.isEmpty())
    }

    @Test
    fun `no greeting with nobody there, someone asleep, or one of you indoors`() {
        assertNull(Together.greet(solo, "jo", Gesture.WAVE))
        assertNull(Together.greet(world(31 to 31, 32 to 31, mayaAsleep = true), "jo", Gesture.WAVE))
        val indoors = world(31 to 31, 32 to 31).let { w -> w.withPlayer(w.player("maya").copy(inside = Inside("cabin", 3, 3))) }
        assertNull(Together.greet(indoors, "jo", Gesture.WAVE))
    }

    @Test
    fun `in the same room, closeness is measured on the room's floor`() {
        val w = world(31 to 31, 40 to 40)
        val bothIn = w.withPlayer(w.player("jo").copy(inside = Inside("cabin", 3, 3)))
            .let { it.withPlayer(it.player("maya").copy(inside = Inside("cabin", 4, 3))) }
        assertNotNull(Together.greet(bothIn, "jo", Gesture.HUG))
    }

    @Test
    fun `stories by the fire, the bench and a picnic are better with company`() {
        val camp = pair.camp.plot.objects.single { it.kind == ObjectKind.CAMPFIRE }
        val bench = pair.camp.plot.objects.single { it.kind == ObjectKind.BENCH }
        assertTrue(Together.stories in ObjectActions.forKind(ObjectKind.CAMPFIRE))
        assertTrue(Together.picnic in ObjectActions.forKind(ObjectKind.BENCH))

        val near = world(camp.x - 1 to camp.y, camp.x + 1 to camp.y)
        val together = assertNotNull(Together.use(near, "jo", camp, Together.stories))
        val far = world(camp.x - 1 to camp.y, 5 to 5)
        val alone = assertNotNull(Together.use(far, "jo", camp, Together.stories))
        assertEquals(Together.companyBonus.getValue(Together.stories), social(together, "jo") - social(alone, "jo"))

        val atBench = world(bench.x, bench.y + 1)
        assertNotNull(Together.use(atBench, "jo", bench, Together.picnic))
    }

    @Test
    fun `using things outdoors still uses up gathering spots and pays out as before`() {
        val tree = pair.camp.plot.objects.first { it.kind == ObjectKind.TREE }
        val chopped = assertNotNull(Together.use(world(tree.x + 1 to tree.y, 5 to 5), "jo", tree, Gathering.chop))
        assertEquals(6, chopped.camp.storage[Resource.WOOD])
        assertNull(Together.use(Worlds.sleep(pair, "jo", 0), "jo", tree, Gathering.chop))
    }

    private fun world(x: Int, y: Int) = world(x to y, 5 to 5)
}
