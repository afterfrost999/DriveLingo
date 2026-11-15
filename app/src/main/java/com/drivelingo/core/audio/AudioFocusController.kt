package com.drivelingo.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.drivelingo.core.vehicle.AudioFocus
import com.drivelingo.core.vehicle.AudioFocusRequester
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** 오디오 쪽에서 본 차량 환경: 내 포커스 상태와 다른 앱의 내비 안내·통화. */
data class AudioEnvironment(
    val focus: AudioFocus = AudioFocus.GAINED,
    val navGuidanceActive: Boolean = false,
    val phoneCallActive: Boolean = false,
)

/**
 * 오디오 포커스·재생 상태 → 앱 모델. 순수 함수라 단위 테스트한다.
 * (AudioManager·AudioAttributes 상수는 컴파일 시 숫자로 들어가므로 JVM 테스트에서도 쓸 수 있다.)
 */
object AudioEnvironmentRules {

    /** OnAudioFocusChangeListener 값 → 앱 모델. 모르는 값은 null(무시). */
    fun focusFromChange(change: Int): AudioFocus? = when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> AudioFocus.GAINED
        AudioManager.AUDIOFOCUS_LOSS -> AudioFocus.LOST
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> AudioFocus.LOST_TRANSIENT
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AudioFocus.LOST_TRANSIENT_CAN_DUCK
        else -> null
    }

    /**
     * requestAudioFocus 결과 → 앱 모델.
     * DELAYED(통화 중 등) 는 나중에 GAIN 이 오므로 일시 상실로 두고, FAILED 는 사용자가 다시 재생할 때까지 멈춘다.
     */
    fun focusFromRequest(result: Int): AudioFocus = when (result) {
        AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> AudioFocus.GAINED
        AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> AudioFocus.LOST_TRANSIENT
        else -> AudioFocus.LOST
    }

    /**
     * 내비 음성 안내 중인지. Pleos 문서에 "안내 시작/끝" 이벤트가 없어서, 지금 재생 중인 소리 중
     * 내비 안내 용도(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)가 있는지로 판단한다.
     * AAOS 는 내비와 미디어를 겹쳐 재생(덕킹)하며 포커스 상실을 알리지 않는 경우가 있어 포커스만으로는 알 수 없다.
     */
    fun navGuidanceActive(activeUsages: Collection<Int>): Boolean =
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE in activeUsages

    /** 전화(수신 벨·통화) 중인지. 오디오 모드와 재생 용도를 함께 본다. */
    fun phoneCallActive(activeUsages: Collection<Int>, audioMode: Int): Boolean =
        audioMode == AudioManager.MODE_RINGTONE ||
            audioMode == AudioManager.MODE_IN_CALL ||
            audioMode == AudioManager.MODE_IN_COMMUNICATION ||
            AudioAttributes.USAGE_VOICE_COMMUNICATION in activeUsages ||
            AudioAttributes.USAGE_NOTIFICATION_RINGTONE in activeUsages
}

/**
 * 실제 오디오 포커스 연동 (Android AudioManager + Pleos 오디오 포커스 정책).
 *
 * - 수업은 재생 전에 [requestAudioFocus] 로 `AUDIOFOCUS_GAIN` 을 요청한다(USAGE_MEDIA, CONTENT_TYPE_SPEECH).
 * - 포커스를 잃으면 [environment] 의 focus 로 알리고, 수업 엔진이 중단 사유로 처리한다.
 *   영구 상실(LOSS)은 자동 재개하지 않고 사용자가 재생을 누를 때 다시 요청한다(Music/Audio 자동 재생 금지).
 * - 덕킹 상태로 겹쳐 들리면 말하기 수업은 알아듣기 어려우므로 `setWillPauseWhenDucked(true)` 로 직접 멈춘다.
 * - 내비 안내·전화는 [AudioEnvironmentRules] 로 재생 상태와 오디오 모드에서 판단한다.
 */
class AudioFocusController(context: Context) : AudioFocusRequester {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val focus = MutableStateFlow(AudioFocus.GAINED)
    private val activeUsages = MutableStateFlow<Set<Int>>(emptySet())
    private val audioMode = MutableStateFlow(audioManager.mode)

    /** 포커스를 갖고 있는(또는 기다리는) 중인지. 놓은 뒤 늦게 온 콜백은 무시한다. */
    @Volatile private var holding = false

    val environment: Flow<AudioEnvironment> = combine(focus, activeUsages, audioMode) { f, usages, mode ->
        AudioEnvironment(
            focus = f,
            navGuidanceActive = AudioEnvironmentRules.navGuidanceActive(usages),
            phoneCallActive = AudioEnvironmentRules.phoneCallActive(usages, mode),
        )
    }.distinctUntilChanged()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (!holding) return@OnAudioFocusChangeListener
        val next = AudioEnvironmentRules.focusFromChange(change) ?: return@OnAudioFocusChangeListener
        Log.i(TAG, "focus change $change → $next")
        focus.value = next
        audioMode.value = audioManager.mode
    }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(LESSON_ATTRIBUTES)
        .setAcceptsDelayedFocusGain(true)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(focusListener, handler)
        .build()

    init {
        audioManager.registerAudioPlaybackCallback(object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                updatePlayback(configs)
            }
        }, handler)
        updatePlayback(audioManager.activePlaybackConfigurations)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.addOnModeChangedListener(context.mainExecutor) { mode ->
                Log.i(TAG, "audio mode $mode")
                audioMode.value = mode
            }
        }
    }

    private fun updatePlayback(configs: List<AudioPlaybackConfiguration>) {
        activeUsages.value = configs.map { it.audioAttributes.usage }.toSet()
        // API 31 미만에는 모드 변경 리스너가 없어서 재생 상태가 바뀔 때 같이 읽는다.
        audioMode.value = audioManager.mode
    }

    override fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(focusRequest)
        val next = AudioEnvironmentRules.focusFromRequest(result)
        holding = next != AudioFocus.LOST
        Log.i(TAG, "request focus result=$result → $next")
        focus.value = next
        return next == AudioFocus.GAINED
    }

    override fun abandonAudioFocus() {
        holding = false
        audioManager.abandonAudioFocusRequest(focusRequest)
        // 포커스를 놓은 상태는 "중단 사유 없음"이다. 다음 수업이 이전 상실 상태로 시작하지 않게 한다.
        focus.value = AudioFocus.GAINED
    }

    private companion object {
        const val TAG = "AudioFocus"
        val LESSON_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
