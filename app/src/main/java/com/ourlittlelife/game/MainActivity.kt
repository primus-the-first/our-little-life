package com.ourlittlelife.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ourlittlelife.game.home.GameViewModel
import com.ourlittlelife.game.home.HomeScreen
import com.ourlittlelife.game.session.LoadingScreen
import com.ourlittlelife.game.session.Session
import com.ourlittlelife.game.session.SessionViewModel
import com.ourlittlelife.game.session.SignInScreen
import com.ourlittlelife.game.session.WorldSetupScreen
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
        is Session.SignedOut -> SignInScreen(state, onSignIn = session::signIn)
        is Session.NoWorld -> WorldSetupScreen(state, onCreate = session::create, onJoin = session::join)
        is Session.InWorld -> {
            val game: GameViewModel = viewModel()
            LaunchedEffect(state.worldId) { game.attach(state.worldId, state.uid) }
            if (game.ready) HomeScreen(game) else LoadingScreen()
        }
    }
}
