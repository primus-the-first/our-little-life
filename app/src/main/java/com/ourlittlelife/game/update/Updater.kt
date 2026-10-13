package com.ourlittlelife.game.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ourlittlelife.game.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A published version of the game: what latest.json on the releases branch says. */
data class Release(val versionCode: Int, val versionName: String, val apk: String, val notes: String)

/**
 * Keeps friends' phones up to date without the Play Store. Each release puts the APK and a small
 * latest.json on the repo's "releases" branch (tools/release.sh); the game reads latest.json when it
 * opens, and if there's a newer version, downloads the APK and hands it to Android's installer.
 * Android asks the player to confirm, and the first time, to allow this app to install updates.
 */
object Updater {

    private const val LATEST = "https://raw.githubusercontent.com/primus-the-first/our-little-life/releases/latest.json"
    private const val TIMEOUT_MILLIS = 15_000

    /**
     * The newest published version, if it's newer than this one; null if not, or if we can't tell (offline).
     * Debug builds never offer updates: they're signed with a different key, so Android would refuse them.
     */
    suspend fun newer(): Release? = if (BuildConfig.DEBUG) null else withContext(Dispatchers.IO) {
        runCatching {
            val text = open(LATEST).inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            Release(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apk = json.getString("apk"),
                notes = json.optString("notes"),
            )
        }.getOrNull()?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    /** Downloads the release's APK into the app's cache, reporting progress from 0 to 1. */
    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        val target = File(folder, "our-little-life-${release.versionCode}.apk")
        val connection = open(release.apk)
        val total = connection.contentLengthLong.takeIf { it > 0 }
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    total?.let { onProgress((done.toFloat() / it).coerceIn(0f, 1f)) }
                }
            }
        }
        target
    }

    /** True once the player has let this app install updates (Android asks once, in Settings). */
    fun mayInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the Settings page where the player allows this app to install updates. */
    fun askToAllowInstalls(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the downloaded APK to Android's installer, which asks the player to confirm. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            useCaches = false
            instanceFollowRedirects = true
        }
}
