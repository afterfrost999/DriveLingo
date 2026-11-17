package com.drivelingo.core.curriculum

import com.drivelingo.core.driving.LearningMode
import com.drivelingo.feature.lesson.engine.ActivityPlanner
import com.drivelingo.feature.lesson.engine.ActivityType
import com.drivelingo.feature.lesson.engine.LocalFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 코스 콘텐츠가 실제로 읽어 줄 수 있는 문장인지 확인한다(미완성 문장, 자리표시 뜻, 상황과 맞지 않는 질문 방지). */
class CurriculumContentTest {

    private val repo = MockCurriculumRepository()

    private fun allPlans() = Level.entries.flatMap { level ->
        (1..4).flatMap { week -> (1..10).map { lesson -> repo.lessonPlan(level, week, lesson) } }
    }

    @Test
    fun `every expression is a complete sentence with its own meaning and cue`() {
        val expressions = Level.entries.flatMap { level ->
            (1..4).flatMap { week -> (0..4).flatMap { day -> MockCurriculum.expressionsFor(level, week, day) } }
        }.distinct()
        expressions.forEach { e ->
            assertFalse("미완성 문장: ${e.english}", e.english.contains("..."))
            assertTrue("문장 부호로 끝나야 함: ${e.english}", e.english.last() in ".?!")
            assertTrue("뜻이 비어 있음: ${e.english}", e.korean.isNotBlank())
            assertFalse("자리표시 뜻: ${e.english}", e.korean.contains("핵심 표현"))
            assertTrue("상황 질문이 없음: ${e.english}", e.cue.isNotBlank())
        }
    }

    @Test
    fun `day topics are not numbered placeholders`() {
        Level.entries.forEach { level ->
            (1..4).forEach { week ->
                repo.weekPlan(level, week).forEach { day ->
                    assertFalse(day.morningTopic, day.morningTopic.last().isDigit())
                }
            }
        }
    }

    @Test
    fun `practice and roleplay questions belong to the phrase they practice`() {
        allPlans().forEach { plan ->
            plan.items.filter { it.kind == ItemKind.PRACTICE || it.kind == ItemKind.ROLEPLAY }.forEach { item ->
                assertEquals(item.target.english, item.modelAnswer)
            }
            val practiceQuestions = plan.items.filter { it.kind == ItemKind.PRACTICE }.map { it.shortQuestion }
            assertEquals("표현마다 다른 질문이어야 함", practiceQuestions.size, practiceQuestions.distinct().size)
        }
    }

    @Test
    fun `warm-up never reviews one of today's new phrases`() {
        allPlans().filter { it.commute == CommuteType.MORNING }.forEach { plan ->
            plan.items.filter { it.id == "warmup" }.forEach { warmup ->
                assertFalse(warmup.target in plan.newExpressions)
            }
        }
    }

    @Test
    fun `wrap-up does not say goodbye before the closing line`() {
        allPlans().forEach { plan ->
            val wrap = plan.items.last()
            assertEquals(ItemKind.WRAP_UP, wrap.kind)
            assertFalse(wrap.listenScript.contains("See you"))
        }
    }

    @Test
    fun `personal questions are open ended so the example answer is not a correction`() {
        val plan = repo.lessonPlan(Level.L2, 1, 3)
        val personal = plan.items.first { it.kind == ItemKind.PERSONAL }
        val turn = ActivityPlanner.plan(personal, LearningMode.SHORT_ANSWER, offline = false)!!
        assertTrue(turn.openEnded)
        val feedback = LocalFeedback.build(turn.copy(activity = ActivityType.SHORT_ANSWER), "No, I don't.")
        assertTrue(feedback.correct)
        assertTrue(feedback.reply.startsWith("Thanks for sharing"))
        assertNull(feedback.recast)
    }

    @Test
    fun `corrected sentences in the evening have no fake meaning`() {
        val plan = repo.lessonPlan(Level.L2, 1, 4, corrections = listOf("Thanks for coming, everyone."))
        val fix = plan.items.first { it.id == "fix-0" }
        assertEquals("", fix.target.korean)
    }
}
