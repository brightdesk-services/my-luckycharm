package com.brightdesk.myluckycharm.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapping, not the framework lookup — [BrightnessRange.ofSystem] needs a
 * real device. These pin the behaviour that a hardcoded 0..255 got wrong on
 * every phone using a different scale.
 */
class BrightnessRangeTest {

    @Test
    fun `a full pull reaches the top of whatever scale the device uses`() {
        for (max in listOf(255, 1023, 2047, 4095)) {
            val range = BrightnessRange(min = (max * 0.03f).toInt(), max = max)
            assertEquals("scale of $max", max, range.valueFor(1f))
        }
    }

    @Test
    fun `no pull leaves the screen dim but never black`() {
        val range = BrightnessRange(min = 30, max = 1023)
        assertEquals(30, range.valueFor(0f))
        assertTrue("must stay visible", range.valueFor(0f) > 0)
    }

    @Test
    fun `half a pull lands halfway up the usable range`() {
        val range = BrightnessRange(min = 100, max = 1100)
        assertEquals(600, range.valueFor(0.5f))
    }

    @Test
    fun `fractions outside zero to one are clamped, not extrapolated`() {
        val range = BrightnessRange(min = 8, max = 255)
        assertEquals(8, range.valueFor(-5f))
        assertEquals(255, range.valueFor(9f))
    }

    @Test
    fun `a degenerate range cannot produce an out of bounds value`() {
        val range = BrightnessRange(min = 10, max = 10)
        assertEquals(10, range.valueFor(0f))
        assertEquals(10, range.valueFor(1f))
    }
}
