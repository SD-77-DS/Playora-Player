package com.playora.app

import com.playora.app.playback.EqualizerManager
import com.playora.app.util.formatDuration
import com.playora.app.util.formatSize
import org.junit.Assert.assertEquals
import org.junit.Test

class UtilTest {
    @Test fun durationFormatting() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("1:01:01", formatDuration(3_661_000))
    }

    @Test fun sizeFormatting() {
        assertEquals("512 B", formatSize(512))
        assertEquals("2.0 MB", formatSize(2L shl 20))
    }

    @Test fun presetCurveIsInterpolatedOverAnyBandCount() {
        val flat = EqualizerManager.curveToLevels(floatArrayOf(0f, 0f, 0f, 0f, 0f), 5, 1500)
        assertEquals(List(5) { 0 }, flat)
        val bass = EqualizerManager.curveToLevels(floatArrayOf(1f, 0f, 0f, 0f, 0f), 10, 1500)
        assertEquals(10, bass.size)
        assertEquals(1500, bass.first())
        assertEquals(0, bass.last())
    }
}
