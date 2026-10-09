package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SaveGameTest {

    /** A game in the middle of day 2: things gathered, a cabin built, a tree half chopped. */
    private fun gameInProgress(): SaveGame {
        var camp = Camp(StarterPlot.create(), Storage().plus(mapOf(Resource.PLANKS to 45, Resource.FIBER to 3)))
        var jo = Character("Jo")
        camp = assertIs<BuildResult.Done>(camp.build(jo, RoomKind.CABIN, 8, 3)).let { jo = it.character; it.camp }
        val tree = camp.plot.objects.first { it.kind == ObjectKind.TREE }
        camp = assertIs<UseResult.Done>(camp.use(jo, tree, Gathering.chop)).let { jo = it.character; it.camp }
        return SaveGame(character = jo, camp = camp, tileX = 8, tileY = 8)
    }

    @Test
    fun `a saved game loads back exactly`() {
        val game = gameInProgress()
        assertEquals(game, SaveCodec.decode(SaveCodec.encode(game)))
    }

    @Test
    fun `today's gathering counts survive a save`() {
        val game = gameInProgress()
        val tree = game.camp.plot.objects.first { it.kind == ObjectKind.TREE }
        val loaded = SaveCodec.decode(SaveCodec.encode(game))!!
        assertEquals(3, loaded.camp.usesLeft(tree))
    }

    @Test
    fun `a damaged save starts a fresh game instead of crashing`() {
        assertNull(SaveCodec.decode("{ this is not a save"))
        assertNull(SaveCodec.decode(""))
    }

    @Test
    fun `a save from a newer version of the game is not loaded`() {
        val future = SaveCodec.encode(gameInProgress()).replace("\"version\":1", "\"version\":99")
        assertNull(SaveCodec.decode(future))
    }

    @Test
    fun `the save is plain JSON with a version`() {
        val text = SaveCodec.encode(gameInProgress())
        assert(text.startsWith("{\"version\":1")) { text.take(40) }
    }
}
