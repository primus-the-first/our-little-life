package com.ourlittlelife.game

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.ourlittlelife.rules.Moment
import com.ourlittlelife.rules.NoteMedia
import com.ourlittlelife.rules.Private
import com.ourlittlelife.game.data.PrivateMedia
import com.ourlittlelife.game.data.WorldSource
import com.ourlittlelife.game.data.WorldStore
import com.ourlittlelife.game.session.LobbyScreen
import com.ourlittlelife.game.session.Session
import com.ourlittlelife.game.session.SignInScreen
import com.ourlittlelife.game.home.GameViewModel
import com.ourlittlelife.game.home.HomeScreen
import com.ourlittlelife.game.ui.theme.OurLittleLifeTheme
import com.ourlittlelife.rules.Interiors
import com.ourlittlelife.rules.JoinResult
import com.ourlittlelife.rules.Room
import com.ourlittlelife.rules.RoomKind
import com.ourlittlelife.rules.Mode
import com.ourlittlelife.rules.Need
import com.ourlittlelife.rules.Needs
import com.ourlittlelife.rules.ObjectKind
import com.ourlittlelife.rules.Resource
import com.ourlittlelife.rules.Storage
import com.ourlittlelife.rules.World
import com.ourlittlelife.rules.Worlds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the main screen and its sheets on this computer, into app/build/shots, so the look can be
 * checked without a phone:  ./gradlew :app:testDebugUnitTest
 * (The 3D view needs a real GPU, so these use the flat map.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h873dp-420dpi")
class Screenshots {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** A world mid-game: two players, a stocked chest, and one need running low. */
    private fun world(): World {
        val solo = Worlds.create("w", "YF838H", Mode.BESTIES, "me", "Anthony")
        val pair = (Worlds.join(solo, "them", "Tourretta") as JoinResult.Joined).world
        val needs = Needs.full().plus(mapOf(Need.ENERGY to -2, Need.HUNGER to -18, Need.FUN to -52, Need.SOCIAL to -40, Need.HYGIENE to -81))
        val me = pair.player("me").let { it.copy(character = it.character.copy(needs = needs)) }
        val stocked = Storage().plus(mapOf(Resource.WOOD to 34, Resource.STONE to 16, Resource.FIBER to 70, Resource.PLANKS to 12, Resource.COINS to 1240, Resource.FISH to 2))
        return pair.withPlayer(me).copy(camp = pair.camp.copy(storage = stocked))
    }

    private class FakeSource(start: World) : WorldSource {
        override val solo = false
        private val state = MutableStateFlow(start)
        override fun observe(): Flow<World> = state
        override suspend fun update(change: (World) -> World?): World = (change(state.value) ?: state.value).also { state.value = it }
        override val media = object : PrivateMedia {
            override suspend fun put(type: String, from: String, bytes: ByteArray) = "new"
            override suspend fun get(id: String): ByteArray? = if (id == "p1") picture() else ByteArray(8)
            override suspend fun delete(id: String) = Unit
        }

        /** A stand-in photo: an evening sky over a hill. */
        private fun picture(): ByteArray {
            val bitmap = android.graphics.Bitmap.createBitmap(640, 400, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            canvas.drawColor(0xFF39507A.toInt())
            paint.color = 0xFFF5CB62.toInt()
            canvas.drawCircle(470f, 130f, 46f, paint)
            paint.color = 0xFF2E5B3C.toInt()
            canvas.drawOval(-120f, 280f, 760f, 620f, paint)
            return java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        }
    }

    private fun game(start: World = world()): GameViewModel =
        GameViewModel(ApplicationProvider.getApplicationContext<Application>()).apply { attach(FakeSource(start), "me") }

    /** The same world with a cabin built, and you standing inside it. */
    private fun indoors(): World {
        val w = world()
        val cabin = Room("cabin", RoomKind.CABIN, w.camp.plot.yard.x + 3, w.camp.plot.yard.y + 15)
        val built = w.copy(camp = w.camp.copy(plot = w.camp.plot.copy(rooms = listOf(cabin))))
        return Interiors.enter(built, "me", "cabin")!!
    }

    private fun shot(name: String, night: Boolean, start: World = world(), then: (GameViewModel) -> Unit = {}) {
        val game = game(start)
        compose.setContent { OurLittleLifeTheme(night = night) { HomeScreen(game) } }
        compose.waitForIdle()
        then(game)
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name-${if (night) "night" else "day"}.png")
    }

    @Test fun home_night() = shot("home", night = true)

    @Test fun home_day() = shot("home", night = false)

    @Test fun me_night() = shot("me", night = true) { it.showMe() }

    @Test fun together_night() = shot("together", night = true) { it.showPartner() }

    @Test fun campfire_night() = shot("campfire", night = true) { game ->
        game.tapTile(game.plot.objects.first { it.kind == ObjectKind.CAMPFIRE }.let { it.x }, game.plot.objects.first { it.kind == ObjectKind.CAMPFIRE }.y)
    }

    @Test fun campfire_day() = shot("campfire", night = false) { game ->
        game.tapTile(game.plot.objects.first { it.kind == ObjectKind.CAMPFIRE }.let { it.x }, game.plot.objects.first { it.kind == ObjectKind.CAMPFIRE }.y)
    }

    @Test fun tree_night() = shot("tree", night = true) { game ->
        val tree = game.plot.objects.first { it.kind == ObjectKind.TREE }
        game.tapTile(tree.x, tree.y)
    }

    @Test fun settings_night() = shot("settings", night = true) { compose.onNodeWithContentDescription("Settings").performClick() }

    @Test fun settings_day() = shot("settings", night = false) { compose.onNodeWithContentDescription("Settings").performClick() }

    @Test fun bed_night() = shot("bed", night = true) { compose.onNodeWithText("Bed").performClick() }

    @Test fun build_night() = shot("build", night = true) { game ->
        game.toggleBuildMode()
        compose.waitForIdle()
        game.tapTile(game.plot.yard.x + 3, game.plot.yard.y + 15)
    }

    @Test fun chest_day() = shot("chest", night = false) { game ->
        val chest = game.plot.objects.first { it.kind == ObjectKind.CHEST }
        game.tapTile(chest.x, chest.y)
    }

    @Test fun busy_night() = shot("busy", night = true) { game ->
        val fire = game.plot.objects.first { it.kind == ObjectKind.CAMPFIRE }
        game.perform(fire, game.actionsFor(fire).first())
        compose.mainClock.advanceTimeBy(3000)
    }

    @Test fun room_night() = shot("room", night = true, start = indoors())

    @Test fun room_piece_day() = shot("room-bed", night = false, start = indoors()) { game ->
        val bed = game.insideRoom!!.furniture.first()
        game.tapInside(bed.x, bed.y)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w873dp-h393dp-land-420dpi")
    fun landscape_night() = shot("landscape", night = true)

    /** A Partners world, side by side, both in, with a bond and a couple of notes. */
    private fun partners(bothIn: Boolean = true, asking: Moment? = null, media: Boolean = false): World {
        var w = Private.setMode(world(), Mode.PARTNERS)
        w = w.withPlayer(w.player("them").copy(tileX = w.player("me").tileX + 1, tileY = w.player("me").tileY))
        if (!bothIn) return w
        w = Private.optIn(Private.optIn(w, "me", true)!!, "them", true)!!.copy(bond = 14)
        w = Private.leaveNote(w, "them", "Dinner by the fire tonight?", now = 1)!!
        w = Private.leaveNote(w, "me", "Yes. I'll bring the fish.", now = 2)!!
        if (media) {
            w = Private.leaveMedia(w, "them", NoteMedia.PHOTO, "p1", now = 3, caption = "From the hill last night")!!
            w = Private.leaveMedia(w, "me", NoteMedia.VOICE, "v1", now = 4, seconds = 23)!!
        }
        return if (asking != null) Private.ask(w, "them", asking)!! else w
    }

    private fun openPrivate(pin: Boolean) {
        compose.onNodeWithText("Together").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Private").performScrollTo().performClick()
        compose.waitForIdle()
        if (pin) {
            compose.onNodeWithText("PIN, 4 to 8 digits").performTextInput("2468")
            compose.onNodeWithText("Same PIN again").performTextInput("2468")
            compose.onNodeWithText("Set PIN").performClick()
            compose.waitForIdle()
        }
    }

    @Test fun private_row_night() = shot("private-row", night = true, start = partners()) { compose.onNodeWithText("Together").performClick() }

    @Test fun private_gate_night() = shot("private-gate", night = true, start = partners()) { openPrivate(pin = false) }

    @Test fun private_optin_day() = shot("private-optin", night = false, start = partners(bothIn = false)) { openPrivate(pin = true) }

    @Test fun private_inside_night() = shot("private-inside", night = true, start = partners()) { openPrivate(pin = true) }

    @Test fun private_asked_day() = shot("private-asked", night = false, start = partners(asking = Moment.CUDDLE)) { openPrivate(pin = true) }

    @Test fun private_media_night() = shot("private-media", night = true, start = partners(media = true)) {
        openPrivate(pin = true)
        compose.onNodeWithText("Voice").performScrollTo()
    }

    @Test fun private_card_night() = shot("private-card", night = true, start = partners(asking = Moment.CUDDLE))

    @Test fun lobby_night() = screen("lobby", night = true) {
        LobbyScreen(
            Session.Lobby(
                uid = "me", name = "Anthony", soloSummary = "Day 3 · 1 room",
                worlds = listOf(
                    WorldStore.Summary("a", "YF838H", Mode.BESTIES, "Tourretta", 10),
                    WorldStore.Summary("b", "2AGT8E", Mode.PARTNERS, null, 2),
                ),
            ),
            onOpen = {}, onPlaySolo = {}, onCreate = { _, _ -> }, onJoin = {},
        )
    }

    @Test fun lobby_day() = screen("lobby", night = false) {
        LobbyScreen(
            Session.Lobby(uid = "me", name = "Anthony", soloSummary = null, worlds = emptyList()),
            onOpen = {}, onPlaySolo = {}, onCreate = { _, _ -> }, onJoin = {},
        )
    }

    @Test fun signin_night() = screen("signin", night = true) { SignInScreen(Session.SignedOut(), onSignIn = {}, onPlaySolo = {}) }

    @Test fun signin_day() = screen("signin", night = false) { SignInScreen(Session.SignedOut(), onSignIn = {}, onPlaySolo = {}) }

    private fun screen(name: String, night: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { OurLittleLifeTheme(night = night) { content() } }
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/$name-${if (night) "night" else "day"}.png")
    }
}
