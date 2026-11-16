package com.drivelingo.core.speech

/** TTS 로 한 번에 읽을 조각. [korean] 이면 한국어 음성, 아니면 영어 음성으로 읽는다. */
data class SpeechChunk(val text: String, val korean: Boolean)

/**
 * 코치 문장을 TTS 조각으로 나눈다. 순수 함수(SpeechChunkerTest).
 *
 * - 한국어 설명과 영어 문장이 섞이면("지금 표현은 … 뜻이에요. Shall we get started?") 문장마다 언어를 골라
 *   한국어는 한국어 음성으로, 영어는 영어 음성으로 읽는다. 같은 언어 문장은 이어 붙여 TTS 호출을 줄인다.
 * - [byClause] 면 쉼표에서도 나눈다. Pleos TTS 에 말 속도 API 가 없어 "천천히"는 조각 사이 쉼으로 대신한다.
 */
object SpeechChunker {

    private val SENTENCE_END = Regex("(?<=[.!?])\\s+")
    private val CLAUSE_END = Regex("(?<=[,;:])\\s+")

    fun chunks(text: String, byClause: Boolean = false): List<SpeechChunk> {
        val sentences = text.trim().split(SENTENCE_END).filter { it.isNotBlank() }
        val pieces = if (byClause) sentences.flatMap { it.split(CLAUSE_END) }.filter { it.isNotBlank() } else sentences
        val out = mutableListOf<SpeechChunk>()
        for (piece in pieces) {
            val korean = piece.any { it in '가'..'힣' }
            val last = out.lastOrNull()
            if (!byClause && last != null && last.korean == korean) {
                out[out.lastIndex] = last.copy(text = last.text + " " + piece.trim())
            } else {
                out += SpeechChunk(piece.trim(), korean)
            }
        }
        return out
    }

    /** 느리게 말할 때 조각 사이 쉼(ms). 기본 속도면 0. */
    fun pauseBetweenMs(rate: Float): Long = if (rate >= 0.99f) 0L else ((1f / rate - 1f) * 900f).toLong()
}
