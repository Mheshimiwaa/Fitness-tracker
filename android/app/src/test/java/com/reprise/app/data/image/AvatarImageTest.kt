package com.reprise.app.data.image

import android.media.ExifInterface
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the arithmetic behind [AvatarImage].
 *
 * This is the part of the photo feature that can be tested exactly, and it is
 * the part most likely to be quietly wrong: a rounding mistake here does not
 * crash anything, it just uploads a blurrier or a needlessly large picture.
 * Reading and encoding the image needs a device (and a real picture), so
 * [AvatarImage.toJpeg] is exercised by hand instead.
 */
class AvatarImageTest {

    // -----------------------------------------------------------------------
    // Output size
    // -----------------------------------------------------------------------

    @Test
    fun `a landscape photo is capped on its longest edge and keeps its shape`() {
        val size = scaledSize(width = 4000, height = 3000, maxEdge = 512)

        assertEquals(512, size.width)
        assertEquals(384, size.height)
    }

    @Test
    fun `a portrait photo is capped on its longest edge too`() {
        val size = scaledSize(width = 3000, height = 4000, maxEdge = 512)

        assertEquals(384, size.width)
        assertEquals(512, size.height)
    }

    @Test
    fun `a square photo becomes a square of the maximum edge`() {
        assertEquals(AvatarSize(512, 512), scaledSize(1000, 1000, 512))
    }

    @Test
    fun `a photo that is already small enough is left exactly as it is`() {
        // Scaling UP would only make an avatar blurrier, so it must not happen.
        assertEquals(AvatarSize(100, 50), scaledSize(100, 50, 512))
        assertEquals(AvatarSize(512, 512), scaledSize(512, 512, 512))
    }

    @Test
    fun `a nonsensical size does not produce a zero-pixel image`() {
        // A 1-pixel-tall panorama would round its height to 0; a bitmap of zero
        // height cannot be encoded at all.
        val panoramic = scaledSize(width = 5000, height = 1, maxEdge = 512)
        assertEquals(512, panoramic.width)
        assertEquals(1, panoramic.height)

        assertEquals(AvatarSize(1, 1), scaledSize(0, 0, 512))
        assertEquals(AvatarSize(1, 1), scaledSize(-10, 20, 512))
    }

    @Test
    fun `the longest edge ends up exactly at the limit, never above it`() {
        val originals = listOf(4000 to 3000, 3000 to 4000, 5000 to 5000, 700 to 900, 512 to 1024)

        for ((width, height) in originals) {
            val size = scaledSize(width, height, maxEdge = AvatarImage.MAX_EDGE)

            assertTrue(
                "expected $width x $height to fit within ${AvatarImage.MAX_EDGE}, got $size",
                maxOf(size.width, size.height) <= AvatarImage.MAX_EDGE,
            )
        }
    }

    // -----------------------------------------------------------------------
    // Decode size
    // -----------------------------------------------------------------------

    @Test
    fun `decoding is shrunk by powers of two without dropping below the target`() {
        assertEquals(4, inSampleSize(4000, 3000, 512))
        assertEquals(2, inSampleSize(1024, 1024, 512))
        // 1000 / 2 = 500, which is BELOW 512 — so no sampling, and one step is
        // not worth taking here.
        assertEquals(1, inSampleSize(1000, 1000, 512))
        assertEquals(1, inSampleSize(100, 100, 512))
        // 4096 / 8 = 512 exactly, which is still enough.
        assertEquals(8, inSampleSize(4096, 4096, 512))
    }

    @Test
    fun `an image is never decoded smaller than the limit`() {
        // The exact-scaling step afterwards can only shrink, so if sampling went
        // too far the uploaded picture would be softer than it needs to be.
        val originals = listOf(4000 to 3000, 8000 to 6000, 1024 to 1024, 4096 to 500, 40 to 30)

        for ((width, height) in originals) {
            val sample = inSampleSize(width, height, AvatarImage.MAX_EDGE)
            val decodedWidth = width / sample
            val decodedHeight = height / sample

            assertTrue(
                "expected $width x $height sampled by $sample to stay at least " +
                    "${AvatarImage.MAX_EDGE}px, got $decodedWidth x $decodedHeight",
                maxOf(decodedWidth, decodedHeight) >= AvatarImage.MAX_EDGE ||
                    maxOf(decodedWidth, decodedHeight) == maxOf(width, height),
            )
        }
    }

    @Test
    fun `sampling then scaling lands exactly on the limit`() {
        // The two steps have to agree: BitmapFactory only understands powers of
        // two, so `scaledSize` finishes the job. Together they must reach the
        // limit exactly for any photo larger than it.
        val originals = listOf(4000 to 3000, 3000 to 4000, 5000 to 5000, 4096 to 500, 700 to 900)

        for ((width, height) in originals) {
            val sample = inSampleSize(width, height, AvatarImage.MAX_EDGE)
            val decoded = AvatarSize(width / sample, height / sample)
            val final = scaledSize(decoded.width, decoded.height, AvatarImage.MAX_EDGE)

            assertEquals(
                "expected $width x $height to end up exactly at the limit, got $final",
                AvatarImage.MAX_EDGE,
                maxOf(final.width, final.height),
            )
        }
    }

    // -----------------------------------------------------------------------
    // EXIF rotation
    // -----------------------------------------------------------------------

    @Test
    fun `every EXIF orientation maps to the rotation that makes it upright`() {
        assertEquals(0, rotationDegreesFor(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(0, rotationDegreesFor(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertEquals(180, rotationDegreesFor(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(180, rotationDegreesFor(ExifInterface.ORIENTATION_FLIP_VERTICAL))
        assertEquals(90, rotationDegreesFor(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(90, rotationDegreesFor(ExifInterface.ORIENTATION_TRANSPOSE))
        assertEquals(270, rotationDegreesFor(ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals(270, rotationDegreesFor(ExifInterface.ORIENTATION_TRANSVERSE))
    }

    @Test
    fun `an unknown orientation is treated as upright`() {
        assertEquals(0, rotationDegreesFor(0))
        assertEquals(0, rotationDegreesFor(99))
    }

    // -----------------------------------------------------------------------
    // The request body
    // -----------------------------------------------------------------------

    @Test
    fun `the upload body is the jpeg itself, labelled as a jpeg`() {
        val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0x01, 0x02)

        val body = AvatarImage.requestBody(bytes)

        // The server sniffs the bytes for real, but the header still has to be an
        // image type or the raw parser will refuse to read the body at all.
        assertEquals("image/jpeg", body.contentType().toString())
        assertEquals(bytes.size.toLong(), body.contentLength())

        // And the body carries the bytes unchanged — not re-encoded, not wrapped.
        val buffer = Buffer()
        body.writeTo(buffer)
        assertArrayEquals(bytes, buffer.readByteArray())
    }
}
