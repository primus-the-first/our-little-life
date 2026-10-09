package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import com.ourlittlelife.rules.Need.FUN
import com.ourlittlelife.rules.Need.HUNGER
import com.ourlittlelife.rules.Need.HYGIENE
import com.ourlittlelife.rules.Need.SOCIAL
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RulesTest {

    private val idleHour = Action("idle", "Do nothing", 60)

    private fun Character.doing(action: Action): Character =
        assertIs<ActionResult.Done>(Rules.perform(this, action)).character

    @Test
    fun `a new character starts day 1 at 7am with full needs and 16 hours`() {
        val jo = Character("Jo")
        assertEquals(1, jo.clock.day)
        assertEquals("Monday", jo.clock.weekday)
        assertEquals(16 * 60, jo.clock.minutesLeft)
        Need.entries.forEach { assertEquals(100, jo.needs[it]) }
    }

    @Test
    fun `an action spends its minutes`() {
        val jo = Character("Jo").doing(StarterActions.wash)
        assertEquals(Rules.DAY_START + 30, jo.clock.minute)
    }

    @Test
    fun `needs drop by their hourly rate`() {
        val jo = Character("Jo").doing(idleHour)
        assertEquals(96, jo.needs[ENERGY])
        assertEquals(94, jo.needs[HUNGER])
        assertEquals(97, jo.needs[FUN])
        assertEquals(98, jo.needs[SOCIAL])
        assertEquals(97, jo.needs[HYGIENE])
    }

    @Test
    fun `effects are added after the drop and capped at 100`() {
        val hungry = Character("Jo", needs = Needs.full().plus(mapOf(HUNGER to -50)))
        assertEquals(67, hungry.doing(StarterActions.snack).needs[HUNGER]) // 50 - 3 + 20

        val full = Character("Jo").doing(StarterActions.snack)
        assertEquals(100, full.needs[HUNGER])
    }

    @Test
    fun `needs never go below 0`() {
        val tired = Character("Jo", needs = Needs.of(ENERGY to 5))
        val afterWork = tired.doing(StarterActions.work)
        assertEquals(0, afterWork.needs[ENERGY])
    }

    @Test
    fun `an action that runs past 11pm is refused`() {
        val late = Character("Jo", clock = Clock(1, Rules.DAY_END - 20))
        val result = Rules.perform(late, StarterActions.wash)
        assertEquals(ActionResult.NotEnoughTime(20), result)
    }

    @Test
    fun `a sleeping character can't act`() {
        val asleep = Rules.sleep(Character("Jo"))
        assertEquals(ActionResult.Asleep, Rules.perform(asleep, StarterActions.snack))
    }

    @Test
    fun `the day advances only when both characters are asleep`() {
        val jo = Rules.sleep(Character("Jo"))
        val maya = Character("Maya")
        assertFalse(Rules.canAdvance(listOf(jo, maya)))
        assertTrue(Rules.canAdvance(listOf(jo, Rules.sleep(maya))))
        assertFalse(Rules.canAdvance(emptyList()))
    }

    @Test
    fun `waking up starts the next day at 7am with the overnight changes`() {
        val tired = Character("Jo", needs = Needs.full().plus(mapOf(ENERGY to -80, HUNGER to -50)), clock = Clock(1, 22 * 60))
        val (jo, maya) = Rules.advanceDay(listOf(Rules.sleep(tired), Rules.sleep(Character("Maya"))))

        assertEquals(Clock(2, Rules.DAY_START), jo.clock)
        assertFalse(jo.asleep)
        assertEquals(90, jo.needs[ENERGY]) // 20 + 70
        assertEquals(40, jo.needs[HUNGER]) // 50 - 10
        assertEquals(95, jo.needs[HYGIENE]) // 100 - 5
        assertEquals(2, maya.clock.day)
    }

    @Test
    fun `Saturday and Sunday are not workdays`() {
        assertTrue(Clock.startOfDay(5).isWorkday)
        assertFalse(Clock.startOfDay(6).isWorkday)
        assertEquals("Sunday", Clock.startOfDay(7).weekday)
        assertEquals("Monday", Clock.startOfDay(8).weekday)
    }

    @Test
    fun `mood is the average of the five needs`() {
        val needs = Needs.of(ENERGY to 100, HUNGER to 50, FUN to 50, SOCIAL to 50, HYGIENE to 0)
        assertEquals(50, needs.mood)
    }
}
