package com.ourlittlelife.rules

/**
 * Next-door neighbours: one world, two lots side by side, each with its own camp, chest and home.
 *
 * The rest of the rules know one camp with one chest, and they stay that way. A neighbour acts on the
 * world "as themselves" ([asPlayer]): their own chest stands in for the camp's, and their lot is where
 * they may build. [settle] puts the chest back afterwards, and [act] does both around a change. Worlds
 * where the two live together have no homes, and pass through all of this untouched.
 */
object Neighbours {

    private val gathered = setOf(ObjectKind.TREE, ObjectKind.ROCK, ObjectKind.BUSH)

    /** The world as [playerId] plays it: their chest, their lot. */
    fun asPlayer(world: World, playerId: String): World {
        val lot = world.homes[playerId] ?: return world
        return world.copy(camp = world.camp.copy(storage = world.chests[playerId] ?: Storage(), plot = world.camp.plot.copy(lot = lot)))
    }

    /** Back to how the world is saved: the chest returned to its owner, nobody's view left behind. */
    fun settle(world: World, playerId: String): World {
        if (playerId !in world.homes) return world
        return world.copy(
            chests = world.chests + (playerId to world.camp.storage),
            camp = world.camp.copy(storage = Storage(), plot = world.camp.plot.copy(lot = null)),
        )
    }

    /**
     * Runs [change] with [playerId] acting. Null, as from [change], means it can't happen. Whoever's view
     * [world] was to begin with (nobody's, as saved, or another player's mid-action) is kept, so one
     * player's change can cover for the other (autopilot) without their chests getting mixed up.
     */
    fun act(world: World, playerId: String, change: (World) -> World?): World? {
        if (playerId !in world.homes) return change(world)
        val done = settle(change(asPlayer(world, playerId)) ?: return null, playerId)
        return done.copy(camp = done.camp.copy(storage = world.camp.storage, plot = done.camp.plot.copy(lot = world.camp.plot.lot)))
    }

    /** Whose lot a tile is on, or null for shared ground (and everywhere, when living together). */
    fun ownerAt(world: World, x: Int, y: Int): String? = world.homes.entries.firstOrNull { it.value.contains(x, y) }?.key

    fun owns(world: World, playerId: String, room: Room): Boolean = ownerAt(world, room.x, room.y).let { it == null || it == playerId }

    /**
     * Whether [playerId] may use [obj]. Trees, rocks and bushes on a neighbour's lot are theirs, unless
     * they've said help yourself; everything else (their fire, their bench, the wild) is open to visitors.
     */
    fun mayGather(world: World, playerId: String, obj: PlotObject): Boolean {
        if (obj.kind !in gathered) return true
        val owner = ownerAt(world, obj.x, obj.y) ?: return true
        return owner == playerId || world.players[owner]?.helpYourself == true
    }

    /** Whether [playerId] may plant in and pick from [room]'s garden: their own, or a neighbour's who said help yourself. */
    fun mayTake(world: World, playerId: String, room: Room): Boolean {
        val owner = ownerAt(world, room.x, room.y) ?: return true
        return owner == playerId || world.players[owner]?.helpYourself == true
    }

    fun setHelpYourself(world: World, playerId: String, on: Boolean): World =
        world.withPlayer(world.player(playerId).copy(helpYourself = on))

    /** The camp thing of this [kind] that's [playerId]'s own: the one on their lot, or the only one there is. */
    fun mine(world: World, playerId: String, kind: ObjectKind): PlotObject? {
        val all = world.camp.plot.objects.filter { it.kind == kind }
        val lot = world.homes[playerId] ?: return all.firstOrNull()
        return all.firstOrNull { lot.contains(it.x, it.y) }
    }

    /** A cabin of their own (or, living together, the shared one): nights under it are restful. */
    fun hasRoof(world: World, playerId: String): Boolean {
        val lot = world.homes[playerId] ?: return world.camp.hasRoof
        return world.camp.plot.rooms.any { it.kind == RoomKind.CABIN && lot.contains(it.x, it.y) }
    }

    /** Where a neighbour first appears: beside their own campfire. */
    fun doorstep(world: World, playerId: String): Pair<Int, Int> {
        val fire = mine(world, playerId, ObjectKind.CAMPFIRE) ?: return WorldMap.YARD.x to WorldMap.YARD.y
        return fire.x - 1 to fire.y
    }
}
