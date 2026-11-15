package com.drivelingo.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelingo.app.AppContainer
import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.speech.AnswerStyle
import com.drivelingo.core.speech.ListenRequest
import com.drivelingo.core.speech.ListenResult
import com.drivelingo.feature.lesson.engine.PhraseMatcher
import com.drivelingo.feature.lesson.engine.WordCounter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { MIC, VOICE_TEST, SELF_ASSESS, REPEAT, INTERVIEW, RESULT, PICK_LEVEL }

enum class VoicePhase { IDLE, PLAYING, LISTENING, DONE, FAILED }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.MIC,
    val micGranted: Boolean = false,
    val voicePhase: VoicePhase = VoicePhase.IDLE,
    val heard: String? = null,
    val selfLevel: Int? = null,
    val repeatIndex: Int = 0,
    val interviewIndex: Int = 0,
    val recommended: Level? = null,
    val chosen: Level = Level.L2,
    val rankingOptIn: Boolean = false,
    /** 레벨 테스트·설정은 주차 중에만(NO_SETUP). */
    val restricted: Boolean = false,
) {
    val testPart: Int get() = when (step) {
        OnboardingStep.SELF_ASSESS -> 1
        OnboardingStep.REPEAT -> 2
        OnboardingStep.INTERVIEW -> 3
        else -> 0
    }
}

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(restricted = container.drivingMonitor.status.value.uiRestricted))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val repeatScores = mutableListOf<Float>()
    private val interviewWords = mutableListOf<Int>()
    private var voiceJob: Job? = null

    init {
        viewModelScope.launch {
            container.drivingMonitor.status.collect { status ->
                // 테스트 중 출발하면 음성을 멈추고, 주차 후 "다시 듣기"로 이어서 한다.
                if (status.uiRestricted && !_state.value.restricted) stopVoice()
                _state.update { it.copy(restricted = status.uiRestricted) }
            }
        }
    }

    fun onMicPermissionResult(granted: Boolean) {
        _state.update { it.copy(micGranted = granted, step = if (granted) OnboardingStep.VOICE_TEST else OnboardingStep.PICK_LEVEL) }
    }

    fun startVoiceTest() = runVoice {
        val result = listen(AnswerStyle.SHORT, "voice-test", LevelTestContent.VOICE_TEST_SENTENCE)
        _state.update {
            if (result is ListenResult.Recognized) it.copy(voicePhase = VoicePhase.DONE, heard = result.text)
            else it.copy(voicePhase = VoicePhase.FAILED, heard = null)
        }
    }

    fun toSelfAssessment() = _state.update { it.copy(step = OnboardingStep.SELF_ASSESS, voicePhase = VoicePhase.IDLE) }

    fun chooseSelfLevel(index: Int) {
        repeatScores.clear()
        interviewWords.clear()
        _state.update { it.copy(selfLevel = index, step = OnboardingStep.REPEAT, repeatIndex = 0) }
        playCurrent()
    }

    /** 현재 문항을 (다시) 들려주고 대답을 듣는다. */
    fun playCurrent() {
        val s = _state.value
        when (s.step) {
            OnboardingStep.REPEAT -> runVoice {
                val sentence = LevelTestContent.repeatSentences[s.repeatIndex]
                speak(sentence)
                val result = listen(AnswerStyle.REPEAT, "level-repeat-${s.repeatIndex}", sentence)
                val score = (result as? ListenResult.Recognized)?.let { PhraseMatcher.similarity(it.text, sentence) } ?: 0f
                repeatScores += score
                advance()
            }
            OnboardingStep.INTERVIEW -> runVoice {
                speak(LevelTestContent.interviewQuestions[s.interviewIndex])
                val result = listen(AnswerStyle.LONG, "level-interview-${s.interviewIndex}", LevelTestContent.mockInterviewAnswers[s.interviewIndex])
                interviewWords += (result as? ListenResult.Recognized)?.let { WordCounter.count(it.text) } ?: 0
                advance()
            }
            else -> Unit
        }
    }

    fun pickLevelManually() {
        stopVoice()
        _state.update { it.copy(step = OnboardingStep.PICK_LEVEL) }
    }

    fun selectLevel(level: Level) = _state.update { it.copy(chosen = level, step = OnboardingStep.RESULT) }

    fun setRankingOptIn(optIn: Boolean) = _state.update { it.copy(rankingOptIn = optIn) }

    fun finish() {
        val s = _state.value
        container.progress.completeOnboarding(s.chosen, s.micGranted, s.rankingOptIn)
    }

    private fun advance() {
        val s = _state.value
        when {
            s.step == OnboardingStep.REPEAT && s.repeatIndex < LevelTestContent.repeatSentences.lastIndex -> {
                _state.update { it.copy(repeatIndex = it.repeatIndex + 1) }
                playCurrent()
            }
            s.step == OnboardingStep.REPEAT -> {
                _state.update { it.copy(step = OnboardingStep.INTERVIEW, interviewIndex = 0) }
                playCurrent()
            }
            s.step == OnboardingStep.INTERVIEW && s.interviewIndex < LevelTestContent.interviewQuestions.lastIndex -> {
                _state.update { it.copy(interviewIndex = it.interviewIndex + 1) }
                playCurrent()
            }
            else -> {
                val level = LevelEstimator.estimate(s.selfLevel ?: 1, repeatScores, interviewWords)
                _state.update { it.copy(step = OnboardingStep.RESULT, recommended = level, chosen = level, voicePhase = VoicePhase.IDLE) }
            }
        }
    }

    private fun runVoice(block: suspend () -> Unit) {
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch { block() }
    }

    private fun stopVoice() {
        voiceJob?.cancel()
        container.voiceOutput.stop()
        container.voiceInput.cancel()
        _state.update { it.copy(voicePhase = VoicePhase.IDLE) }
    }

    private suspend fun speak(text: String) {
        _state.update { it.copy(voicePhase = VoicePhase.PLAYING) }
        container.voiceOutput.speak(text)
    }

    private suspend fun listen(style: AnswerStyle, itemId: String, expected: String): ListenResult {
        _state.update { it.copy(voicePhase = VoicePhase.LISTENING) }
        return container.voiceInput.listen(ListenRequest(style, maxDurationMs = null, itemId = itemId, expected = expected))
    }
}
