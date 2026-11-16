package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivateTest {

    private fun pair(mode: Mode): World {
        val solo = Worlds.create("w", "KQ7XMB", mode, "jo", "Jo")
        val both = assertIs<JoinResult.Joined>(Worlds.join(solo, "maya", "Maya")).world
        // Side by side by the fire.
        return both.withPlayer(both.player("jo").copy(tileX = 31, tileY = 31)).withPlayer(both.player("maya").copy(tileX = 32, tileY = 31))
    }

    private val partners = pair(Mode.PARTNERS)
    private val bothIn = Private.optIn(Private.optIn(partners, "jo", true)!!, "maya", true)!!

    private fun World.inRoom(kind: RoomKind, furniture: List<Furniture> = emptyList()): World {
        val room = Room("r", kind, WorldMap.YARD.x + 2, WorldMap.YARD.y + 15, furniture = furniture)
        val built = copy(camp = camp.copy(plot = camp.plot.copy(rooms = listOf(room))))
        return players.keys.fold(built) { w, id -> w.withPlayer(w.player(id).copy(inside = Inside("r", 3, 3))) }
    }

    @Test
    fun `it's only offered to partners`() {
        assertTrue(Private.offered(partners))
        assertFalse(Private.offered(pair(Mode.BESTIES)))
        assertNull(Private.optIn(pair(Mode.ROOMMATES), "jo", true))
    }

    @Test
    fun `it's on only when both of you have said yes`() {
        assertFalse(Private.on(partners))
        assertFalse(Private.on(Private.optIn(partners, "jo", true)!!))
        assertTrue(Private.on(bothIn))
    }

    @Test
    fun `nobody is in it before a second player has joined`() {
        val alone = Worlds.create("w", "KQ7XMB", Mode.PARTNERS, "jo", "Jo")
        assertFalse(Private.on(Private.optIn(alone, "jo", true)!!))
    }

    @Test
    fun `either of you can turn it off for both, and anything being asked is dropped`() {
        val asking = Private.ask(bothIn, "jo", Moment.CUDDLE)!!
        val off = Private.optIn(asking, "maya", false)!!
        assertFalse(Private.on(off))
        assertNull(off.player("jo").asking)
        assertNull(Private.ask(off, "jo", Moment.CUDDLE))
    }

    @Test
    fun `a moment is asked for, never just taken`() {
        val asked = Private.ask(bothIn, "jo", Moment.CUDDLE)!!
        assertEquals(Moment.CUDDLE, asked.player("jo").asking)
        // Nothing has happened yet: needs, clocks and the bond are untouched.
        assertEquals(bothIn.player("maya").character, asked.player("maya").character)
        assertEquals(0, asked.bond)
        assertEquals("jo" to Moment.CUDDLE, Private.waiting(asked, "maya")?.let { it.first.id to it.second })
        assertNull(Private.waiting(asked, "jo"))
    }

    @Test
    fun `saying yes shares it, time passes for both, you feel closer, and the bond grows`() {
        val half = Needs.full().plus(mapOf(Need.SOCIAL to -50, Need.FUN to -50))
        val start = bothIn.players.keys.fold(bothIn) { w, id -> w.withPlayer(w.player(id).let { it.copy(character = it.character.copy(needs = half)) }) }
        val done = assertNotNull(Private.accept(Private.ask(start, "jo", Moment.CUDDLE)!!, "maya"))
        for (id in listOf("jo", "maya")) {
            val c = done.player(id).character
            assertTrue(c.needs[Need.SOCIAL] > 50, id)
            assertEquals(Rules.DAY_START + Moment.CUDDLE.minutes, c.clock.minute, id)
        }
        assertEquals(Moment.CUDDLE.bond, done.bond)
        assertNull(done.player("jo").asking)
    }

    @Test
    fun `not now leaves everything as it was`() {
        val asked = Private.ask(bothIn, "jo", Moment.CUDDLE)!!
        assertEquals(bothIn, Private.decline(asked, "maya"))
        assertEquals(bothIn, Private.decline(asked, "jo"))
    }

    @Test
    fun `you can't say yes to your own asking, or to nothing`() {
        assertNull(Private.accept(Private.ask(bothIn, "jo", Moment.CUDDLE)!!, "jo"))
        assertNull(Private.accept(bothIn, "maya"))
    }

    @Test
    fun `you have to be together and awake`() {
        val apart = bothIn.withPlayer(bothIn.player("maya").copy(tileX = 40, tileY = 31))
        assertEquals(MomentProblem.APART, Private.problem(apart, "jo", Moment.CUDDLE))
        val asleep = Worlds.sleep(bothIn, "maya", 0)
        assertEquals(MomentProblem.ASLEEP, Private.problem(asleep, "jo", Moment.CUDDLE))
        // One indoors, one out, is apart too.
        val split = bothIn.withPlayer(bothIn.player("maya").copy(inside = Inside("r", 1, 1)))
        assertEquals(MomentProblem.APART, Private.problem(split, "jo", Moment.CUDDLE))
        assertNull(Private.problem(bothIn, "jo", Moment.CUDDLE))
    }

    @Test
    fun `if you drift apart before the answer, yes no longer works`() {
        val asked = Private.ask(bothIn, "jo", Moment.CUDDLE)!!
        val moved = asked.withPlayer(asked.player("jo").copy(tileX = 45, tileY = 40))
        assertNull(Private.accept(moved, "maya"))
    }

    @Test
    fun `closer moments wait for the bond, and for the right room`() {
        assertEquals(MomentProblem.BOND, Private.problem(bothIn, "jo", Moment.BED))
        val close = bothIn.copy(bond = 100)
        assertEquals(MomentProblem.PLACE, Private.problem(close, "jo", Moment.BED))
        assertEquals(MomentProblem.PLACE, Private.problem(close, "jo", Moment.SHOWER))
        val bedroom = close.inRoom(RoomKind.CABIN, listOf(Furniture("bed", FurnitureKind.BED, 0, 0)))
        assertNull(Private.problem(bedroom, "jo", Moment.BED))
        assertEquals(MomentProblem.PLACE, Private.problem(bedroom, "jo", Moment.SHOWER))
        assertNull(Private.problem(close.inRoom(RoomKind.BATHROOM), "jo", Moment.SHOWER))
    }

    @Test
    fun `the bond tops out at 100`() {
        val nearly = bothIn.copy(bond = 99)
        assertEquals(100, Private.accept(Private.ask(nearly, "jo", Moment.CUDDLE)!!, "maya")!!.bond)
    }

    @Test
    fun `notes are kept for the two of you, newest last, and only while it's on`() {
        assertNull(Private.leaveNote(partners, "jo", "hello", now = 1))
        val one = Private.leaveNote(bothIn, "jo", "  Thinking of you  ", now = 10)!!
        val two = Private.leaveNote(one, "maya", "Come home soon", now = 20)!!
        assertEquals(listOf("Thinking of you", "Come home soon"), two.notes.map { it.text })
        assertEquals(listOf("jo", "maya"), two.notes.map { it.from })
        assertNull(Private.leaveNote(bothIn, "jo", "   ", now = 1))
    }

    @Test
    fun `notes are trimmed to size, and old ones make room`() {
        val long = Private.leaveNote(bothIn, "jo", "x".repeat(2000), now = 1)!!
        assertEquals(Private.NOTE_LENGTH, long.notes.single().text.length)
        val many = (1..Private.NOTES_KEPT + 5).fold(bothIn) { w, i -> Private.leaveNote(w, "jo", "note $i", now = i.toLong())!! }
        assertEquals(Private.NOTES_KEPT, many.notes.size)
        assertEquals("note ${Private.NOTES_KEPT + 5}", many.notes.last().text)
    }

    @Test
    fun `deleting a note deletes it for both`() {
        val one = Private.leaveNote(bothIn, "jo", "oops", now = 10)!!
        assertEquals(emptyList(), Private.deleteNote(one, one.notes.single().id).notes)
    }

    @Test
    fun `resetting turns it off for both, so each has to say yes again`() {
        val off = Private.reset(Private.ask(bothIn, "jo", Moment.CUDDLE)!!)
        assertFalse(Private.on(off))
        assertFalse(off.player("maya").adult)
        assertNull(off.player("jo").asking)
        assertFalse(Private.on(Private.optIn(off, "jo", true)!!))
    }

    @Test
    fun `a world can become a partners world, and leaving partners switches the private side off`() {
        val besties = pair(Mode.BESTIES)
        assertTrue(Private.offered(Private.setMode(besties, Mode.PARTNERS)))
        val noted = Private.leaveNote(bothIn.copy(bond = 30), "jo", "keep me", now = 1)!!
        val left = Private.setMode(noted, Mode.ROOMMATES)
        assertFalse(Private.on(left))
        assertTrue(left.players.values.none { it.adult })
        // Kept, out of sight, for if you come back.
        assertEquals(30, left.bond)
        assertEquals(1, left.notes.size)
    }

    @Test
    fun `it all survives saving`() {
        val busy = Private.leaveNote(Private.ask(bothIn.copy(bond = 12), "jo", Moment.CUDDLE)!!, "maya", "x", now = 5)!!
        assertEquals(busy, Worlds.decode(Worlds.encode(busy)))
    }

    @Test
    fun `news about it gives nothing away`() {
        val before = Glance.of(bothIn, "maya")
        val asked = Nudges.between(before, Glance.of(Private.ask(bothIn, "jo", Moment.CUDDLE)!!, "maya")).single()
        val note = Nudges.between(before, Glance.of(Private.leaveNote(bothIn, "jo", "a very private thing", now = 1)!!, "maya")).single()
        for (nudge in listOf(asked, note)) {
            assertEquals(NudgeKind.PRIVATE, nudge.kind)
            assertEquals("Jo is thinking of you", nudge.title)
            assertFalse("private" in nudge.text.lowercase() || "cuddle" in nudge.text.lowercase())
        }
        // Your own note is no news to you.
        assertEquals(emptyList(), Nudges.between(Glance.of(bothIn, "jo"), Glance.of(Private.leaveNote(bothIn, "jo", "x", now = 1)!!, "jo")))
    }
}
