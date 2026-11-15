package com.drivelingo.core.progress

import com.drivelingo.core.curriculum.Level
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 시안과 같은 화면이 나오도록 넣어 두는 초기 데이터.
 * 13일 연속 · L2 1주차 수업 3/10(화요일 출근 수업) 직전 상태.
 */
internal object MockSeed {

    fun progress(today: LocalDate): LearningProgress {
        val pastWeekdays = generateSequence(today.minusDays(1)) { it.minusDays(1) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .take(13)
            .sortedBy { it.toEpochDay() }
            .map(LocalDate::toString)
            .toList()
        return LearningProgress(
            level = Level.L2,
            week = 1,
            completedLessonsInWeek = 2,
            streakDays = 13,
            studyDates = pastWeekdays,
            tripDates = pastWeekdays,
            weeklySegmentWords = 336,
        )
    }
}

/**
 * 구간 마스터 랭킹(Mock).
 * 실제 구현에서는 서버가 "구간 ID + 주간 단어 수"만 받아 집계한다. 원본 위치·경로는 보내지 않는다.
 * 랭킹은 [UserSettings.rankingOptIn] 이 켜진 사용자만 대상으로 한다.
 */
object MockRanking {
    const val SEGMENT_NAME = "판교IC → 양재IC"
    private const val RIVAL_NAME = "출근길영어왕"
    private const val RIVAL_WORDS = 486

    data class Entry(val rank: Int, val name: String, val words: Int, val isMe: Boolean)
    data class Board(val segment: String, val entries: List<Entry>, val myRank: Int, val wordsToMaster: Int)

    fun board(myWords: Int): Board {
        val sorted = listOf(RIVAL_NAME to RIVAL_WORDS, "나" to myWords).sortedByDescending { it.second }
        val entries = sorted.mapIndexed { i, (name, words) -> Entry(i + 1, name, words, name == "나") }
        val myRank = entries.first { it.isMe }.rank
        return Board(SEGMENT_NAME, entries, myRank, (RIVAL_WORDS - myWords).coerceAtLeast(0))
    }
}
