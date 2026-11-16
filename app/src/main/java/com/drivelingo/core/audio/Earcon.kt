package com.drivelingo.core.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 수업 효과음(UX 기획서 4장 "효과음"). 말할 차례·멈춤·재개를 화면 없이 소리로 구분한다.
 *
 * 규칙: 모두 0.5초 이내, 내비 안내음과 다른 음색, 경고음처럼 날카로운 소리는 쓰지 않는다.
 */
sealed interface Earcon {
    val label: String

    /** 마이크가 열린다. 밝은 상승음 '띵'. */
    data object YourTurn : Earcon { override val label = "말할 차례 (띵)" }

    /** 답을 접수할 때마다 한 음씩 올라간다. 5단에서 더 오르지 않는다. */
    data class Combo(val step: Int) : Earcon {
        override val label get() = "콤보 ${clampedStep}단"
        val clampedStep: Int get() = step.coerceIn(1, MAX_STEP)

        companion object { const val MAX_STEP = 5 }
    }

    /** 안전 멈춤, 진행 상황 얼림. 낮은 하강 '톡'. */
    data object Pause : Earcon { override val label = "잠시 멈춤 (톡)" }

    /** 얼린 지점부터 다시 시작. '톡'의 역방향. */
    data object Resume : Earcon { override val label = "재개" }

    /** 정차 중 길게 말할 질문 시작. 부드러운 2음 '띵동'. */
    data object LongQuestion : Earcon { override val label = "긴 대답 질문 (띵동)" }

    /** 오늘 수업 끝. 3음 멜로디. */
    data object SessionComplete : Earcon { override val label = "세션 완료" }
}

/** 효과음 재생 경계. 코루틴이 취소되면 즉시 멈춰야 한다. */
interface EarconPlayer {
    suspend fun play(earcon: Earcon)
}

/**
 * 효과음을 PCM 으로 합성한다. 음원 파일 없이 코드로 만들어 음색·길이를 테스트로 고정한다.
 *
 * 음색: 기본음 + 약한 2·3배음(마림바 비슷한 부드러운 소리), 6ms 어택 후 지수 감쇠.
 * 감쇠를 노트 끝에서 -40dB 까지 내려 클릭 소리 없이 끝난다.
 */
object EarconSynth {

    const val SAMPLE_RATE = 22_050
    const val MAX_DURATION_MS = 500
    private const val PEAK = 0.45f
    private const val ATTACK_MS = 6
    private const val GAP_MS = 15
    private const val END_LEVEL = 0.01 // -40dB

    data class Tone(val frequencyHz: Double, val durationMs: Int, val gain: Float = 1f)

    /** 콤보 음계: C5 D5 E5 G5 A5 (5음 음계라 어느 단에서 멈춰도 어울린다). */
    private val COMBO_SCALE = doubleArrayOf(523.25, 587.33, 659.25, 783.99, 880.0)

    fun tones(earcon: Earcon): List<Tone> = when (earcon) {
        Earcon.YourTurn -> listOf(Tone(783.99, 70), Tone(1174.66, 150))
        is Earcon.Combo -> listOf(Tone(COMBO_SCALE[earcon.clampedStep - 1], 110))
        Earcon.Pause -> listOf(Tone(440.0, 70, 0.8f), Tone(293.66, 130, 0.8f))
        Earcon.Resume -> listOf(Tone(293.66, 70, 0.8f), Tone(440.0, 130, 0.8f))
        Earcon.LongQuestion -> listOf(Tone(659.25, 180), Tone(523.25, 260))
        Earcon.SessionComplete -> listOf(Tone(523.25, 120), Tone(659.25, 120), Tone(783.99, 220))
    }

    fun durationMs(earcon: Earcon): Int {
        val tones = tones(earcon)
        return tones.sumOf { it.durationMs } + GAP_MS * (tones.size - 1)
    }

    fun render(earcon: Earcon, sampleRate: Int = SAMPLE_RATE): ShortArray {
        val out = ArrayList<Short>(sampleRate * durationMs(earcon) / 1000 + 1)
        tones(earcon).forEachIndexed { i, tone ->
            if (i > 0) repeat(sampleRate * GAP_MS / 1000) { out.add(0) }
            renderTone(tone, sampleRate, out)
        }
        return out.toShortArray()
    }

    private fun renderTone(tone: Tone, sampleRate: Int, out: MutableList<Short>) {
        val n = sampleRate * tone.durationMs / 1000
        val attack = sampleRate * ATTACK_MS / 1000
        val decaySamples = (n - attack).coerceAtLeast(1)
        val decayRate = ln(END_LEVEL) / decaySamples // 감쇠 구간 끝에서 END_LEVEL
        for (k in 0 until n) {
            val t = k.toDouble() / sampleRate
            val w = 2 * PI * tone.frequencyHz * t
            val wave = sin(w) + 0.25 * sin(2 * w) + 0.08 * sin(3 * w)
            val env = if (k < attack) k.toDouble() / attack else exp(decayRate * (k - attack))
            // 마지막 샘플은 정확히 0 으로 끝내 클릭을 없앤다.
            val tail = if (k == n - 1) 0.0 else 1.0
            val v = wave / 1.33 * env * tail * PEAK * tone.gain * Short.MAX_VALUE
            out.add(v.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
    }
}
