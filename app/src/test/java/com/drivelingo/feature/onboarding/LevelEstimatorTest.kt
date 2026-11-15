package com.drivelingo.feature.onboarding

import com.drivelingo.core.curriculum.Level
import org.junit.Assert.assertEquals
import org.junit.Test

class LevelEstimatorTest {

    @Test
    fun `all repeats passed with short interview answers is L2`() {
        assertEquals(Level.L2, LevelEstimator.estimate(selfLevel = 1, repeatScores = listOf(1f, 1f, 0.96f), interviewWords = listOf(9, 9)))
    }

    @Test
    fun `failed repeats place at L1`() {
        assertEquals(Level.L1, LevelEstimator.estimate(selfLevel = 1, repeatScores = listOf(1f, 0.4f, 0.2f), interviewWords = listOf(3, 2)))
    }

    @Test
    fun `measured level stays within one step of self assessment`() {
        assertEquals(Level.L2, LevelEstimator.estimate(selfLevel = 0, repeatScores = listOf(1f, 1f, 1f), interviewWords = listOf(30, 30)))
        assertEquals(Level.L3, LevelEstimator.estimate(selfLevel = 3, repeatScores = listOf(1f, 0f, 0f), interviewWords = listOf(2, 2)))
    }
}
