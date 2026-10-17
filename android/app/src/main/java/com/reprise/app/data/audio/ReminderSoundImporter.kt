package com.reprise.app.data.audio

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Copies a sound the user picked from their phone into the phone's shared sound
 * library, so that ANDROID can play it later.
 *
 * Why a copy is needed at all: the reminder's sound is played by the system when
 * the notification is posted, and a file the user chose with a document picker
 * is readable only by this app. Pointing the notification at the picked file
 * would install a reminder that silently makes no sound on some devices — the
 * worst kind of bug, because nothing looks broken. Adding the file to the shared
 * sound library removes the whole question: that library is exactly where every
 * notification sound on the phone already lives, and the system can read it.
 *
 * It has a pleasant side effect too: the imported sound also becomes selectable
 * in Android's own sound settings, so it is not locked inside RepRise.
 */
object ReminderSoundImporter {

    /**
     * The largest file worth importing, in bytes.
     *
     * A notification sound is a second or two long; this limit only exists so
     * that a mistakenly chosen album cannot be copied into the sound library. It
     * is generous enough (about five minutes of compressed audio) that a real
     * sound is never refused.
     */
    const val MAX_BYTES = 5L * 1024 * 1024

    /** The outcome of an import. */
    sealed interface Result {
        /**
         * The sound is in the shared library and ready to use.
         *
         * @param uri the URI to hand to the notification channel.
         * @param name the picked file's name, for display.
         */
        data class Imported(val uri: String, val name: String) : Result

        /** The file was bigger than [MAX_BYTES]. */
        data object TooLarge : Result

        /** The file could not be read or stored — unreadable, moved or empty. */
        data object Unreadable : Result
    }

    /**
     * Copy the audio at [source] into the shared sound library.
     *
     * Runs on the IO dispatcher because it reads a file, writes a file and talks
     * to the media provider — none of which belongs on the main thread.
     */
    suspend fun import(context: Context, source: Uri): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        // A broad catch is right here: the content resolver can fail in many
        // ways (the file was deleted after being picked, the provider crashed,
        // storage is full), and all of them mean the same thing to the user —
        // this sound cannot be used, try another. A crash would be worse.
        try {
            val name = displayNameOf(resolver, source) ?: FALLBACK_FILE_NAME

            val bytes = resolver.openInputStream(source)?.use { it.readAtMost(MAX_BYTES) }
                ?: return@withContext Result.Unreadable
            if (bytes.isEmpty()) return@withContext Result.Unreadable

            val stored = storeAsNotificationSound(
                resolver = resolver,
                name = name,
                mimeType = resolver.getType(source),
                bytes = bytes,
            ) ?: return@withContext Result.Unreadable

            Result.Imported(uri = stored.toString(), name = name)
        } catch (error: Exception) {
            Result.Unreadable
        }
    }

    /**
     * Read the whole stream, or give up once it exceeds [limit].
     *
     * Returning null (rather than a truncated array) matters: a half-copied
     * sound would be stored, look fine in the settings list, and play as a
     * fraction of a second of noise.
     */
    private fun InputStream.readAtMost(limit: Long): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        while (true) {
            val read = read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            if (output.size() > limit) return null
        }

        return output.toByteArray()
    }

    /** The picked file's name, as the document provider reports it. */
    private fun displayNameOf(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.takeIf { it.isNotBlank() }

    /**
     * Add the bytes to the audio library as a notification sound.
     *
     * `IS_NOTIFICATION` is what makes the file appear among the phone's
     * notification sounds; without it the file would be treated as music.
     *
     * On Android 10 and later the row is written in two steps — inserted with
     * `IS_PENDING` set, filled in, then published — so that no other app can
     * read a half-written file. Earlier versions have no such flag and write
     * straight into the shared library, which needs WRITE_EXTERNAL_STORAGE (the
     * manifest declares it only up to API 28, and the caller asks for it at that
     * point).
     */
    @Suppress("DEPRECATION") // The pre-Android-10 collection, used only on those versions.
    private fun storeAsNotificationSound(
        resolver: ContentResolver,
        name: String,
        mimeType: String?,
        bytes: ByteArray,
    ): Uri? {
        val pending = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            // The title is what most sound pickers show, and it reads better
            // without the extension.
            put(MediaStore.Audio.Media.TITLE, name.substringBeforeLast('.', name))
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType ?: FALLBACK_MIME_TYPE)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
            if (pending) put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val collection = if (pending) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val target = resolver.insert(collection, values) ?: return null

        val written = resolver.openOutputStream(target)?.use { output ->
            output.write(bytes)
            true
        } ?: false

        if (!written) {
            // Do not leave an empty row behind for a file that was never filled.
            resolver.delete(target, null, null)
            return null
        }

        if (pending) {
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(target, values, null, null)
        }

        return target
    }

    /** Used when the provider does not report a name; the extension is cosmetic. */
    private const val FALLBACK_FILE_NAME = "reminder_sound.mp3"

    /** Used when the provider does not report a type. */
    private const val FALLBACK_MIME_TYPE = "audio/mpeg"
}
