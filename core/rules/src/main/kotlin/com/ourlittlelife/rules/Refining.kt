package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import com.ourlittlelife.rules.Resource.BRICKS
import com.ourlittlelife.rules.Resource.CLOTH
import com.ourlittlelife.rules.Resource.FIBER
import com.ourlittlelife.rules.Resource.PLANKS
import com.ourlittlelife.rules.Resource.STONE
import com.ourlittlelife.rules.Resource.WOOD

/** Workbench recipes: raw materials in, building materials out. No daily limit, only materials. */
object Refining {
    val sawPlanks = Action("saw_planks", "Saw planks", 60, mapOf(ENERGY to -4), yields = mapOf(PLANKS to 5), costs = mapOf(WOOD to 10))
    val fireBricks = Action("fire_bricks", "Fire bricks", 60, mapOf(ENERGY to -4), yields = mapOf(BRICKS to 4), costs = mapOf(STONE to 8))
    val weaveCloth = Action("weave_cloth", "Weave cloth", 60, mapOf(ENERGY to -2), yields = mapOf(CLOTH to 4), costs = mapOf(FIBER to 10))
}
