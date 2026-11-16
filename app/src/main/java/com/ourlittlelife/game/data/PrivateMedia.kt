package com.ourlittlelife.game.data

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Where the private side's photos and voice messages are kept: apart from the world's game state, and
 * readable only by the world's two members. Never in the app or the repo, which are public.
 */
interface PrivateMedia {
    /** Saves a file and returns its id. */
    suspend fun put(type: String, from: String, bytes: ByteArray): String

    /** The file's bytes, or null if it's gone. */
    suspend fun get(id: String): ByteArray?

    suspend fun delete(id: String)

    companion object {
        /** The most one file may weigh. Firestore documents top out at 1 MB, and the rules enforce 900 KB. */
        const val MAX_BYTES = 850_000
    }
}

/**
 *   worlds/{worldId}/private/{id}   type ("photo" or "voice"), from (user id), at, bytes
 *
 * firestore.rules lets only the world's members read, add and delete these. Files this phone has
 * already fetched are kept in memory for the session, so scrolling back doesn't fetch them again.
 */
class FirestoreMedia(worldId: String, db: FirebaseFirestore = FirebaseFirestore.getInstance()) : PrivateMedia {

    private val files = db.collection("worlds").document(worldId).collection("private")
    private val fetched = HashMap<String, ByteArray>()

    override suspend fun put(type: String, from: String, bytes: ByteArray): String {
        require(bytes.size <= PrivateMedia.MAX_BYTES) { "Too big to keep: ${bytes.size} bytes" }
        val doc = files.document()
        doc.set(mapOf("type" to type, "from" to from, "at" to System.currentTimeMillis(), "bytes" to Blob.fromBytes(bytes))).await()
        fetched[doc.id] = bytes
        return doc.id
    }

    override suspend fun get(id: String): ByteArray? =
        fetched[id] ?: files.document(id).get().await().getBlob("bytes")?.toBytes()?.also { fetched[id] = it }

    override suspend fun delete(id: String) {
        fetched.remove(id)
        files.document(id).delete().await()
    }
}
