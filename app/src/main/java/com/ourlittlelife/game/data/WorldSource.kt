package com.ourlittlelife.game.data

import com.ourlittlelife.game.home.SaveStore
import com.ourlittlelife.rules.Mode
import com.ourlittlelife.rules.World
import com.ourlittlelife.rules.Worlds
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where a world lives: shared in Firestore, or solo in a file on this phone. The game treats both the same. */
interface WorldSource {
    val solo: Boolean

    /** The world as it changes. */
    fun observe(): Flow<World>

    /** Applies [change] to the latest world and saves it; [change] returns null for "can't happen". Returns the world as it now stands. */
    suspend fun update(change: (World) -> World?): World
}

class SharedWorldSource(private val store: WorldStore, private val worldId: String) : WorldSource {
    override val solo = false
    override fun observe(): Flow<World> = store.observe(worldId)
    override suspend fun update(change: (World) -> World?): World = store.update(worldId, change)
}

/**
 * The solo game: one player, saved as a file on this phone, playable offline.
 * The first time, an older solo save (from before shared worlds) is carried over.
 */
class SoloWorldSource(directory: File, private val playerName: String) : WorldSource {
    override val solo = true

    private val file = File(directory, "solo-world.json")
    private val temp = File(directory, "solo-world.json.tmp")
    private val lock = Mutex()
    private val state = MutableStateFlow(load())

    private fun load(): World {
        if (file.exists()) runCatching { return Worlds.decode(file.readText()) }
        val fresh = Worlds.create(SOLO_ID, "SOLO", Mode.BESTIES, PLAYER_ID, playerName)
        val old = SaveStore(file.parentFile!!).load() ?: return fresh
        val player = fresh.player(PLAYER_ID)
        return fresh.copy(camp = old.camp).withPlayer(
            player.copy(character = old.character.copy(name = playerName, asleep = false), tileX = old.tileX, tileY = old.tileY),
        )
    }

    override fun observe(): Flow<World> = state

    override suspend fun update(change: (World) -> World?): World = lock.withLock {
        val current = state.value
        val next = change(current) ?: return@withLock current
        temp.writeText(Worlds.encode(next))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        state.value = next
        next
    }

    companion object {
        const val SOLO_ID = "solo"
        const val PLAYER_ID = "solo-player"

        /** "Day 3 · 2 rooms" for the lobby, without loading the game. */
        fun summary(directory: File): String? {
            val world = File(directory, "solo-world.json").takeIf { it.exists() }
                ?.let { runCatching { Worlds.decode(it.readText()) }.getOrNull() }
                ?: SaveStore(directory).load()?.let { return "Day ${it.character.clock.day}" }
                ?: return null
            val rooms = world.camp.plot.rooms.size
            return "Day ${world.day}" + if (rooms > 0) " · $rooms room${if (rooms > 1) "s" else ""} built" else ""
        }
    }
}
