package com.drivelingo.feature.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelingo.app.AppContainer
import com.drivelingo.core.curriculum.Course
import com.drivelingo.core.curriculum.DayPlan
import com.drivelingo.core.curriculum.LessonPlan
import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.driving.DrivingStatus
import com.drivelingo.core.progress.LearningProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SlotState { DONE, CURRENT, UPCOMING }

data class DaySlots(val day: DayPlan, val morning: SlotState, val evening: SlotState)

data class CourseUiState(
    val currentLevel: Level,
    val course: Course,
    val currentWeek: Int,
    val completedInWeek: Int,
    val days: List<DaySlots>,
    val uiRestricted: Boolean,
)

class CourseViewModel(private val container: AppContainer) : ViewModel() {

    val uiState: StateFlow<CourseUiState> =
        combine(container.progress.progress, container.drivingMonitor.status, ::build)
            .stateIn(viewModelScope, SharingStarted.Eagerly, build(container.progress.progress.value, container.drivingMonitor.status.value))

    fun changeLevel(level: Level) = container.progress.changeLevel(level)

    private fun build(progress: LearningProgress, driving: DrivingStatus): CourseUiState {
        val completed = progress.completedLessonsInWeek
        fun slot(lessonNumber: Int) = when {
            lessonNumber <= completed -> SlotState.DONE
            lessonNumber == completed + 1 -> SlotState.CURRENT
            else -> SlotState.UPCOMING
        }
        val days = container.curriculum.weekPlan(progress.level, progress.week).mapIndexed { i, day ->
            DaySlots(day, morning = slot(i * 2 + 1), evening = slot(i * 2 + 2))
        }
        return CourseUiState(
            currentLevel = progress.level,
            course = container.curriculum.course(progress.level),
            currentWeek = progress.week,
            completedInWeek = completed,
            days = days,
            uiRestricted = driving.uiRestricted,
        )
    }

    companion object {
        const val LESSONS_PER_WEEK = LessonPlan.LESSONS_PER_WEEK
    }
}
