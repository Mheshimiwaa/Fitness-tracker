package com.reprise.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The reminder wheel's centring arithmetic.
 *
 * This is the one part of the picker that can be wrong in a way the user would
 * notice but the compiler would not ("my 23:59 fires at 00:59"), so it is kept
 * as a plain function and pinned down here — no device, no Compose UI.
 */
class ReminderWheelTest {

    private companion object {
        const val ROW = 40
        const val PADDING_ROWS = 2

        /** Five rows of 40px put the visible area's midpoint at 100px. */
        const val VIEWPORT_CENTRE = 100

        /**
         * The rows a wheel shows with [centrePosition] in its middle row.
         *
         * [shift] is how far the rows have slid: 0 is settled, 40 is exactly one
         * row on, and values between the two are mid-flick.
         */
        fun rowsAround(centrePosition: Int, shift: Int = 0): List<WheelRowBounds> =
            List(5) { i ->
                WheelRowBounds(
                    index = centrePosition - 2 + i,
                    offset = shift + i * ROW,
                    size = ROW,
                )
            }
    }

    @Test
    fun `the first value sits in the middle at the top of the wheel`() {
        // 00:00 opens with value 0 centred, two padding rows above it.
        assertEquals(
            0,
            centeredWheelValue(
                rows = rowsAround(centrePosition = PADDING_ROWS),
                viewportCentre = VIEWPORT_CENTRE,
                paddingRows = PADDING_ROWS,
                valueCount = 24,
            ),
        )
    }

    @Test
    fun `the last hour can be centred`() {
        // 23 must be reachable, which is what the trailing padding rows are for.
        assertEquals(
            23,
            centeredWheelValue(
                rows = rowsAround(centrePosition = 23 + PADDING_ROWS),
                viewportCentre = VIEWPORT_CENTRE,
                paddingRows = PADDING_ROWS,
                valueCount = 24,
            ),
        )
    }

    @Test
    fun `the last minute can be centred`() {
        assertEquals(
            59,
            centeredWheelValue(
                rows = rowsAround(centrePosition = 59 + PADDING_ROWS),
                viewportCentre = VIEWPORT_CENTRE,
                paddingRows = PADDING_ROWS,
                valueCount = 60,
            ),
        )
    }

    @Test
    fun `a value in the middle of the wheel is read back unchanged`() {
        assertEquals(
            12,
            centeredWheelValue(
                rows = rowsAround(centrePosition = 12 + PADDING_ROWS),
                viewportCentre = VIEWPORT_CENTRE,
                paddingRows = PADDING_ROWS,
                valueCount = 60,
            ),
        )
    }

    @Test
    fun `a flick past the halfway point reads the row that is closer`() {
        // Rows slid 21px of a 40px row, so value 6 is no longer the middle one:
        // the value above it (5) is now nearer.
        assertEquals(
            5,
            centeredWheelValue(
                rows = rowsAround(centrePosition = 6 + PADDING_ROWS, shift = 21),
                viewportCentre = VIEWPORT_CENTRE,
                paddingRows = PADDING_ROWS,
                valueCount = 60,
            ),
        )
    }

    @Test
    fun `nothing is reported before the wheel has been laid out`() {
        assertNull(
            centeredWheelValue(
                rows = emptyList(),
                viewportCentre = VIEWPORT_CENTRE,
                paddingRows = PADDING_ROWS,
                valueCount = 60,
            ),
        )
    }

    @Test
    fun `padding on its own is not a value`() {
        // Only rows 0 and 1 exist and row 1 is nearest the middle; padding is not
        // a number the user picked, so nothing is reported.
        assertNull(
            centeredWheelValue(
                rows = listOf(
                    WheelRowBounds(index = 0, offset = 0, size = ROW),
                    WheelRowBounds(index = 1, offset = ROW, size = ROW),
                ),
                viewportCentre = VIEWPORT_CENTRE,
                paddingRows = PADDING_ROWS,
                valueCount = 60,
            ),
        )
    }
}
