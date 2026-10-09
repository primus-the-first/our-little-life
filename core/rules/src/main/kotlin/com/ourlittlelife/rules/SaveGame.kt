package com.ourlittlelife.rules

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything needed to pick a game back up: the character, the camp, and where the character stands. */
@Serializable
data class SaveGame(
    val version: Int = SaveCodec.VERSION,
    val character: Character,
    val camp: Camp,
    val tileX: Int,
    val tileY: Int,
)

/** Saves are plain JSON with a version number, so older builds never misread a newer save. */
object SaveCodec {
    const val VERSION = 1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(game: SaveGame): String = json.encodeToString(SaveGame.serializer(), game)

    /** The saved game, or null when the text is damaged or from a newer version of the game. */
    fun decode(text: String): SaveGame? = try {
        json.decodeFromString(SaveGame.serializer(), text).takeIf { it.version <= VERSION }
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
