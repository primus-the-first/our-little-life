package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalksTest {

    private val solo = Worlds.create("w", "KQ7XMB", Mode.BESTIES, "jo", "Jo")
    private val pair = assertIs<JoinResult.Joined>(Worlds.join(solo, "maya", "Maya")).world
    private val plot = pair.camp.plot

    /** Everyone at 50 fun and 50 social, so changes are easy to read. */
    private val half = Needs.full().plus(mapOf(Need.FUN to -50, Need.SOCIAL to -50))
    private val world = pair.players.values.fold(pair) { w, p -> w.withPlayer(p.copy(character = p.character.copy(needs = half))) }

    @Test
    fun `the trail is a loop of path tiles, each one step from the next`() {
        val trail = Walks.trail
        assertTrue(trail.size >= 40, "only ${trail.size} steps")
        trail.forEach { (x, y) -> assertEquals(Terrain.PATH, plot.terrainAt(x, y), "($x, $y) is off the path") }
        (trail + trail.first()).zipWithNext().forEach { (a, b) ->
            assertEquals(1, Math.abs(a.first - b.first) + Math.abs(a.second - b.second), "$a to $b isn't one step")
        }
        assertEquals(trail.size, trail.toSet().size, "the loop crosses itself")
    }

    @Test
    fun `the trailhead sign stands by the trail, offers a walk, and can be reached`() {
        val sign = plot.objects.single { it.kind == ObjectKind.TRAILHEAD }
        assertEquals(listOf(Walks.alone), ObjectActions.forKind(ObjectKind.TRAILHEAD))
        assertNotNull(Walking.spotBeside(plot, sign, from = pair.player("jo").tile))
        assertEquals(Terrain.PATH, plot.terrainAt(Walks.END.first, Walks.END.second))
        assertNotNull(Walking.path(plot, Walks.END, Walks.trail.first()))
    }

    @Test
    fun `asking for a walk shows on your character`() {
        val asked = assertNotNull(Walks.invite(world, "jo"))
        assertEquals(Activity.WALK_INVITE, asked.player("jo").activity)
        assertEquals(Activity.WALK_INVITE, Walks.waitingInvite(asked, forPlayer = "maya")?.activity)
        assertNull(Walks.waitingInvite(asked, forPlayer = "jo"))
    }

    @Test
    fun `you can't ask with nobody to ask, or when either of you is asleep`() {
        assertNull(Walks.invite(solo, "jo"))
        assertNull(Walks.invite(Worlds.sleep(world, "maya", 0), "jo"))
        assertNull(Walks.invite(Worlds.sleep(world, "jo", 0), "jo"))
    }

    @Test
    fun `saying yes walks you both for an hour, with fun and social for each`() {
        val walked = assertNotNull(Walks.accept(assertNotNull(Walks.invite(world, "jo")), "maya"))
        listOf("jo", "maya").forEach { id ->
            val p = walked.player(id)
            assertEquals(Rules.DAY_START + 60, p.character.clock.minute, id)
            assertEquals(50 - 3 + 12, p.character.needs[Need.FUN], id)
            assertEquals(50 - 2 + Walks.TOGETHER_SOCIAL, p.character.needs[Need.SOCIAL], id)
            assertNull(p.activity, id)
        }
        assertEquals(Walks.END, walked.player("maya").tile)
        assertEquals(Walks.BEHIND_END, walked.player("jo").tile)
    }

    @Test
    fun `saying yes needs an open invite, and late at night is fine`() {
        assertNull(Walks.accept(world, "maya"))
        val asked = assertNotNull(Walks.invite(world, "jo"))
        val late = asked.withPlayer(asked.player("jo").let { it.copy(character = it.character.copy(clock = Clock(1, Rules.DAY_END - 30))) })
        assertEquals(Rules.DAY_END, assertNotNull(Walks.accept(late, "maya")).player("jo").character.clock.minute)
    }

    @Test
    fun `a walk takes you both out of the house`() {
        val indoors = world.players.values.fold(world) { w, p -> w.withPlayer(p.copy(inside = Inside("cabin", 3, 3))) }
        val walked = assertNotNull(Walks.accept(assertNotNull(Walks.invite(indoors, "jo")), "maya"))
        assertTrue(walked.players.values.all { it.inside == null })
    }

    @Test
    fun `not now, or cancelling, clears the invite`() {
        val asked = assertNotNull(Walks.invite(world, "jo"))
        assertNull(Walks.decline(asked, "maya").player("jo").activity)
        assertNull(Walks.decline(asked, "jo").player("jo").activity)
    }

    @Test
    fun `walking alone is fun but not social`() {
        val jo = world.player("jo").character
        val done = assertIs<ActionResult.Done>(Rules.perform(jo, Walks.alone)).character
        assertEquals(50 - 3 + 12, done.needs[Need.FUN])
        assertEquals(50 - 2, done.needs[Need.SOCIAL])
    }
}
