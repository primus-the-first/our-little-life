package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GatheringTest {

    private val camp = Camp(StarterPlot.create())
    private val jo = Character("Jo")
    private val tree = camp.plot.objects.first { it.kind == ObjectKind.TREE }
    private val otherTree = camp.plot.objects.filter { it.kind == ObjectKind.TREE }[1]
    private val rock = camp.plot.objects.first { it.kind == ObjectKind.ROCK }
    private val bush = camp.plot.objects.first { it.kind == ObjectKind.BUSH }
    private val campfire = camp.plot.objects.single { it.kind == ObjectKind.CAMPFIRE }

    private fun Camp.done(character: Character, obj: PlotObject, action: Action): UseResult.Done =
        assertIs<UseResult.Done>(use(character, obj, action))

    @Test
    fun `chopping a tree puts wood in the chest and takes an hour`() {
        val result = camp.done(jo, tree, Gathering.chop)
        assertEquals(6, result.camp.storage[Resource.WOOD])
        assertEquals(Rules.DAY_START + 60, result.character.clock.minute)
    }

    @Test
    fun `rocks give stone and bushes give fiber`() {
        val afterRock = camp.done(jo, rock, Gathering.breakRock)
        assertEquals(4, afterRock.camp.storage[Resource.STONE])
        val afterBush = afterRock.camp.done(afterRock.character, bush, Gathering.pickFiber)
        assertEquals(5, afterBush.camp.storage[Resource.FIBER])
        assertEquals(4, afterBush.camp.storage[Resource.STONE])
    }

    @Test
    fun `gathering is hard work and costs extra energy`() {
        val idle = Rules.decay(60)[Need.ENERGY]!!
        val result = camp.done(jo, tree, Gathering.chop)
        assertTrue(result.character.needs[Need.ENERGY] < 100 + idle)
    }

    @Test
    fun `a tree can be chopped 4 times a day, then it is used up`() {
        var state = camp
        var character = jo
        repeat(4) {
            val result = state.done(character, tree, Gathering.chop)
            state = result.camp
            character = result.character
        }
        assertEquals(0, state.usesLeft(tree))
        assertEquals(24, state.storage[Resource.WOOD])
        assertEquals(UseResult.UsedUp, state.use(character, tree, Gathering.chop))
    }

    @Test
    fun `each tree keeps its own count`() {
        val afterOne = camp.done(jo, tree, Gathering.chop).camp
        assertEquals(3, afterOne.usesLeft(tree))
        assertEquals(4, afterOne.usesLeft(otherTree))
    }

    @Test
    fun `everything grows back overnight but the chest keeps what you gathered`() {
        val chopped = camp.done(jo, tree, Gathering.chop).camp
        val tomorrow = chopped.nextDay()
        assertEquals(4, tomorrow.usesLeft(tree))
        assertEquals(6, tomorrow.storage[Resource.WOOD])
    }

    @Test
    fun `a refused gather changes nothing`() {
        val asleep = Rules.sleep(jo)
        assertEquals(UseResult.Refused(ActionResult.Asleep), camp.use(asleep, tree, Gathering.chop))
    }

    @Test
    fun `camp actions don't touch the chest or count uses`() {
        val result = camp.done(jo, campfire, StarterActions.cook)
        assertEquals(camp.storage, result.camp.storage)
        assertEquals(Int.MAX_VALUE, result.camp.usesLeft(campfire))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an object only offers its own actions`() {
        camp.use(jo, campfire, Gathering.chop)
    }

    @Test
    fun `an empty chest has nothing in it`() {
        Resource.entries.forEach { assertEquals(0, camp.storage[it]) }
    }
}
