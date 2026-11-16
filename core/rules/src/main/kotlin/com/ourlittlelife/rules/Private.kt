package com.ourlittlelife.rules

import com.ourlittlelife.rules.Need.ENERGY
import com.ourlittlelife.rules.Need.FUN
import com.ourlittlelife.rules.Need.HYGIENE
import com.ourlittlelife.rules.Need.SOCIAL
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * A moment for the two of you. Each takes [minutes], changes how you both feel, and grows the bond by
 * [bond]; the closer ones wait until the bond has reached [needsBond], and for the right room.
 * What happens is never shown: the screen fades, and time passes.
 */
@Serializable
enum class Moment(val label: String, val minutes: Int, val bond: Int, val needsBond: Int, val effects: Map<Need, Int>) {
    CUDDLE("Cuddle up", 20, bond = 3, needsBond = 0, effects = mapOf(SOCIAL to 15, FUN to 5)),
    MAKE_OUT("Make out", 20, bond = 5, needsBond = 0, effects = mapOf(SOCIAL to 20, FUN to 15)),
    SHOWER("Shower together", 30, bond = 6, needsBond = 10, effects = mapOf(HYGIENE to 50, SOCIAL to 15, FUN to 10)),
    BED("Go to bed together", 60, bond = 10, needsBond = 20, effects = mapOf(SOCIAL to 30, FUN to 30, ENERGY to -10)),
}

/** Why a moment can't be asked for right now. */
enum class MomentProblem { OFF, ASLEEP, APART, BOND, PLACE }

/** What a note can carry besides words. */
enum class NoteMedia { PHOTO, VOICE }

/**
 * A note one of you left for the other in the private space. A photo or a voice message is a note too:
 * [photo] or [voice] is the id of its file, which is kept apart from the world (see the app's PrivateMedia),
 * [text] is its caption, and [seconds] is how long a voice message runs.
 */
@Serializable
data class Note(
    val id: String,
    val from: String,
    val text: String,
    val at: Long,
    val photo: String? = null,
    val voice: String? = null,
    val seconds: Int = 0,
)

/**
 * The private side of a Partners world. It exists only when both players have opted in, and either can
 * turn it off for both. Moments are asked for and have to be accepted; notes are yours alone. Nothing
 * in here is ever described in a notification.
 */
object Private {

    const val NOTE_LENGTH = 500
    const val NOTES_KEPT = 60
    const val BOND_MAX = 100

    /** The longest a voice message can be. */
    const val VOICE_SECONDS = 60

    /** Only Partners worlds have a private side at all. */
    fun offered(world: World): Boolean = world.mode == Mode.PARTNERS

    /** On once there are two of you and both have said yes. */
    fun on(world: World): Boolean = offered(world) && world.players.size == 2 && world.players.values.all { it.adult }

    /** One player's own yes or no. Null outside a Partners world. Turning it off drops anything being asked. */
    fun optIn(world: World, playerId: String, yes: Boolean): World? {
        if (!offered(world)) return null
        val mine = world.withPlayer(world.player(playerId).copy(adult = yes))
        if (yes) return mine
        return mine.copy(players = mine.players.mapValues { (_, p) -> p.copy(asking = null) })
    }

    /**
     * Off for both, and both have to say yes again. For a forgotten PIN: whoever resets it on one phone
     * can't get back in without the other knowing.
     */
    fun reset(world: World): World = world.copy(players = world.players.mapValues { (_, p) -> p.copy(adult = false, asking = null) })

    /**
     * Changes what the two of you are. Leaving Partners turns the private side off for both; the bond and
     * your notes are kept, out of sight, in case you come back.
     */
    fun setMode(world: World, mode: Mode): World =
        if (mode == Mode.PARTNERS) world.copy(mode = mode) else reset(world).copy(mode = mode)

    /** What stands in the way of [from] asking for [moment], or null if nothing does. */
    fun problem(world: World, from: String, moment: Moment): MomentProblem? {
        if (!on(world)) return MomentProblem.OFF
        val me = world.player(from)
        val other = world.players.values.first { it.id != from }
        if (me.character.asleep || other.character.asleep) return MomentProblem.ASLEEP
        if (!together(me, other)) return MomentProblem.APART
        if (world.bond < moment.needsBond) return MomentProblem.BOND
        if (!rightPlace(world, me, moment)) return MomentProblem.PLACE
        return null
    }

    /** Asks your partner. Nothing happens until they say yes. Null if it can't be asked right now. */
    fun ask(world: World, from: String, moment: Moment): World? {
        if (problem(world, from, moment) != null) return null
        return world.withPlayer(world.player(from).copy(asking = moment))
    }

    /** Who is asking [forPlayer], and for what. */
    fun waiting(world: World, forPlayer: String): Pair<Player, Moment>? =
        world.players.values.firstOrNull { it.id != forPlayer && it.asking != null }?.let { it to it.asking!! }

    /** "Not now", or taking back your own asking. */
    fun decline(world: World, by: String): World {
        val asker = waiting(world, by)?.first ?: world.player(by).takeIf { it.asking != null } ?: return world
        return world.withPlayer(asker.copy(asking = null))
    }

    /** Saying yes. Null if nobody asked, or it can no longer happen (you've moved apart, someone fell asleep). */
    fun accept(world: World, by: String): World? {
        val (asker, moment) = waiting(world, by) ?: return null
        if (problem(world, asker.id, moment) != null) return null
        val shared = Action("moment_${moment.name.lowercase()}", moment.label, moment.minutes, moment.effects)
        val me = world.player(by)
        val mine = Rules.perform(me.character, shared) as? ActionResult.Done ?: return null
        val theirs = Rules.perform(asker.character, shared) as? ActionResult.Done ?: return null
        return world
            .withPlayer(me.copy(character = mine.character))
            .withPlayer(asker.copy(character = theirs.character, asking = null))
            .copy(bond = minOf(BOND_MAX, world.bond + moment.bond))
    }

    /** Leaves a note for the two of you. Null while the private side is off, or if there's nothing in it. */
    fun leaveNote(world: World, from: String, text: String, now: Long): World? {
        val clean = text.trim().take(NOTE_LENGTH)
        if (!on(world) || clean.isEmpty()) return null
        return world.copy(notes = (world.notes + Note(nextNoteId(world), from, clean, now)).takeLast(NOTES_KEPT))
    }

    /** Leaves a photo or a voice message whose file is already saved as [mediaId]. Null while the private side is off. */
    fun leaveMedia(world: World, from: String, kind: NoteMedia, mediaId: String, now: Long, caption: String = "", seconds: Int = 0): World? {
        if (!on(world)) return null
        val note = Note(
            id = nextNoteId(world),
            from = from,
            text = caption.trim().take(NOTE_LENGTH),
            at = now,
            photo = mediaId.takeIf { kind == NoteMedia.PHOTO },
            voice = mediaId.takeIf { kind == NoteMedia.VOICE },
            seconds = if (kind == NoteMedia.VOICE) seconds.coerceIn(0, VOICE_SECONDS) else 0,
        )
        return world.copy(notes = (world.notes + note).takeLast(NOTES_KEPT))
    }

    /** The files that [before]'s notes pointed at and [after]'s no longer do: deleted notes, and old ones that made room. */
    fun orphans(before: World, after: World): List<String> {
        val kept = after.notes.flatMap { listOfNotNull(it.photo, it.voice) }.toSet()
        return before.notes.flatMap { listOfNotNull(it.photo, it.voice) }.filterNot { it in kept }
    }

    private fun nextNoteId(world: World): String = "n${(world.notes.maxOfOrNull { it.id.drop(1).toLongOrNull() ?: 0 } ?: 0) + 1}"

    /** Deletes a note for both of you. */
    fun deleteNote(world: World, id: String): World = world.copy(notes = world.notes.filterNot { it.id == id })

    /** Awake and side by side outdoors, or in the same room. */
    private fun together(a: Player, b: Player): Boolean = when {
        a.inside == null && b.inside == null -> abs(a.tileX - b.tileX) + abs(a.tileY - b.tileY) <= 1
        a.inside != null && b.inside != null -> a.inside.roomId == b.inside.roomId
        else -> false
    }

    private fun rightPlace(world: World, me: Player, moment: Moment): Boolean {
        val room = me.inside?.let { at -> world.camp.plot.rooms.firstOrNull { it.id == at.roomId } }
        return when (moment) {
            Moment.SHOWER -> room?.kind == RoomKind.BATHROOM
            Moment.BED -> room?.furniture?.any { it.kind == FurnitureKind.BED } == true
            else -> true
        }
    }
}
