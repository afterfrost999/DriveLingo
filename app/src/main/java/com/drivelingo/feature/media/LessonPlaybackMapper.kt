package com.drivelingo.feature.media

import android.support.v4.media.session.PlaybackStateCompat
import com.drivelingo.core.driving.Interruption
import com.drivelingo.feature.lesson.engine.LessonPhase
import com.drivelingo.feature.lesson.engine.LessonState

/** 미디어 세션에 내보낼 재생 상태. Android 객체 없이 만들 수 있어 단위 테스트한다. */
data class LessonPlayback(
    /** PlaybackStateCompat.STATE_* */
    val state: Int,
    /** PlaybackStateCompat.ACTION_* 조합 */
    val actions: Long,
    val title: String,
    /** 차량 미디어 화면에 보이는 한 줄. 주행 중에도 보이므로 학습 문장·점수·숫자는 넣지 않는다. */
    val subtitle: String,
)

/**
 * 수업 상태 → 미디어 세션 재생 상태.
 *
 * - 수업 없음/끝남: 정지. 재생(새 수업 시작)만 할 수 있다.
 * - 멈춤(사용자 일시정지·중단 이벤트): 일시정지. 재생(재개)·정지.
 * - 그 밖: 재생 중. 일시정지·다시 듣기(이전)·건너뛰기(다음)·정지.
 */
object LessonPlaybackMapper {

    const val IDLE_TITLE = "말차 영어 수업"

    private const val START_ACTIONS =
        PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID

    fun map(state: LessonState?): LessonPlayback {
        if (state == null || state.isTerminal) {
            return LessonPlayback(
                state = PlaybackStateCompat.STATE_STOPPED,
                actions = START_ACTIONS,
                title = state?.plan?.title ?: IDLE_TITLE,
                subtitle = when (state?.phase) {
                    LessonPhase.FINISHED -> "오늘 수업 완료"
                    LessonPhase.STOPPED -> "멈춘 곳부터 이어서 할 수 있어요"
                    else -> "재생을 누르면 수업을 시작해요"
                },
            )
        }
        val paused = state.phase == LessonPhase.PAUSED
        return LessonPlayback(
            state = if (paused) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING,
            actions = if (paused) {
                START_ACTIONS or PlaybackStateCompat.ACTION_STOP
            } else {
                PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_STOP
            },
            title = state.plan.title,
            subtitle = subtitle(state),
        )
    }

    private fun subtitle(state: LessonState): String = when (state.phase) {
        LessonPhase.PAUSED -> {
            val reasons = state.signalInterruptions + if (state.userPaused) setOf(Interruption.USER_PAUSE) else emptySet()
            "잠시 멈춤 · " + (Interruption.primary(reasons)?.label ?: "곧 이어서 해요")
        }
        LessonPhase.ANSWER -> "말할 차례"
        LessonPhase.THINKING -> "생각 중"
        LessonPhase.QUIET -> "조용히 기다리는 중"
        LessonPhase.READY -> "준비 중"
        else -> "듣는 중"
    }
}
