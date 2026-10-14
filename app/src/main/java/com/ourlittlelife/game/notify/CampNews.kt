package com.ourlittlelife.game.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.ourlittlelife.game.MainActivity
import com.ourlittlelife.game.R
import com.ourlittlelife.game.data.WorldStore
import com.ourlittlelife.rules.Glance
import com.ourlittlelife.rules.Nudge
import com.ourlittlelife.rules.Nudges
import com.ourlittlelife.rules.World
import java.util.concurrent.TimeUnit

/**
 * News from camp while the game is closed: your partner going to bed, a new day, a wave, an invitation,
 * crops ready. Free, with no server: every 15 minutes or so (as often as Android allows) the phone
 * reads its shared worlds and compares each with how it looked last time (Nudges in the rules). What
 * you've already seen in the game is recorded as you play, so it never comes back as a notification.
 */
object CampNews {

    private const val CHANNEL = "camp"
    private const val WORK = "camp-news"
    private const val CHECK_MINUTES = 15L

    /** True while the game is on screen: news is noted but not announced. */
    @Volatile var gameOnScreen = false

    private fun prefs(context: Context) = context.getSharedPreferences("camp-news", Context.MODE_PRIVATE)

    /** Whether this phone shows camp news. On unless turned off. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean("on", true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on", on).apply()
        if (on) start(context) else WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    /** Starts the regular check (once; later calls keep the existing schedule). */
    fun start(context: Context) {
        if (!enabled(context)) return
        val request = PeriodicWorkRequestBuilder<CampNewsWorker>(CHECK_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Notes how [world] looks to [uid] right now, so nothing in it is news later. */
    fun seen(context: Context, world: World, uid: String) {
        if (uid !in world.players) return
        prefs(context).edit().putString(key(world.id, uid), Glance.of(world, uid).encode()).apply()
    }

    /** Compares each shared world with last time, notes how it looks now, and announces what changed. */
    suspend fun check(context: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val worlds = WorldStore().openWorlds(uid)
        for (world in worlds) {
            val before = prefs(context).getString(key(world.id, uid), null)?.let(Glance::decode)
            val now = Glance.of(world, uid)
            prefs(context).edit().putString(key(world.id, uid), now.encode()).apply()
            if (!gameOnScreen && enabled(context)) {
                Nudges.between(before, now).forEach { announce(context, world.id, it) }
            }
        }
    }

    private fun key(worldId: String, uid: String) = "glance.$worldId.$uid"

    private fun announce(context: Context, worldId: String, nudge: Nudge) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Camp news", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your partner going to bed, a new day, waves and invitations, crops ready"
            },
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_camp_news)
            .setContentTitle(nudge.title)
            .setContentText(nudge.text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // One notification per world and kind: a newer wave replaces an older one instead of piling up.
        manager.notify("$worldId/${nudge.kind}".hashCode(), notification)
    }
}

class CampNewsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { CampNews.check(applicationContext) }
        .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}
