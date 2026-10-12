package com.ourlittlelife.rules

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorldTest {

    private val hour = 60 * 60 * 1000L
    private val solo = Worlds.create(id = "w1", code = "KQ7XMB", mode = Mode.BESTIES, hostId = "jo", hostName = "Jo")
    private val pair = assertIs<JoinResult.Joined>(Worlds.join(solo, "maya", "Maya")).world

    @Test
    fun `a new world has a starter plot and its host`() {
        assertEquals(listOf("jo"), solo.players.keys.toList())
        assertEquals("Jo", solo.players.getValue("jo").character.name)
        assertEquals(WorldMap.expand(StarterPlot.create()), solo.camp.plot)
        assertEquals(1, solo.day)
    }

    @Test
    fun `each player picks their own look, and it survives saving`() {
        val styled = Worlds.setLook(pair, "maya", Look(body = 0, skin = 1))
        assertEquals(Look(body = 0, skin = 1), styled.player("maya").look)
        assertEquals(null, styled.player("jo").look)
        assertEquals(styled, Worlds.decode(Worlds.encode(styled)))
    }

    @Test
    fun `a look has an outfit and one of four skin tones, and older looks keep working`() {
        val dressed = Worlds.setLook(pair, "jo", Look(body = 1, skin = 3, outfit = 2))
        assertEquals(Look(body = 1, skin = 3, outfit = 2), dressed.player("jo").look)
        assertEquals(4, Look.SKIN_TONES)
        assertEquals(3, Look.OUTFITS)
        val old = Worlds.decode(Worlds.encode(Worlds.setLook(pair, "jo", Look(body = 0, skin = 1))).replace(",\"outfit\":0", ""))
        assertEquals(Look(body = 0, skin = 1, outfit = 0), old.player("jo").look)
    }

    @Test
    fun `invite codes are 6 easy-to-read characters`() {
        val codes = (1..200).map { Worlds.newCode(Random(it)) }
        codes.forEach { code ->
            assertEquals(6, code.length)
            assertTrue(code.all { it in Worlds.CODE_ALPHABET }, code)
            assertFalse(code.any { it in "O0I1L" }, "ambiguous character in $code")
        }
        assertTrue(codes.toSet().size > 190)
    }

    @Test
    fun `the second player joins on the same day and next to the fire`() {
        val later = solo.copy(players = solo.players.mapValues { (_, p) -> p.copy(character = p.character.copy(clock = Clock.startOfDay(4))) })
        val joined = assertIs<JoinResult.Joined>(Worlds.join(later, "maya", "Maya")).world
        val maya = joined.players.getValue("maya")
        assertEquals(4, maya.character.clock.day)
        assertNotEquals(joined.players.getValue("jo").tile, maya.tile)
    }

    @Test
    fun `a world holds two players at most, and joining twice changes nothing`() {
        assertEquals(JoinResult.Full, Worlds.join(pair, "sam", "Sam"))
        assertEquals(JoinResult.AlreadyIn(pair), Worlds.join(pair, "maya", "Maya"))
    }

    @Test
    fun `the day only moves on when both players are asleep`() {
        val joAsleep = Worlds.sleep(pair, "jo", now = 0)
        assertEquals(1, Worlds.advanceIfReady(joAsleep).day)

        val bothAsleep = Worlds.sleep(joAsleep, "maya", now = hour)
        val next = Worlds.advanceIfReady(bothAsleep)
        assertEquals(2, next.day)
        next.players.values.forEach {
            assertFalse(it.character.asleep)
            assertEquals(null, it.sleptAt)
        }
    }

    @Test
    fun `before a partner joins, the host can play days alone`() {
        val next = Worlds.advanceIfReady(Worlds.sleep(solo, "jo", now = 0))
        assertEquals(2, next.day)
    }

    @Test
    fun `a new day regrows everything and a roof means a full night's rest`() {
        val tree = pair.camp.plot.objects.first { it.kind == ObjectKind.TREE }
        val chopped = assertIs<UseResult.Done>(pair.camp.use(pair.players.getValue("jo").character, tree, Gathering.chop)).camp
        val world = pair.copy(camp = chopped)
        val next = Worlds.advanceIfReady(Worlds.sleep(Worlds.sleep(world, "jo", 0), "maya", 0))
        assertEquals(4, next.camp.usesLeft(tree))
    }

    @Test
    fun `autopilot plays the day for someone who hasn't shown up in 24 hours`() {
        val joAsleep = Worlds.sleep(pair, "jo", now = 0)
        val tooEarly = Worlds.autopilot(joAsleep, now = 23 * hour)
        assertEquals(1, tooEarly.day)

        val covered = Worlds.autopilot(joAsleep, now = 25 * hour)
        assertEquals(2, covered.day)
    }

    @Test
    fun `autopilot keeps the absent player fed and clean`() {
        val hungry = pair.copy(players = pair.players.mapValues { (id, p) ->
            if (id == "maya") p.copy(character = p.character.copy(needs = Needs.of(Need.HUNGER to 20, Need.HYGIENE to 20, Need.ENERGY to 80, Need.FUN to 50, Need.SOCIAL to 50))) else p
        })
        val covered = Worlds.autopilot(Worlds.sleep(hungry, "jo", now = 0), now = 25 * hour)
        val maya = covered.players.getValue("maya").character
        assertTrue(maya.needs[Need.HUNGER] > 20, "hunger ${maya.needs[Need.HUNGER]}")
        assertTrue(maya.needs[Need.HYGIENE] > 20, "hygiene ${maya.needs[Need.HYGIENE]}")
    }

    @Test
    fun `autopilot does nothing when nobody is waiting`() {
        assertEquals(pair, Worlds.autopilot(pair, now = 100 * hour))
    }

    @Test
    fun `a world saves and loads as JSON`() {
        val json = Worlds.encode(pair)
        assertEquals(pair, Worlds.decode(json))
    }
}
