package com.drivelingo.core.progress

import java.time.DayOfWeek
import java.time.LocalDate

enum class DayMark { DONE, TODAY, TODAY_DONE, UPCOMING, SKIPPED }

/** 통근 스트릭 규칙. "재택이나 휴가로 차를 안 탄 날은 자동으로 유지돼요." */
object StreakRules {

    /**
     * 오늘 수업을 마쳤을 때의 새 스트릭.
     * 마지막 학습일과 오늘 사이에 "운전했지만 학습하지 않은 날"이 있으면 1부터 다시 센다.
     */
    fun afterStudy(streak: Int, studyDates: Set<LocalDate>, tripDates: Set<LocalDate>, today: LocalDate): Int {
        if (today in studyDates) return streak.coerceAtLeast(1)
        val last = studyDates.maxByOrNull { it.toEpochDay() } ?: return 1
        val missedDrivingDay = tripDates.any { it.isAfter(last) && it.isBefore(today) && it !in studyDates }
        return if (missedDrivingDay) 1 else streak + 1
    }

    /** 이번 주 월~일 표시. */
    fun weekMarks(today: LocalDate, studyDates: Set<LocalDate>): List<DayMark> {
        val monday = today.with(DayOfWeek.MONDAY)
        return (0..6).map { offset ->
            val day = monday.plusDays(offset.toLong())
            when {
                day == today && day in studyDates -> DayMark.TODAY_DONE
                day == today -> DayMark.TODAY
                day in studyDates -> DayMark.DONE
                day.isBefore(today) -> DayMark.SKIPPED
                else -> DayMark.UPCOMING
            }
        }
    }
}
