package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NudgesTest {

    private val solo = Worlds.create("w", "KQ7XMB", Mode.BESTIES, "jo", "Jo")
    private val pair = assertIs<JoinResult.Joined>(Worlds.join(solo, "maya", "Maya")).world

    private fun World.maya(change: (Player) -> Player) = withPlayer(change(player("maya")))
    private fun World.jo(change: (Player) -> Player) = withPlayer(change(player("jo")))
    private fun Player.asleep() = copy(character = character.copy(asleep = true))

    /** What Jo's phone would say after the world went from [before] to [after]. */
    private fun nudges(before: World, after: World) = Nudges.between(Glance.of(before, "jo"), Glance.of(after, "jo"))

    private fun kinds(before: World, after: World) = nudges(before, after).map { it.kind }

    @Test
    fun `nothing changed, nothing to say`() {
        assertEquals(emptyList(), nudges(pair, pair))
    }

    @Test
    fun `the first look at a world never nudges`() {
        assertEquals(emptyList(), Nudges.between(null, Glance.of(pair, "jo")))
    }

    @Test
    fun `your partner going to bed while you're up`() {
        val nudge = nudges(pair, pair.maya { it.asleep() }).single()
        assertEquals(NudgeKind.PARTNER_ASLEEP, nudge.kind)
        assertEquals("Maya went to bed", nudge.title)
        assertEquals("Tuesday starts when you sleep too.", nudge.text)
    }

    @Test
    fun `no bedtime nudge if you're asleep already`() {
        val joAsleep = pair.jo { it.asleep() }
        assertEquals(emptyList(), kinds(joAsleep, joAsleep.maya { it.asleep() }))
    }

    @Test
    fun `a new day`() {
        val tomorrow = pair
            .jo { it.copy(character = it.character.copy(clock = Clock.startOfDay(2))) }
            .maya { it.copy(character = it.character.copy(clock = Clock.startOfDay(2))) }
        val nudge = nudges(pair, tomorrow).single()
        assertEquals(NudgeKind.NEW_DAY, nudge.kind)
        assertEquals("Tuesday is here", nudge.title)
    }

    @Test
    fun `a new day replaces the bedtime nudge, which is old news by then`() {
        val tomorrow = pair.maya { it.asleep().copy(character = it.character.copy(clock = Clock.startOfDay(2))) }
        assertEquals(listOf(NudgeKind.NEW_DAY), kinds(pair, tomorrow))
    }

    @Test
    fun `a wave, a high five or a hug from your partner`() {
        val waved = assertIs<World>(Together.greet(pair.maya { it.copy(tileX = 12, tileY = 11) }.jo { it.copy(tileX = 11, tileY = 11) }, "maya", Gesture.HUG))
        val nudge = nudges(pair, waved).single()
        assertEquals(NudgeKind.GREETING, nudge.kind)
        assertEquals("Maya hugged you", nudge.title)
    }

    @Test
    fun `a second wave nudges again`() {
        val once = pair.jo { it.copy(greeting = Greeting("maya", Gesture.WAVE, 1)) }
        val twice = pair.jo { it.copy(greeting = Greeting("maya", Gesture.WAVE, 2)) }
        assertEquals(listOf(NudgeKind.GREETING), kinds(once, twice))
    }

    @Test
    fun `an invitation to stargaze or walk`() {
        assertEquals("Maya is stargazing", nudges(pair, pair.maya { it.copy(activity = Activity.STARGAZING) }).single().title)
        assertEquals("Maya would like a walk", nudges(pair, pair.maya { it.copy(activity = Activity.WALK_INVITE) }).single().title)
    }

    @Test
    fun `someone joining your world`() {
        val nudge = Nudges.between(Glance.of(solo, "jo"), Glance.of(pair, "jo")).single()
        assertEquals(NudgeKind.JOINED, nudge.kind)
        assertEquals("Maya joined your world", nudge.title)
    }

    @Test
    fun `crops that just became ready`() {
        val garden = Room("g", RoomKind.GARDEN, 0, 0, beds = listOf(Bed(Crop.CARROT, grown = 1), Bed(Crop.PUMPKIN, grown = 4)))
        fun withBeds(beds: List<Bed>) = pair.copy(camp = pair.camp.copy(plot = pair.camp.plot.copy(rooms = listOf(garden.copy(beds = beds)))))
        val before = withBeds(garden.beds)
        val after = withBeds(listOf(Bed(Crop.CARROT, grown = 2), Bed(Crop.PUMPKIN, grown = 4)))
        val nudge = nudges(before, after).single()
        assertEquals(NudgeKind.CROPS, nudge.kind)
        assertEquals("Your carrots are ready", nudge.title)
    }

    @Test
    fun `a glance survives being saved and read back`() {
        val glance = Glance.of(pair.maya { it.asleep() }, "jo")
        assertEquals(glance, Glance.decode(glance.encode()))
        assertTrue(Glance.decode("not json") == null)
    }
}
