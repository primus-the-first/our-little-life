package com.ourlittlelife.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ourlittlelife.game.data.Auth
import com.ourlittlelife.game.data.SharedWorldSource
import com.ourlittlelife.game.data.SoloWorldSource
import com.ourlittlelife.game.data.WorldStore
import com.ourlittlelife.game.home.GameViewModel
import com.ourlittlelife.game.home.HomeScreen
import com.ourlittlelife.game.session.LoadingScreen
import com.ourlittlelife.game.session.LobbyScreen
import com.ourlittlelife.game.session.Session
import com.ourlittlelife.game.session.SessionViewModel
import com.ourlittlelife.game.session.SignInScreen
import com.ourlittlelife.game.ui.theme.OurLittleLifeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OurLittleLifeTheme {
                AppRoot()
            }
        }
    }
}

/** Sign in, then create or join a world, then play. */
@Composable
private fun AppRoot(session: SessionViewModel = viewModel()) {
    when (val state = session.state) {
        Session.Loading -> LoadingScreen()
        is Session.SignedOut -> SignInScreen(state, onSignIn = session::signIn, onPlaySolo = session::playSolo)
        is Session.Lobby -> LobbyScreen(state, onOpen = session::openWorld, onPlaySolo = session::playSolo, onCreate = session::create, onJoin = session::join)
        is Session.InWorld -> {
            // One game per world: switching worlds gives a fresh game, never a mix of two.
            val game: GameViewModel = viewModel(key = state.worldId)
            val context = LocalContext.current
            LaunchedEffect(state.worldId) {
                val source = if (state.solo) {
                    SoloWorldSource(context.filesDir, playerName = Auth().currentUser?.let(Auth::firstName) ?: "You")
                } else {
                    SharedWorldSource(WorldStore(), state.worldId)
                }
                game.attach(source, state.uid)
            }
            if (game.ready) HomeScreen(game, onBackToLobby = session::backToLobby) else LoadingScreen()
        }
    }
}
