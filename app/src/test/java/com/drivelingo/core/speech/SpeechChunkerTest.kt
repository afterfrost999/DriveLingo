package com.drivelingo.core.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechChunkerTest {

    @Test
    fun `korean explanation and english prompt are read with their own voices`() {
        val chunks = SpeechChunker.chunks("지금 표현은 우리말로 '시작할까요'라는 뜻이에요. 다시 들어 볼게요. Repeat after me. Shall we get started?")
        assertEquals(
            listOf(
                SpeechChunk("지금 표현은 우리말로 '시작할까요'라는 뜻이에요. 다시 들어 볼게요.", korean = true),
                SpeechChunk("Repeat after me. Shall we get started?", korean = false),
            ),
            chunks,
        )
    }

    @Test
    fun `plain english stays one chunk`() {
        assertEquals(listOf(SpeechChunk("Okay, next one. How was your weekend?", false)), SpeechChunker.chunks("Okay, next one. How was your weekend?"))
    }

    @Test
    fun `slow speech splits at clauses and pauses between them`() {
        val chunks = SpeechChunker.chunks("Sure, I'll slow down. Thanks for coming, everyone.", byClause = true)
        assertEquals(listOf("Sure,", "I'll slow down.", "Thanks for coming,", "everyone."), chunks.map { it.text })
        assertEquals(0L, SpeechChunker.pauseBetweenMs(1f))
        assertEquals(true, SpeechChunker.pauseBetweenMs(0.7f) > SpeechChunker.pauseBetweenMs(0.85f))
    }
}
