package com.ourlittlelife.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The few things about a world one player's phone keeps an eye on while the game is closed: the day,
 * whether each of you is asleep, the latest greeting you got, what your partner is up to, and which
 * crops are ready. Small enough to save after every check.
 */
@Serializable
data class Glance(
    val day: Int,
    val meAsleep: Boolean,
    val partnerName: String? = null,
    val partnerAsleep: Boolean = false,
    val partnerActivity: Activity? = null,
    val greetingCount: Int = 0,
    val greeting: Gesture? = null,
    val readyCrops: Set<Crop> = emptySet(),
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(world: World, me: String): Glance {
            val mine = world.player(me)
            val partner = world.players.values.firstOrNull { it.id != me }
            val greeting = mine.greeting?.takeIf { it.from != me }
            return Glance(
                day = world.day,
                meAsleep = mine.character.asleep,
                partnerName = partner?.character?.name,
                partnerAsleep = partner?.character?.asleep ?: false,
                partnerActivity = partner?.activity,
                greetingCount = greeting?.count ?: 0,
                greeting = greeting?.gesture,
                readyCrops = world.camp.plot.rooms.flatMap { it.beds }.filter(Gardening::ready).mapNotNull { it.crop }.toSet(),
            )
        }

        fun decode(text: String): Glance? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

enum class NudgeKind { JOINED, NEW_DAY, PARTNER_ASLEEP, GREETING, STARGAZING, WALK, CROPS }

/** One notification: what it's about, a short title, and a line underneath. */
data class Nudge(val kind: NudgeKind, val title: String, val text: String)

/** What's worth telling a player about, from how their world looked at the last check and how it looks now. */
object Nudges {

    fun between(before: Glance?, after: Glance): List<Nudge> {
        if (before == null) return emptyList()
        val partner = after.partnerName
        val nudges = mutableListOf<Nudge>()

        if (before.partnerName == null && partner != null) {
            nudges += Nudge(NudgeKind.JOINED, "$partner joined your world", "Say hello when you're next at camp.")
        }
        if (after.day > before.day) {
            val weekday = Clock.startOfDay(after.day).weekday
            nudges += Nudge(NudgeKind.NEW_DAY, "$weekday is here", "A new day at camp.")
        } else if (partner != null && after.partnerAsleep && !before.partnerAsleep && !after.meAsleep) {
            val tomorrow = Clock.startOfDay(after.day + 1).weekday
            nudges += Nudge(NudgeKind.PARTNER_ASLEEP, "$partner went to bed", "$tomorrow starts when you sleep too.")
        }
        if (partner != null && after.greetingCount > before.greetingCount && after.greeting != null) {
            nudges += Nudge(NudgeKind.GREETING, "$partner ${after.greeting.past}", "Say hi back at camp.")
        }
        if (partner != null && after.partnerActivity != before.partnerActivity) {
            when (after.partnerActivity) {
                Activity.STARGAZING -> nudges += Nudge(NudgeKind.STARGAZING, "$partner is stargazing", "Join them on the hill.")
                Activity.WALK_INVITE -> nudges += Nudge(NudgeKind.WALK, "$partner would like a walk", "Head out together.")
                null -> Unit
            }
        }
        val ripe = after.readyCrops - before.readyCrops
        if (ripe.isNotEmpty()) {
            val names = ripe.sortedBy { it.ordinal }.map { it.label.lowercase() }
            val what = if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " and " + names.last()
            val verb = if (ripe.size == 1 && ripe.single() == Crop.PUMPKIN) "is" else "are"
            nudges += Nudge(NudgeKind.CROPS, "Your $what $verb ready", "Pick them in the garden.")
        }
        return nudges
    }
}
