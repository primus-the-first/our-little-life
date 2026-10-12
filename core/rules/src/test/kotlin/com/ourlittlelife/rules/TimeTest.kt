package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Time passing the way it does in The Sims: the clock runs while you play, and needs drop with it. */
class TimeTest {

    private val solo = Worlds.create("w", "KQ7XMB", Mode.PARTNERS, "jo", "Jo")
    private val pair = assertIs<JoinResult.Joined>(Worlds.join(solo, "maya", "Maya")).world

    @Test
    fun `passing time moves the clock and wears needs down`() {
        val jo = Character("Jo")
        val later = Rules.passTime(jo, 60)
        assertEquals(Rules.DAY_START + 60, later.clock.minute)
        assertEquals(100 - 6, later.needs[Need.HUNGER])
        assertEquals(100 - 4, later.needs[Need.ENERGY])
    }

    @Test
    fun `the clock carries on past midnight, still on the same day`() {
        val late = Character("Jo", clock = Clock(3, 23 * 60 + 30))
        val after = Rules.passTime(late, 60)
        assertEquals(3, after.clock.day)
        assertEquals(24 * 60 + 30, after.clock.minute)
        val acted = assertIs<ActionResult.Done>(Rules.perform(after, StarterActions.snack)).character
        assertEquals(24 * 60 + 30 + StarterActions.snack.minutes, acted.clock.minute)
    }

    @Test
    fun `everyday things take believable amounts of time`() {
        assertTrue(StarterActions.snack.minutes <= 15)
        assertTrue(StarterActions.wash.minutes <= 20)
        assertTrue(StarterActions.cook.minutes in 30..60)
        assertTrue(Races.lap.minutes <= 10)
        assertEquals(8 * 60, StarterActions.work.minutes)
    }

    @Test
    fun `run out of energy and you fall asleep where you stand`() {
        val drained = pair.withPlayer(pair.player("jo").let { it.copy(character = it.character.copy(needs = it.character.needs.plus(mapOf(Need.ENERGY to -98)))) })
        val after = Worlds.passTime(drained, "jo", 60, now = 1_000)
        assertTrue(after.player("jo").character.asleep)
        assertEquals(1_000L, after.player("jo").sleptAt)
        assertFalse(after.player("maya").character.asleep)
    }

    @Test
    fun `falling asleep last starts the next morning for everyone`() {
        val mayaAsleep = Worlds.sleep(pair, "maya", 0)
        val drained = mayaAsleep.withPlayer(mayaAsleep.player("jo").let { it.copy(character = it.character.copy(needs = it.character.needs.plus(mapOf(Need.ENERGY to -98)))) })
        val morning = Worlds.passTime(drained, "jo", 60, now = 1_000)
        assertEquals(2, morning.day)
        assertTrue(morning.players.values.none { it.character.asleep })
    }

    @Test
    fun `time doesn't pass for someone asleep`() {
        val asleep = Worlds.sleep(pair, "jo", 0)
        assertEquals(asleep, Worlds.passTime(asleep, "jo", 30, now = 5))
    }
}
