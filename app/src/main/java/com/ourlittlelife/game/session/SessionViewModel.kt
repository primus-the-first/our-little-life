package com.ourlittlelife.game.session

import android.app.Activity
import android.app.Application
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

/** Where the player is in getting to their world. */
sealed interface Session {
    data object Loading : Session
    data class SignedOut(val error: String? = null, val busy: Boolean = false) : Session
    data class NoWorld(
        val uid: String,
        val name: String,
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

    var state by mutableStateOf<Session>(Session.Loading)
        private set

    init {
        viewModelScope.launch {
            auth.users().collect { user -> state = if (user == null) Session.SignedOut() else findWorld(user) }
        }
    }

    private suspend fun findWorld(user: FirebaseUser): Session = try {
        store.findWorldFor(user.uid)?.let { Session.InWorld(it, user.uid) } ?: noWorld(user)
    } catch (e: Exception) {
        noWorld(user, error = "Couldn't reach the game. Check your connection and try again.")
    }

    private fun noWorld(user: FirebaseUser, error: String? = null) =
        Session.NoWorld(user.uid, Auth.firstName(user), soloSummary(), error)

    /** "Day 3 · 18 wood, 8 stone" for a solo game that will come along into the new world. */
    private fun soloSummary(): String? {
        val solo = soloSave.load() ?: return null
        val stock = Resource.entries.filter { solo.camp.storage[it] > 0 }
            .joinToString(", ") { "${solo.camp.storage[it]} ${it.label.lowercase()}" }
        val rooms = if (solo.camp.plot.rooms.isNotEmpty()) " · ${solo.camp.plot.rooms.size} room built" else ""
        return "Day ${solo.character.clock.day}" + (if (stock.isNotEmpty()) " · $stock" else "") + rooms
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

    fun create(mode: Mode) {
        val current = state as? Session.NoWorld ?: return
        state = current.copy(busy = true, error = null)
        viewModelScope.launch {
            state = try {
                Session.InWorld(store.create(current.uid, current.name, mode, soloSave.load()), current.uid)
            } catch (e: Exception) {
                current.copy(busy = false, error = "Couldn't create the world. Check your connection and try again.")
            }
        }
    }

    fun join(code: String) {
        val current = state as? Session.NoWorld ?: return
        if (code.trim().length != 6) {
            state = current.copy(error = "Codes are 6 letters and numbers, like KQ7XMB.")
            return
        }
        state = current.copy(busy = true, error = null)
        viewModelScope.launch {
            state = try {
                when (val outcome = store.join(code, current.uid, current.name)) {
                    is WorldStore.JoinOutcome.Joined -> Session.InWorld(outcome.worldId, current.uid)
                    WorldStore.JoinOutcome.NoSuchCode -> current.copy(busy = false, error = "No world has that code. Check it with your friend.")
                    WorldStore.JoinOutcome.Full -> current.copy(busy = false, error = "That world already has two players.")
                }
            } catch (e: Exception) {
                current.copy(busy = false, error = "Couldn't join. Check your connection and try again.")
            }
        }
    }
}
