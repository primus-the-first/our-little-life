package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TogetherTest {

    private val solo = Worlds.create("w", "KQ7XMB", Mode.BESTIES, "jo", "Jo")
    private val pair = assertIs<JoinResult.Joined>(Worlds.join(solo, "maya", "Maya")).world

    /** Jo at (11,11); Maya placed wherever the test needs her. Everyone starts at 50 fun and 50 social. */
    private fun world(mayaAt: Pair<Int, Int>, mayaActivity: Activity? = null, mayaAsleep: Boolean = false): World {
        val half = Needs.full().plus(mapOf(Need.FUN to -50, Need.SOCIAL to -50))
        val jo = pair.player("jo").let { it.copy(character = it.character.copy(needs = half), tileX = 11, tileY = 11) }
        val maya = pair.player("maya").let {
            it.copy(
                character = it.character.copy(needs = half, asleep = mayaAsleep),
                tileX = mayaAt.first,
                tileY = mayaAt.second,
                activity = mayaActivity,
            )
        }
        return pair.withPlayer(jo).withPlayer(maya)
    }

    private fun joAfterStargazing(world: World): Character = Together.stargaze(world, "jo")!!.player("jo").character

    @Test
    fun `stargazing alone is an hour of fun`() {
        val jo = joAfterStargazing(world(mayaAt = 20 to 20))
        assertEquals(Rules.DAY_START + 60, jo.clock.minute)
        assertEquals(50 - 2 + 20, jo.needs[Need.FUN])
        assertEquals(50 - 1, jo.needs[Need.SOCIAL])
    }

    @Test
    fun `with your partner close by it's social too`() {
        val jo = joAfterStargazing(world(mayaAt = 13 to 11))
        assertEquals(50 - 1 + Together.NEARBY_SOCIAL, jo.needs[Need.SOCIAL])
    }

    @Test
    fun `stargazing together counts for even more`() {
        val jo = joAfterStargazing(world(mayaAt = 12 to 12, mayaActivity = Activity.STARGAZING))
        assertEquals(50 - 1 + Together.TOGETHER_SOCIAL, jo.needs[Need.SOCIAL])
    }

    @Test
    fun `a partner who is asleep or far away doesn't count`() {
        assertEquals(49, joAfterStargazing(world(mayaAt = 12 to 11, mayaAsleep = true)).needs[Need.SOCIAL])
        assertEquals(49, joAfterStargazing(world(mayaAt = 11, 15)).needs[Need.SOCIAL])
    }

    @Test
    fun `nearby means within 3 tiles, walking distance`() {
        assertEquals(true, Together.nearby(11 to 11, 14 to 11))
        assertEquals(true, Together.nearby(11 to 11, 12 to 13))
        assertEquals(false, Together.nearby(11 to 11, 13 to 13))
    }

    @Test
    fun `starting to stargaze is visible to your partner, and finishing clears it`() {
        val started = Together.startStargazing(world(mayaAt = 20 to 20), "jo")
        assertEquals(Activity.STARGAZING, started.player("jo").activity)
        assertNull(Together.stargaze(started, "jo")!!.player("jo").activity)
    }

    @Test
    fun `asleep means no stargazing`() {
        assertNull(Together.stargaze(Worlds.sleep(world(mayaAt = 20 to 20), "jo", 0), "jo"))
    }

    @Test
    fun `solo stargazing works with nobody else in the world`() {
        val jo = Together.stargaze(solo, "jo")!!.player("jo").character
        assertEquals(100, jo.needs[Need.FUN])
    }

    private fun world(mayaAt: Int, y: Int) = world(mayaAt = mayaAt to y)
}
