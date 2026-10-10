package com.ourlittlelife.game.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ourlittlelife.rules.JoinResult
import com.ourlittlelife.rules.Mode
import com.ourlittlelife.rules.SaveGame
import com.ourlittlelife.rules.World
import com.ourlittlelife.rules.Worlds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Shared worlds in Firestore.
 *
 *   worlds/{worldId}  code, mode, members (up to two user ids), state (the whole World as JSON), updatedAt
 *   codes/{code}      worldId, so a friend can find the world from its invite code
 *
 * Every change runs the game rules inside a transaction on the latest saved state,
 * so two phones changing the world at the same moment never overwrite each other.
 */
class WorldStore(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val worlds = db.collection("worlds")
    private val codes = db.collection("codes")

    /** A world as the lobby shows it. */
    data class Summary(val id: String, val code: String, val mode: Mode, val partnerName: String?, val day: Int)

    /** Every world this person belongs to, most recently played first. */
    suspend fun worldsFor(uid: String): List<Summary> =
        worlds.whereArrayContains("members", uid).get().await().documents
            .sortedByDescending { it.getTimestamp("updatedAt")?.seconds ?: 0 }
            .mapNotNull { doc ->
                val world = doc.getString("state")?.let(Worlds::decode) ?: return@mapNotNull null
                Summary(
                    id = doc.id,
                    code = world.code,
                    mode = world.mode,
                    partnerName = world.players.values.firstOrNull { it.id != uid }?.character?.name,
                    day = world.day,
                )
            }

    /** Creates a world with this person as host. A solo save, if there is one, comes along as the starting point. */
    suspend fun create(uid: String, name: String, mode: Mode, solo: SaveGame?): String {
        var code = Worlds.newCode()
        while (codes.document(code).get().await().exists()) code = Worlds.newCode()

        val ref = worlds.document()
        var world = Worlds.create(ref.id, code, mode, uid, name)
        if (solo != null) {
            val host = world.player(uid)
            world = world.copy(camp = solo.camp).withPlayer(
                host.copy(character = solo.character.copy(name = name, asleep = false), tileX = solo.tileX, tileY = solo.tileY),
            )
        }
        ref.set(
            mapOf(
                "code" to code,
                "mode" to mode.name,
                "members" to listOf(uid),
                "state" to Worlds.encode(world),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        codes.document(code).set(mapOf("worldId" to ref.id)).await()
        return ref.id
    }

    sealed interface JoinOutcome {
        data class Joined(val worldId: String) : JoinOutcome
        data object NoSuchCode : JoinOutcome
        data object Full : JoinOutcome
    }

    /**
     * Joins with an invite code. First this person adds themselves to the members list
     * (the security rules allow only that, and only while there's room); then, as a member,
     * they add their character to the world.
     */
    suspend fun join(code: String, uid: String, name: String): JoinOutcome {
        val clean = code.trim().uppercase()
        val codeDoc = codes.document(clean).get().await()
        val worldId = codeDoc.getString("worldId") ?: return JoinOutcome.NoSuchCode
        try {
            worlds.document(worldId).update("members", FieldValue.arrayUnion(uid)).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) return JoinOutcome.Full
            throw e
        }
        update(worldId) { world ->
            when (val result = Worlds.join(world, uid, name)) {
                is JoinResult.Joined -> result.world
                is JoinResult.AlreadyIn -> null
                JoinResult.Full -> null
            }
        }
        return JoinOutcome.Joined(worldId)
    }

    /** The world as it changes, live. */
    fun observe(worldId: String): Flow<World> = callbackFlow {
        val registration = worlds.document(worldId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            snapshot?.getString("state")?.let { trySend(Worlds.decode(it)) }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Applies [change] to the latest saved world inside a transaction and saves the result.
     * [change] returns null to mean "that can't happen any more" (say, your partner just used the last of a bush).
     * Returns the world as it now stands, changed or not.
     */
    suspend fun update(worldId: String, change: (World) -> World?): World {
        val ref = worlds.document(worldId)
        return db.runTransaction { transaction ->
            val current = Worlds.decode(transaction.get(ref).getString("state")!!)
            val next = change(current)
            if (next != null && next != current) {
                transaction.update(ref, mapOf("state" to Worlds.encode(next), "updatedAt" to FieldValue.serverTimestamp()))
            }
            next ?: current
        }.await()
    }
}
