package com.drivelingo.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EarconSynthTest {

    private val all = listOf(
        Earcon.YourTurn, Earcon.Pause, Earcon.Resume, Earcon.LongQuestion, Earcon.SessionComplete,
    ) + (1..Earcon.Combo.MAX_STEP).map { Earcon.Combo(it) }

    @Test
    fun `every earcon is at most half a second`() {
        all.forEach { assertTrue("$it", EarconSynth.durationMs(it) <= EarconSynth.MAX_DURATION_MS) }
    }

    @Test
    fun `rendered length matches the declared duration`() {
        all.forEach {
            val expected = EarconSynth.SAMPLE_RATE * EarconSynth.durationMs(it) / 1000
            assertTrue("$it", abs(EarconSynth.render(it).size - expected) <= EarconSynth.tones(it).size)
        }
    }

    @Test
    fun `combo rises one note per step and stops at five`() {
        val pitches = (1..Earcon.Combo.MAX_STEP).map { EarconSynth.tones(Earcon.Combo(it)).single().frequencyHz }
        assertEquals(pitches.sorted(), pitches)
        assertEquals(pitches.size, pitches.toSet().size)
        assertEquals(EarconSynth.tones(Earcon.Combo(5)), EarconSynth.tones(Earcon.Combo(9)))
    }

    @Test
    fun `resume is the reverse of pause`() {
        val pause = EarconSynth.tones(Earcon.Pause).map { it.frequencyHz }
        val resume = EarconSynth.tones(Earcon.Resume).map { it.frequencyHz }
        assertEquals(pause.reversed(), resume)
        assertTrue(pause.first() > pause.last()) // 멈춤은 내려가고 재개는 올라간다
    }

    @Test
    fun `soft level without clipping and silent edges so there is no click`() {
        all.forEach { earcon ->
            val pcm = EarconSynth.render(earcon)
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("$earcon peak $peak", peak < Short.MAX_VALUE * 0.5)
            assertEquals(0, pcm.first().toInt())
            assertEquals(0, pcm.last().toInt())
        }
    }
}
