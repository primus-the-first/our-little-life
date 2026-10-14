package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoingTest {

    private val solo = Worlds.create("w", "KQ7XMB", Mode.BESTIES, "jo", "Jo")
    private val reading = Doing(action = "read_book", target = "shelf-1", since = 1_000_000L, minutes = 45)

    @Test
    fun `an action shows for as long as it plays out, a game minute a second`() {
        assertTrue(reading.activeAt(1_000_000L))
        assertTrue(reading.activeAt(1_000_000L + 44_000))
        assertFalse(reading.activeAt(1_000_000L + 45_000 + Doing.GRACE_MILLIS + 1))
    }

    @Test
    fun `a clock running a little behind still shows it`() {
        assertTrue(reading.activeAt(1_000_000L - 2_000))
    }

    @Test
    fun `what someone is doing is saved with the world`() {
        val busy = solo.withPlayer(solo.player("jo").copy(doing = reading))
        assertEquals(reading, Worlds.decode(Worlds.encode(busy)).player("jo").doing)
    }

    @Test
    fun `worlds saved before this existed read as doing nothing`() {
        val old = Worlds.encode(solo).replace("\"doing\":null,", "")
        assertNull(Worlds.decode(old).player("jo").doing)
    }
}
