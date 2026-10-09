package com.ourlittlelife.game.home

import com.ourlittlelife.rules.SaveCodec
import com.ourlittlelife.rules.SaveGame
import java.io.File

/**
 * The saved game as one small JSON file in the app's private storage.
 * Each save goes to a temporary file first and then replaces the old one,
 * so a crash halfway through a write never leaves a broken save behind.
 */
class SaveStore(directory: File) {
    private val file = File(directory, "save.json")
    private val temp = File(directory, "save.json.tmp")

    fun load(): SaveGame? = if (file.exists()) SaveCodec.decode(file.readText()) else null

    fun save(game: SaveGame) {
        temp.writeText(SaveCodec.encode(game))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }
}
