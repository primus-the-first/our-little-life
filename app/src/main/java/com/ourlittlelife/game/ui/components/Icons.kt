package com.ourlittlelife.game.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Carpenter
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Foundation
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.HolidayVillage
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phishing
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.ui.graphics.vector.ImageVector
import com.ourlittlelife.rules.Action
import com.ourlittlelife.rules.Gesture
import com.ourlittlelife.rules.Need
import com.ourlittlelife.rules.Resource

/**
 * The game's icons. Material Symbols Rounded stands in until the pixel set is drawn (see the design
 * system); everything goes through here, so swapping the set later touches one file.
 */
object OllIcons {
    val time = Icons.Rounded.Schedule
    val coins = Icons.Rounded.Paid
    val me = Icons.Rounded.Person
    val together = Icons.Rounded.Groups
    val build = Icons.Rounded.Construction
    val furnish = Icons.Rounded.Chair
    val bed = Icons.Rounded.Bedtime
    val settings = Icons.Rounded.Settings
    val outside = Icons.Rounded.MeetingRoom
    val inside = Icons.Rounded.Home
    val chest = Icons.Rounded.Inventory2
    val garden = Icons.Rounded.Yard
    val go = Icons.Rounded.ChevronRight
    val sleep = Icons.Rounded.Hotel
    val nextDoor = Icons.Rounded.HolidayVillage

    fun of(need: Need): ImageVector = when (need) {
        Need.ENERGY -> Icons.Rounded.Bolt
        Need.HUNGER -> Icons.Rounded.Restaurant
        Need.FUN -> Icons.Rounded.SentimentSatisfied
        Need.SOCIAL -> Icons.Rounded.Favorite
        Need.HYGIENE -> Icons.Rounded.WaterDrop
    }

    /** Null for the few things with no fitting icon yet (the vegetables): those show their name instead. */
    fun of(resource: Resource): ImageVector? = when (resource) {
        Resource.WOOD -> Icons.Rounded.Forest
        Resource.STONE -> Icons.Rounded.Terrain
        Resource.FIBER -> Icons.Rounded.Grass
        Resource.PLANKS -> Icons.Rounded.Carpenter
        Resource.BRICKS -> Icons.Rounded.Foundation
        Resource.CLOTH -> Icons.Rounded.Checkroom
        Resource.COINS -> Icons.Rounded.Paid
        Resource.FISH -> Icons.Rounded.SetMeal
        Resource.CARROT, Resource.TOMATO, Resource.PUMPKIN -> null
    }

    fun of(gesture: Gesture): ImageVector = when (gesture) {
        Gesture.WAVE -> Icons.Rounded.WavingHand
        Gesture.HIGH_FIVE -> Icons.Rounded.AutoAwesome
        Gesture.HUG -> Icons.Rounded.Favorite
    }

    /** What an action looks like: its own icon where it has one, else whatever it gives you most of. */
    fun of(action: Action): ImageVector = when {
        action.id == "chop_wood" -> Icons.Rounded.Forest
        action.id == "break_rock" -> Icons.Rounded.Terrain
        action.id == "pick_fiber" -> Icons.Rounded.Grass
        action.id.startsWith("nap") -> Icons.Rounded.Bedtime
        action.id.startsWith("cook") || action.id.startsWith("roast") || action.id.startsWith("grill") -> Icons.Rounded.LocalFireDepartment
        action.id.startsWith("snack") -> Icons.Rounded.Cookie
        action.id.startsWith("wash") -> Icons.Rounded.WaterDrop
        action.id.startsWith("work") -> Icons.Rounded.Work
        action.id.startsWith("stargaze") -> Icons.Rounded.AutoAwesome
        action.id == "sit_sofa" -> Icons.Rounded.Weekend
        action.id.startsWith("sit") -> Icons.Rounded.Chair
        action.id == "picnic" -> Icons.Rounded.LunchDining
        action.id == "fire_stories" -> Icons.Rounded.Forum
        action.id.startsWith("meal") -> Icons.Rounded.Restaurant
        action.id.startsWith("read") -> Icons.AutoMirrored.Rounded.MenuBook
        action.id.startsWith("fish") || action.id.startsWith("cast") -> Icons.Rounded.Phishing
        action.id.startsWith("lap") || action.id.startsWith("race") -> Icons.AutoMirrored.Rounded.DirectionsRun
        action.id.startsWith("walk") -> Icons.Rounded.Hiking
        action.id.startsWith("plant") || action.id.startsWith("water") || action.id.startsWith("harvest") -> Icons.Rounded.Eco
        action.id.startsWith("build") -> Icons.Rounded.Construction
        else -> action.yields.keys.firstNotNullOfOrNull(::of)
            ?: action.effects.filterValues { it > 0 }.maxByOrNull { it.value }?.key?.let(::of)
            ?: Icons.Rounded.ChevronRight
    }
}
