package com.reprise.app.data.image

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a picture the user picked — from the gallery, or from the camera — into
 * the small JPEG that is uploaded as their profile photo.
 *
 * Why re-encode instead of uploading what was picked?
 *   - A phone camera produces a several-megabyte, 4000-pixel-wide file, and an
 *     avatar is drawn at about 72dp. Sending the original would spend the user's
 *     data and the server's disk on detail nothing ever displays.
 *   - Re-encoding guarantees the server receives a real JPEG whatever the source
 *     was (PNG, WebP, HEIC...), so an upload never depends on the gallery's
 *     format or on an extension being truthful.
 *   - It also DROPS the EXIF metadata, which can carry the location the photo was
 *     taken at. The one piece of EXIF that is read — the orientation — is applied
 *     to the pixels first, so a portrait photo does not arrive sideways.
 *
 * The arithmetic that decides the output size is kept in plain functions
 * ([scaledSize], [inSampleSize], [rotationDegreesFor]) because that is the part
 * worth testing, and it can be tested without a device or an image file.
 */
object AvatarImage {

    /** The longest edge of the uploaded image, in pixels. */
    const val MAX_EDGE = 512

    /** How the bytes are sent, and the only image type the app uploads. */
    const val CONTENT_TYPE = "image/jpeg"

    /** JPEG quality. 85 is the usual "indistinguishable but much smaller" point. */
    private const val QUALITY = 85

    /**
     * Read [uri] and return a JPEG, scaled to at most [maxEdge] on its longest
     * edge, or null when the picture cannot be read or decoded at all.
     *
     * Runs on the IO dispatcher: decoding several megapixels is far too much work
     * for the main thread.
     */
    suspend fun toJpeg(context: Context, uri: Uri, maxEdge: Int = MAX_EDGE): ByteArray? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver

            // Bounds-only pass: it allocates no pixels and tells us how much to
            // shrink by. Done BEFORE decoding, so a 50-megapixel photo never has
            // to fit in memory at full size.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = inSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null

            // These bitmaps are never displayed; they exist only to be encoded,
            // so each one is released as soon as it is no longer needed instead
            // of waiting for the next GC. Uploading several photos in a row
            // should not grow the heap.
            var scaled: Bitmap? = null
            var upright: Bitmap? = null
            try {
                val target = scaledSize(decoded.width, decoded.height, maxEdge)
                scaled = if (target.width == decoded.width && target.height == decoded.height) {
                    decoded
                } else {
                    Bitmap.createScaledBitmap(decoded, target.width, target.height, true)
                }

                upright = rotate(scaled, rotationDegreesFor(readExifOrientation(resolver, uri)))

                val bytes = ByteArrayOutputStream().use { output ->
                    upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)
                    output.toByteArray()
                }

                // An encoder that produced nothing has not produced a photo.
                if (bytes.isEmpty()) null else bytes
            } finally {
                if (upright != null && upright !== scaled) upright.recycle()
                if (scaled != null && scaled !== decoded) scaled.recycle()
                decoded.recycle()
            }
        }

    /**
     * Wrap the bytes as the request body the API expects: the image itself, with
     * an `image/jpeg` content type (never a multipart form — see the backend's
     * photo service for why).
     */
    fun requestBody(jpegBytes: ByteArray): RequestBody =
        jpegBytes.toRequestBody(CONTENT_TYPE.toMediaType())

    /** The EXIF orientation of [uri], or [EXIF_NORMAL] when there is none. */
    private fun readExifOrientation(resolver: ContentResolver, uri: Uri): Int {
        return try {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    EXIF_NORMAL,
                )
            } ?: EXIF_NORMAL
        } catch (_: Exception) {
            // A picture with no EXIF at all (a screenshot, a PNG) is upright.
            EXIF_NORMAL
        }
    }

    /** [bitmap] turned clockwise by [degrees]. Zero returns it untouched. */
    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap

        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

/** EXIF orientation 1: the picture is already the right way up. */
internal const val EXIF_NORMAL = 1

/**
 * The size [width] x [height] should be shrunk to so its LONGEST edge is at most
 * [maxEdge], keeping the aspect ratio.
 *
 * A picture already small enough is returned unchanged — an avatar is never
 * scaled UP, because that would only make it blurrier.
 */
fun scaledSize(width: Int, height: Int, maxEdge: Int): AvatarSize {
    if (width <= 0 || height <= 0) return AvatarSize(1, 1)
    if (maxEdge <= 0) return AvatarSize(width, height)

    val longest = max(width, height)
    if (longest <= maxEdge) return AvatarSize(width, height)

    val scale = maxEdge.toDouble() / longest
    return AvatarSize(
        width = (width * scale).roundToInt().coerceAtLeast(1),
        height = (height * scale).roundToInt().coerceAtLeast(1),
    )
}

/**
 * The `inSampleSize` to decode with: the largest power of two that still leaves
 * the longest edge at or above [maxEdge].
 *
 * `BitmapFactory` only understands powers of two here, and decoding smaller is
 * what keeps a big photo from being loaded into memory at full size. [scaledSize]
 * then makes up the difference exactly, so the result is not needlessly tiny.
 */
fun inSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    if (maxEdge <= 0) return 1

    var sample = 1
    while (max(width, height) / (sample * 2) >= maxEdge) {
        sample *= 2
    }
    return sample
}

/**
 * The clockwise rotation, in degrees, that makes a picture with this EXIF
 * orientation upright.
 *
 * The four mirrored orientations (which some front cameras write) are treated as
 * their rotation equivalent: their mirroring is dropped, because an avatar is a
 * circle and a mirrored face in a circle is not something anyone notices. Their
 * ROTATION, on the other hand, is very noticeable, which is why this exists.
 */
fun rotationDegreesFor(exifOrientation: Int): Int = when (exifOrientation) {
    ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
    ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
    ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
    else -> 0
}

/** A width/height pair, in pixels. */
data class AvatarSize(val width: Int, val height: Int)
