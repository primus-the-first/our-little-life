package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import com.ourlittlelife.rules.Need.FUN
import com.ourlittlelife.rules.Need.HUNGER
import kotlinx.serialization.Serializable

/** What grows in the garden: how many watered days it takes, what the seeds cost, and what it gives. */
@Serializable
enum class Crop(val label: String, val days: Int, val seedCoins: Int, val produce: Resource, val yield: Int) {
    CARROT("Carrots", days = 2, seedCoins = 5, produce = Resource.CARROT, yield = 3),
    TOMATO("Tomatoes", days = 3, seedCoins = 8, produce = Resource.TOMATO, yield = 3),
    PUMPKIN("Pumpkin", days = 4, seedCoins = 12, produce = Resource.PUMPKIN, yield = 1),
}

/** One bed in the garden: what's planted, how many watered days it has grown, and whether it's been watered today. */
@Serializable
data class Bed(val crop: Crop? = null, val grown: Int = 0, val watered: Boolean = false)

sealed interface GardenResult {
    data class Done(val world: World) : GardenResult
    data object BedTaken : GardenResult
    data class Missing(val shortfall: Map<Resource, Int>) : GardenResult
    data object Refused : GardenResult
}

/**
 * The garden: buy seeds with coins, plant them in beds, water once a day (one watering does every
 * bed, and either of you can do it), and harvest when they're ready. Beds only grow on days they were
 * watered, overnight. Numbers are starting values.
 */
object Gardening {

    const val BEDS = 6

    val planting = Action("plant", "Plant seeds", 10, mapOf(FUN to 2))
    val watering = Action("water", "Water the beds", 15, mapOf(FUN to 2, ENERGY to -2))
    val picking = Action("harvest", "Harvest", 10, mapOf(FUN to 4))

    fun starterBeds(kind: RoomKind): List<Bed> = if (kind == RoomKind.GARDEN) List(BEDS) { Bed() } else emptyList()

    fun ready(bed: Bed): Boolean = bed.crop != null && bed.grown >= bed.crop.days

    /** Plants [crop] in bed [index], paying for the seeds from the chest. */
    fun plant(world: World, playerId: String, roomId: String, index: Int, crop: Crop): GardenResult {
        val garden = world.camp.plot.rooms.firstOrNull { it.id == roomId } ?: return GardenResult.Refused
        val bed = garden.beds.getOrNull(index) ?: return GardenResult.Refused
        if (bed.crop != null) return GardenResult.BedTaken
        val seeds = mapOf(Resource.COINS to crop.seedCoins)
        val short = world.camp.storage.shortfall(seeds)
        if (short.isNotEmpty()) return GardenResult.Missing(short)
        val worked = work(world, playerId, planting) ?: return GardenResult.Refused
        val planted = withBeds(worked, garden.id) { beds -> beds.replace(index, Bed(crop)) }
        return GardenResult.Done(planted.copy(camp = planted.camp.copy(storage = planted.camp.storage.minus(seeds))))
    }

    /** Waters every bed with something still growing. Null if there's nothing to water, or you're asleep. */
    fun water(world: World, playerId: String, roomId: String): World? {
        val garden = world.camp.plot.rooms.firstOrNull { it.id == roomId } ?: return null
        if (garden.beds.none { it.crop != null && !ready(it) && !it.watered }) return null
        val worked = work(world, playerId, watering) ?: return null
        return withBeds(worked, garden.id) { beds -> beds.map { if (it.crop != null && !ready(it)) it.copy(watered = true) else it } }
    }

    /** Picks a ripe bed: the produce goes in the chest and the bed is ready for new seeds. */
    fun harvest(world: World, playerId: String, roomId: String, index: Int): World? {
        val garden = world.camp.plot.rooms.firstOrNull { it.id == roomId } ?: return null
        val bed = garden.beds.getOrNull(index)?.takeIf(::ready) ?: return null
        val crop = bed.crop ?: return null
        val worked = work(world, playerId, picking) ?: return null
        val picked = withBeds(worked, garden.id) { beds -> beds.replace(index, Bed()) }
        return picked.copy(camp = picked.camp.copy(storage = picked.camp.storage.plus(mapOf(crop.produce to crop.yield))))
    }

    /** Overnight: every watered bed grows a day (ripe ones wait), and every bed needs watering again. */
    fun grow(plot: Plot): Plot = plot.copy(
        rooms = plot.rooms.map { room ->
            if (room.beds.isEmpty()) room
            else room.copy(beds = room.beds.map { bed ->
                val crop = bed.crop
                val grown = if (crop != null && bed.watered && bed.grown < crop.days) bed.grown + 1 else bed.grown
                bed.copy(grown = grown, watered = false)
            })
        },
    )

    private fun work(world: World, playerId: String, action: Action): World? {
        val player = world.player(playerId)
        val done = Rules.perform(player.character, action) as? ActionResult.Done ?: return null
        return world.withPlayer(player.copy(character = done.character))
    }

    private fun withBeds(world: World, roomId: String, change: (List<Bed>) -> List<Bed>): World {
        val plot = world.camp.plot
        return world.copy(camp = world.camp.copy(plot = plot.copy(rooms = plot.rooms.map { if (it.id == roomId) it.copy(beds = change(it.beds)) else it })))
    }

    private fun List<Bed>.replace(index: Int, bed: Bed): List<Bed> = mapIndexed { i, old -> if (i == index) bed else old }
}

/** Recipes that use what you've grown and caught. */
object Cooking {
    val roast = Action("roast_veg", "Roast vegetables", 30, mapOf(HUNGER to 55, FUN to 5), costs = mapOf(Resource.CARROT to 1, Resource.TOMATO to 1))
    val stew = Action("fish_stew", "Make a fish stew", 60, mapOf(HUNGER to 90, FUN to 15), costs = mapOf(Resource.FISH to 1, Resource.CARROT to 1, Resource.TOMATO to 1))
    val pie = Action("pumpkin_pie", "Bake a pumpkin pie", 60, mapOf(HUNGER to 60, FUN to 20), costs = mapOf(Resource.PUMPKIN to 1))
}
