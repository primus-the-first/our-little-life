package com.ourlittlelife.rules

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RefiningTest {

    private val plot = StarterPlot.create()
    private val workbench = plot.objects.single { it.kind == ObjectKind.WORKBENCH }
    private val jo = Character("Jo")

    private fun campWith(vararg stock: Pair<Resource, Int>) = Camp(plot, Storage().plus(stock.toMap()))

    private fun Camp.done(action: Action, character: Character = jo): UseResult.Done =
        assertIs<UseResult.Done>(use(character, workbench, action))

    @Test
    fun `the workbench offers sawing, firing and weaving`() {
        assertEquals(listOf(Refining.sawPlanks, Refining.fireBricks, Refining.weaveCloth), ObjectActions.forKind(ObjectKind.WORKBENCH))
    }

    @Test
    fun `sawing turns 10 wood into 5 planks in an hour`() {
        val result = campWith(Resource.WOOD to 12).done(Refining.sawPlanks)
        assertEquals(2, result.camp.storage[Resource.WOOD])
        assertEquals(5, result.camp.storage[Resource.PLANKS])
        assertEquals(Rules.DAY_START + 60, result.character.clock.minute)
    }

    @Test
    fun `firing makes bricks from stone and weaving makes cloth from fiber`() {
        val camp = campWith(Resource.STONE to 8, Resource.FIBER to 10)
        val afterBricks = camp.done(Refining.fireBricks)
        assertEquals(0, afterBricks.camp.storage[Resource.STONE])
        assertEquals(4, afterBricks.camp.storage[Resource.BRICKS])

        val afterCloth = afterBricks.camp.done(Refining.weaveCloth, afterBricks.character)
        assertEquals(0, afterCloth.camp.storage[Resource.FIBER])
        assertEquals(4, afterCloth.camp.storage[Resource.CLOTH])
    }

    @Test
    fun `without enough materials nothing happens and it says what's missing`() {
        val camp = campWith(Resource.WOOD to 6)
        assertEquals(UseResult.Missing(mapOf(Resource.WOOD to 4)), camp.use(jo, workbench, Refining.sawPlanks))
    }

    @Test
    fun `refining has no daily limit, only materials`() {
        var result = campWith(Resource.WOOD to 30).done(Refining.sawPlanks)
        result = result.camp.done(Refining.sawPlanks, result.character)
        result = result.camp.done(Refining.sawPlanks, result.character)
        assertEquals(15, result.camp.storage[Resource.PLANKS])
        assertEquals(0, result.camp.storage[Resource.WOOD])
    }

    @Test
    fun `a refusal for time or sleep takes no materials`() {
        val camp = campWith(Resource.WOOD to 10)
        val late = jo.copy(clock = Clock(1, Rules.DAY_END - 30))
        assertEquals(UseResult.Refused(ActionResult.NotEnoughTime(30)), camp.use(late, workbench, Refining.sawPlanks))
    }

    @Test
    fun `the chest reports exactly what is short`() {
        val storage = Storage().plus(mapOf(Resource.PLANKS to 30, Resource.BRICKS to 25))
        assertEquals(mapOf(Resource.PLANKS to 10), storage.shortfall(mapOf(Resource.PLANKS to 40, Resource.BRICKS to 20)))
        assertEquals(emptyMap(), storage.shortfall(mapOf(Resource.BRICKS to 20)))
    }
}
