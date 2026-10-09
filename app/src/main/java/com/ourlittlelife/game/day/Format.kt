package com.ourlittlelife.game.day

import com.ourlittlelife.rules.Action

/** 990 → "4:30 pm" */
fun formatTime(minuteOfDay: Int): String {
    val hour24 = (minuteOfDay / 60) % 24
    val minute = minuteOfDay % 60
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    val suffix = if (hour24 < 12) "am" else "pm"
    return "%d:%02d %s".format(hour12, minute, suffix)
}

/** 90 → "1h 30m", 30 → "30m", 480 → "8h" */
fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

/** "1h · +45 Hunger, +5 Fun", or for gathering "1h · +6 Wood · -6 Energy" */
fun describe(action: Action): String {
    val yields = action.yields.entries.joinToString(", ") { (resource, amount) -> "+$amount ${resource.label}" }
    val effects = action.effects.entries.joinToString(", ") { (need, change) ->
        (if (change > 0) "+" else "") + "$change ${need.label}"
    }
    return listOf(formatDuration(action.minutes), yields, effects).filter { it.isNotEmpty() }.joinToString(" · ")
}

fun moodWord(mood: Int): String = when {
    mood >= 70 -> "Cheerful"
    mood >= 40 -> "Fine"
    else -> "Grumpy"
}
