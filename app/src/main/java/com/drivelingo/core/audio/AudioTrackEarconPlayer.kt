package com.drivelingo.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.drivelingo.core.speech.VoiceSimulation
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * 효과음을 실제로 재생한다(`voiceSource=pleos`).
 *
 * 용도는 수업 음성과 같은 `USAGE_MEDIA` 로 둔다. 앱이 가진 오디오 포커스 안에서 같은 볼륨 그룹으로 나가야
 * 코치 목소리와 크기가 맞고, 내비 안내·전화가 오면 함께 멈추기 때문이다.
 */
class AudioTrackEarconPlayer(private val monitor: VoiceSimulation? = null) : EarconPlayer {

    private val cache = ConcurrentHashMap<Earcon, ShortArray>()

    override suspend fun play(earcon: Earcon) {
        monitor?.setEarcon(earcon.label)
        val samples = cache.getOrPut(earcon) { EarconSynth.render(earcon) }
        val track = runCatching { build(samples) }.getOrElse {
            Log.w(TAG, "earcon track failed", it)
            return
        }
        try {
            track.play()
            delay(EarconSynth.durationMs(earcon).toLong() + TAIL_MS)
        } finally {
            // 취소(즉시 중단)되면 여기서 바로 멈춘다.
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun build(samples: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(EarconSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    private companion object {
        const val TAG = "Earcon"
        const val TAIL_MS = 30L
    }
}
