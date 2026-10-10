package com.ourlittlelife.game.data

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.ourlittlelife.game.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Signing in with the Google account already on the phone, through Android's standard account picker. */
class Auth(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Emits the signed-in user, or null, now and whenever it changes. */
    fun users(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    sealed interface Outcome {
        data object SignedIn : Outcome
        data object Cancelled : Outcome
        data class Failed(val reason: String) : Outcome
    }

    suspend fun signInWithGoogle(activity: Activity): Outcome = try {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activity.getString(R.string.default_web_client_id))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
            Outcome.SignedIn
        } else {
            Outcome.Failed("That account type isn't supported. Pick a Google account.")
        }
    } catch (e: GetCredentialCancellationException) {
        Outcome.Cancelled
    } catch (e: GetCredentialException) {
        Outcome.Failed("Couldn't sign in with Google. Check that this phone has a Google account and is online.")
    } catch (e: Exception) {
        Outcome.Failed("Couldn't sign in. Check your connection and try again.")
    }

    companion object {
        /** "Anthony Bonney" becomes "Anthony", the name shown above your character. */
        fun firstName(user: FirebaseUser): String =
            user.displayName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: "Friend"
    }
}
