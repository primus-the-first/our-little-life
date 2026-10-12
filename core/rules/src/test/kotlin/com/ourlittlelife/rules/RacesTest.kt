package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RacesTest {

    private val solo = Worlds.create("w", "KQ7XMB", Mode.PARTNERS, "jo", "Jo")
    private val pair = assertIs<JoinResult.Joined>(Worlds.join(solo, "maya", "Maya")).world

    @Test
    fun `the racecourse sign offers a lap`() {
        assertEquals(listOf(Races.lap), ObjectActions.forKind(ObjectKind.RACE_SIGN))
    }

    @Test
    fun `a first lap is a personal best, pays the prize and is fun`() {
        val outcome = assertNotNull(Races.finish(pair, "jo", 24_000))
        assertTrue(outcome.personalBest)
        assertNull(outcome.previousBest)
        assertEquals(24_000L, outcome.world.player("jo").bestLapMillis)
        assertEquals(Races.BEST_PRIZE, outcome.world.camp.storage[Resource.COINS])
        assertEquals(Rules.DAY_START + Races.lap.minutes, outcome.world.player("jo").character.clock.minute)
    }

    @Test
    fun `a slower lap keeps your best and pays nothing`() {
        val first = assertNotNull(Races.finish(pair, "jo", 24_000)).world
        val slower = assertNotNull(Races.finish(first, "jo", 26_000))
        assertFalse(slower.personalBest)
        assertEquals(24_000L, slower.previousBest)
        assertEquals(24_000L, slower.world.player("jo").bestLapMillis)
        assertEquals(Races.BEST_PRIZE, slower.world.camp.storage[Resource.COINS])
    }

    @Test
    fun `beating your partner's best is the race, and you hear about it`() {
        val maya = assertNotNull(Races.finish(pair, "maya", 23_000)).world
        val lost = assertNotNull(Races.finish(maya, "jo", 25_000))
        assertEquals(23_000L, lost.partnerBest)
        assertFalse(lost.beatPartner)
        val won = assertNotNull(Races.finish(maya, "jo", 22_500))
        assertTrue(won.beatPartner)
    }

    @Test
    fun `impossible times and sleeping runners don't count`() {
        assertNull(Races.finish(pair, "jo", Races.FASTEST_LAP_MILLIS - 1))
        assertNull(Races.finish(Worlds.sleep(pair, "jo", 0), "jo", 24_000))
    }

    @Test
    fun `more energy means more stamina at the start line`() {
        val rested = Character("Jo")
        val tired = rested.copy(needs = rested.needs.plus(mapOf(Need.ENERGY to -80)))
        assertTrue(Races.stamina(rested) > Races.stamina(tired))
        assertTrue(Races.stamina(tired) > 0)
    }
}
