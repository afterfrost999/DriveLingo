package com.drivelingo.feature.lesson

import com.drivelingo.core.driving.DrivingSituation
import com.drivelingo.core.driving.DrivingStatus
import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.vehicle.UxRestrictions
import com.drivelingo.feature.lesson.engine.ActivityType
import com.drivelingo.feature.lesson.engine.LessonPhase
import com.drivelingo.feature.lesson.engine.LessonState

/** 화면에 그릴 중앙 음성 상태. 시안의 4가지 상태 + 보조 상태. */
enum class VoiceVisual { LISTENING, SPEAK_TURN, LONG_SPEAK_TURN, THINKING, PAUSED, QUIET, FINISHED }

/** 주차 중에만 보여 주는 글자 정보. 주행 중에는 음성 전사(transcription)를 화면에 띄우지 않는다. */
data class LessonDetail(
    val expression: String,
    val meaning: String,
    val prompt: String,
    val lastAnswer: String?,
    val feedback: String?,
)

data class LessonUiState(
    val topic: String,
    val visual: VoiceVisual,
    val headline: String,
    val caption: String?,
    val badge: String?,
    val progress: Float,
    val isPaused: Boolean,
    val canTogglePause: Boolean,
    val canRepeat: Boolean,
    val offline: Boolean,
    val detail: LessonDetail?,
    val finished: Boolean,
    val stopped: Boolean,
)

/** 엔진 상태 + 운전 상황 → 화면 상태. 순수 함수. */
object LessonUiMapper {

    fun map(s: LessonState, driving: DrivingStatus): LessonUiState {
        val turn = s.turn
        val primary = Interruption.primary(s.activeInterruptions)

        val (visual, headline, caption) = when (s.phase) {
            LessonPhase.READY -> Triple(VoiceVisual.LISTENING, "준비 중", null)
            LessonPhase.PROMPT, LessonPhase.FEEDBACK, LessonPhase.CLOSING -> Triple(VoiceVisual.LISTENING, "듣는 중", null)
            LessonPhase.ANSWER -> when (turn?.activity) {
                ActivityType.LONG_ANSWER ->
                    Triple(VoiceVisual.LONG_SPEAK_TURN, "길게 말해 보세요", "시간 제한 없어요 · 출발해도 끝까지 들을게요")
                ActivityType.REPEAT -> Triple(VoiceVisual.SPEAK_TURN, "말할 차례", "들은 문장을 그대로 따라 말해요")
                ActivityType.DETAILED_REVIEW -> Triple(VoiceVisual.SPEAK_TURN, "말할 차례", "천천히 대답해 보세요")
                else -> Triple(VoiceVisual.SPEAK_TURN, "말할 차례", "짧게 대답해도 충분해요")
            }
            LessonPhase.THINKING -> Triple(VoiceVisual.THINKING, "잠깐만요", "대답을 확인하고 있어요")
            LessonPhase.PAUSED -> Triple(VoiceVisual.PAUSED, "잠시 멈춤", pausedCaption(primary))
            LessonPhase.QUIET -> Triple(VoiceVisual.QUIET, "운전에 집중할 시간", quietCaption(driving.situation))
            LessonPhase.FINISHED -> Triple(VoiceVisual.FINISHED, "수업 완료", "도착하면 리포트를 보여드릴게요")
            LessonPhase.STOPPED -> Triple(VoiceVisual.PAUSED, "수업을 멈췄어요", "다음 운행에서 이어서 해요")
        }

        val badge = when {
            s.phase == LessonPhase.ANSWER && turn?.activity == ActivityType.LONG_ANSWER -> "정차 질문 · 신호 대기"
            driving.situation == DrivingSituation.UNKNOWN && driving.uiRestricted -> "안전 제한 모드 · 듣기만 해요"
            else -> null
        }

        val showText = !driving.uiRestricted && !driving.restricts(UxRestrictions.NO_VOICE_TRANSCRIPTION)
        val item = s.plan.items.getOrNull(s.itemIndex)
        val detail = if (showText && item != null && turn != null) {
            LessonDetail(
                expression = item.target.english,
                meaning = item.target.korean,
                prompt = turn.prompt,
                lastAnswer = s.lastAnswer,
                feedback = s.feedback?.reply,
            )
        } else null

        val activePhase = s.phase in setOf(LessonPhase.PROMPT, LessonPhase.ANSWER, LessonPhase.THINKING, LessonPhase.FEEDBACK, LessonPhase.QUIET)
        return LessonUiState(
            topic = s.plan.weekTitle,
            visual = visual,
            headline = headline,
            caption = caption,
            badge = badge,
            progress = if (s.phase == LessonPhase.FINISHED) 1f else s.progress,
            isPaused = s.phase == LessonPhase.PAUSED,
            // 안전 사유(전화·방향지시등 등)로 멈춘 동안에는 사용자가 재개할 수 없다. 사용자 일시정지·포커스 상실만 재생 버튼으로 재개.
            canTogglePause = activePhase ||
                (s.phase == LessonPhase.PAUSED && (s.userPaused || Interruption.AUDIO_FOCUS_LOST in s.signalInterruptions)),
            canRepeat = s.phase == LessonPhase.PROMPT || s.phase == LessonPhase.ANSWER || s.phase == LessonPhase.FEEDBACK,
            offline = s.offline,
            detail = detail,
            finished = s.phase == LessonPhase.FINISHED,
            stopped = s.phase == LessonPhase.STOPPED,
        )
    }

    private fun pausedCaption(primary: Interruption?): String = when {
        primary == null -> "곧 이어서 할게요 · 진행 상황은 그대로예요"
        primary.needsUserAction -> "${primary.label} · 재생 버튼을 누르면 이어서 해요"
        else -> "${primary.label} · 진행 상황은 그대로예요"
    }

    private fun quietCaption(situation: DrivingSituation): String = when (situation) {
        DrivingSituation.DEPARTING -> "출발 직후엔 잠깐 쉬어요 · 곧 이어서 할게요"
        DrivingSituation.ARRIVING -> "곧 도착해요 · 남은 내용은 다음에 이어서 해요"
        else -> "안전한 구간에서 이어서 할게요"
    }
}
