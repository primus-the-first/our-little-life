package com.ourlittlelife.game.session

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.ourlittlelife.game.data.Auth
import com.ourlittlelife.game.data.WorldStore
import com.ourlittlelife.game.home.SaveStore
import com.ourlittlelife.rules.Mode
import com.ourlittlelife.rules.Resource
import kotlinx.coroutines.launch

/** Where the player is in getting to a world. */
sealed interface Session {
    data object Loading : Session
    data class SignedOut(val error: String? = null, val busy: Boolean = false) : Session

    /** The lobby: your worlds, plus starting a new one or joining a friend's. */
    data class Lobby(
        val uid: String,
        val name: String,
        val worlds: List<WorldStore.Summary>,
        val soloSummary: String?,
        val error: String? = null,
        val busy: Boolean = false,
    ) : Session

    data class InWorld(val worldId: String, val uid: String) : Session
}

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = Auth()
    private val store = WorldStore()
    private val soloSave = SaveStore(application.filesDir)
    private val prefs = application.getSharedPreferences("session", Context.MODE_PRIVATE)

    /** The world to reopen on launch. */
    private var lastWorldId: String?
        get() = prefs.getString("lastWorldId", null)
        set(value) = prefs.edit().putString("lastWorldId", value).apply()

    var state by mutableStateOf<Session>(Session.Loading)
        private set

    init {
        viewModelScope.launch {
            auth.users().collect { user ->
                state = when {
                    user == null -> Session.SignedOut()
                    else -> reopenOrLobby(user)
                }
            }
        }
    }

    /** Straight back into the last world you played, if you still belong to it; otherwise the lobby. */
    private suspend fun reopenOrLobby(user: FirebaseUser): Session {
        val lobby = lobbyFor(user)
        val last = lastWorldId
        return when {
            lobby !is Session.Lobby -> lobby
            last != null && lobby.worlds.any { it.id == last } -> Session.InWorld(last, user.uid)
            lobby.worlds.size == 1 && last == null -> enter(lobby.worlds.first().id, user.uid)
            else -> lobby
        }
    }

    private suspend fun lobbyFor(user: FirebaseUser, error: String? = null): Session = try {
        val worlds = store.worldsFor(user.uid)
        Session.Lobby(user.uid, Auth.firstName(user), worlds, soloSummary().takeIf { worlds.isEmpty() }, error)
    } catch (e: Exception) {
        Session.Lobby(user.uid, Auth.firstName(user), emptyList(), null, "Couldn't reach the game. Check your connection and try again.")
    }

    /** "Day 3 · 18 wood, 8 stone": a solo game that comes along into your first world. */
    private fun soloSummary(): String? {
        val solo = soloSave.load() ?: return null
        val stock = Resource.entries.filter { solo.camp.storage[it] > 0 }
            .joinToString(", ") { "${solo.camp.storage[it]} ${it.label.lowercase()}" }
        val rooms = if (solo.camp.plot.rooms.isNotEmpty()) " · ${solo.camp.plot.rooms.size} room built" else ""
        return "Day ${solo.character.clock.day}" + (if (stock.isNotEmpty()) " · $stock" else "") + rooms
    }

    private fun enter(worldId: String, uid: String): Session {
        lastWorldId = worldId
        return Session.InWorld(worldId, uid)
    }

    fun signIn(activity: Activity) {
        state = Session.SignedOut(busy = true)
        viewModelScope.launch {
            when (val outcome = auth.signInWithGoogle(activity)) {
                Auth.Outcome.SignedIn -> Unit // The sign-in listener moves things on.
                Auth.Outcome.Cancelled -> state = Session.SignedOut()
                is Auth.Outcome.Failed -> state = Session.SignedOut(error = outcome.reason)
            }
        }
    }

    fun openWorld(worldId: String) {
        val lobby = state as? Session.Lobby ?: return
        state = enter(worldId, lobby.uid)
    }

    /** Leaves the world you're in for the lobby. You stay a member and can come back any time. */
    fun backToLobby() {
        val user = auth.currentUser ?: return
        lastWorldId = null
        state = Session.Loading
        viewModelScope.launch { state = lobbyFor(user) }
    }

    fun create(mode: Mode) {
        val lobby = state as? Session.Lobby ?: return
        state = lobby.copy(busy = true, error = null)
        viewModelScope.launch {
            state = try {
                // A solo game only comes along into your first world.
                val solo = if (lobby.worlds.isEmpty()) soloSave.load() else null
                enter(store.create(lobby.uid, lobby.name, mode, solo), lobby.uid)
            } catch (e: Exception) {
                lobby.copy(busy = false, error = "Couldn't create the world. Check your connection and try again.")
            }
        }
    }

    fun join(code: String) {
        val lobby = state as? Session.Lobby ?: return
        val clean = code.trim().uppercase()
        if (clean.length != 6) {
            state = lobby.copy(error = "Codes are 6 letters and numbers, like KQ7XMB.")
            return
        }
        lobby.worlds.firstOrNull { it.code == clean }?.let {
            state = enter(it.id, lobby.uid)
            return
        }
        state = lobby.copy(busy = true, error = null)
        viewModelScope.launch {
            state = try {
                when (val outcome = store.join(clean, lobby.uid, lobby.name)) {
                    is WorldStore.JoinOutcome.Joined -> enter(outcome.worldId, lobby.uid)
                    WorldStore.JoinOutcome.NoSuchCode -> lobby.copy(busy = false, error = "No world has that code. Check it with your friend.")
                    WorldStore.JoinOutcome.Full -> lobby.copy(busy = false, error = "That world already has two players.")
                }
            } catch (e: Exception) {
                lobby.copy(busy = false, error = "Couldn't join. Check your connection and try again.")
            }
        }
    }
}
