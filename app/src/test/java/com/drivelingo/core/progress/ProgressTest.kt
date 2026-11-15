package com.drivelingo.core.progress

import com.drivelingo.core.curriculum.CommuteType
import com.drivelingo.core.curriculum.Level
import com.drivelingo.core.driving.Interruption
import com.drivelingo.core.driving.LearningMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressTest {

    private val tuesday = LocalDate.of(2026, 11, 17)

    @Test
    fun `streak continues across days without driving`() {
        val friday = LocalDate.of(2026, 11, 13)
        val monday = LocalDate.of(2026, 11, 16)
        // 월요일은 재택(운전 안 함) → 화요일 학습해도 스트릭 유지
        assertEquals(6, StreakRules.afterStudy(5, setOf(friday), tripDates = setOf(friday), today = tuesday))
        // 월요일에 운전했는데 학습하지 않음 → 끊김
        assertEquals(1, StreakRules.afterStudy(5, setOf(friday), tripDates = setOf(friday, monday), today = tuesday))
        // 오늘 이미 학습함 → 그대로
        assertEquals(5, StreakRules.afterStudy(5, setOf(tuesday), emptySet(), tuesday))
    }

    @Test
    fun `week marks show done, today and upcoming days`() {
        val monday = LocalDate.of(2026, 11, 16)
        val marks = StreakRules.weekMarks(tuesday, setOf(monday))
        assertEquals(DayMark.DONE, marks[0])
        assertEquals(DayMark.TODAY, marks[1])
        assertEquals(DayMark.UPCOMING, marks[2])
    }

    @Test
    fun `completing a lesson updates streak, daily record, ranking words and clears checkpoint`() {
        val repo = ProgressRepository(InMemoryStore()) { tuesday }
        val before = repo.progress.value
        assertEquals(13, before.streakDays)
        assertEquals(3, before.nextLessonNumber)

        repo.saveCheckpoint(LessonCheckpoint(Level.L2, 1, 3, 4, LessonTally(words = 20)))
        repo.completeLesson(result(words = 112, commute = CommuteType.MORNING))

        val after = repo.progress.value
        assertEquals(14, after.streakDays)
        assertEquals(4, after.nextLessonNumber)
        assertTrue(after.daily!!.morningDone)
        assertEquals(before.weeklySegmentWords + 112, after.weeklySegmentWords)
        assertNull(after.checkpoint)
        assertEquals("I went camping with my family.", after.corrections.first().corrected)
    }

    @Test
    fun `progress survives a restart through the store`() {
        val store = InMemoryStore()
        ProgressRepository(store) { tuesday }.apply {
            completeOnboarding(Level.L3, micGranted = true, rankingOptIn = true)
            completeLesson(result(words = 50, commute = CommuteType.MORNING))
        }
        val reloaded = ProgressRepository(store) { tuesday }
        assertEquals(Level.L3, reloaded.settings.value.level)
        assertTrue(reloaded.settings.value.rankingOptIn)
        assertEquals(50, reloaded.progress.value.lastResult!!.tally.words)
        assertEquals(1, reloaded.progress.value.lastResult!!.tally.pausesByReason[Interruption.TURN_SIGNAL])
    }

    @Test
    fun `finishing the tenth lesson moves to the next week`() {
        val repo = ProgressRepository(InMemoryStore()) { tuesday }
        repeat(8) { repo.completeLesson(result(words = 1, commute = CommuteType.EVENING)) }
        assertEquals(2, repo.progress.value.week)
        assertEquals(1, repo.progress.value.nextLessonNumber)
    }

    @Test
    fun `ranking board compares only with opted-in mock entries`() {
        val board = MockRanking.board(myWords = 448)
        assertEquals(2, board.myRank)
        assertEquals(38, board.wordsToMaster)
    }

    private fun result(words: Int, commute: CommuteType) = LessonResultRecord(
        level = Level.L2, week = 1, lessonNumber = 3, title = "화요일 출근 수업", topic = "회의 시작 표현",
        commute = commute, newExpressionCount = 2,
        tally = LessonTally(
            words = words, maxCombo = 7,
            turnsByMode = mapOf(LearningMode.SHORT_ANSWER to 5),
            pausesByReason = mapOf(Interruption.TURN_SIGNAL to 1),
            corrections = listOf(Correction("I go to camping with my family.", "I went camping with my family.")),
        ),
        date = tuesday.toString(),
    )
}
