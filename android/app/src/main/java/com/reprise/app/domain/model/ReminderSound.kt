package com.reprise.app.domain.model

/**
 * The sound the gym reminder makes when it arrives.
 *
 * Two of the three choices are Android's own — the phone's default notification
 * tone, or silence. The third is a sound the user picked from their phone.
 *
 * That third one carries a subtlety worth understanding, because it explains why
 * this type exists at all. On Android the SYSTEM plays a notification's sound,
 * not the app that posted it, and the system is not allowed to read a file this
 * app picked with a document picker. So the picked file cannot be used directly;
 * it has to be COPIED somewhere the system can read it (the phone's shared sound
 * library — see `ReminderSoundImporter`). [Clip.uri] is that copy, which is why
 * it is a plain URI string rather than a file path or a document ID.
 *
 * Keeping the choice in the domain as a small sealed type means the UI, the
 * storage and the notification channels each handle the same three cases once,
 * and the compiler points at every place that needs to care when one is added.
 */
sealed interface ReminderSound {

    /** Whatever this phone plays for notifications in general. */
    data object SystemDefault : ReminderSound

    /** No sound at all; the reminder still appears and still vibrates. */
    data object Silent : ReminderSound

    /**
     * A sound the user imported.
     *
     * @param uri the shared-library URI of the imported copy — the one Android
     *   is able to read when it plays the notification.
     * @param name the file's name when it was picked, so the card can say which
     *   sound is set. Empty if the name could not be read; the UI falls back to
     *   a generic label.
     */
    data class Clip(val uri: String, val name: String) : ReminderSound
}

/**
 * The keyword stored for [ReminderSound.Silent].
 *
 * It cannot collide with a URI, so one stored string can express all three
 * choices: absent means the phone's default, this word means silence, and
 * anything else is the URI of an imported sound.
 */
private const val SILENT_KEYWORD = "silent"

/**
 * How to store [this] sound: null to store nothing (the default), the silence
 * keyword, or the URI of the imported copy.
 */
fun ReminderSound.storedUri(): String? = when (this) {
    ReminderSound.SystemDefault -> null
    ReminderSound.Silent -> SILENT_KEYWORD
    is ReminderSound.Clip -> uri
}

/** The file name to store alongside [this] sound, or null for the other two. */
fun ReminderSound.storedName(): String? =
    (this as? ReminderSound.Clip)?.name?.takeIf { it.isNotBlank() }

/**
 * Read a stored sound back.
 *
 * Storage is not user input, so nothing here is reported as an error: an
 * unreadable value falls back to the phone's default tone, which is a working
 * reminder rather than a broken one.
 */
fun parseStoredReminderSound(storedUri: String?, storedName: String?): ReminderSound = when {
    storedUri.isNullOrBlank() -> ReminderSound.SystemDefault
    storedUri == SILENT_KEYWORD -> ReminderSound.Silent
    else -> ReminderSound.Clip(uri = storedUri, name = storedName.orEmpty())
}
