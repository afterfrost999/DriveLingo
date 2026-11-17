package com.drivelingo.core.curriculum

/**
 * 코스·수업 콘텐츠 제공. 지금은 앱에 내장한 Mock 카탈로그([MockCurriculum])를 쓴다.
 * 백엔드가 생기면 같은 인터페이스로 원격 구현을 추가하고, 오프라인용 캐시는 이 구현을 재사용한다.
 */
interface CurriculumRepository {
    fun course(level: Level): Course
    fun weekPlan(level: Level, week: Int): List<DayPlan>

    /**
     * @param lessonNumber 주차 내 수업 번호(1..10). 홀수=출근, 짝수=퇴근.
     * @param corrections 직전 수업에서 교정된 표현. 퇴근 복습에 한 번 더 나온다.
     */
    fun lessonPlan(level: Level, week: Int, lessonNumber: Int, corrections: List<String> = emptyList()): LessonPlan
}

class MockCurriculumRepository : CurriculumRepository {

    override fun course(level: Level): Course = MockCurriculum.course(level)

    override fun weekPlan(level: Level, week: Int): List<DayPlan> = MockCurriculum.weekPlan(level, week)

    override fun lessonPlan(level: Level, week: Int, lessonNumber: Int, corrections: List<String>): LessonPlan {
        val course = course(level)
        val courseWeek = course.weeks[(week - 1).coerceIn(0, course.weeks.lastIndex)]
        val dayIndex = ((lessonNumber - 1) / 2).coerceIn(0, 4)
        val commute = if (lessonNumber % 2 == 1) CommuteType.MORNING else CommuteType.EVENING
        val day = weekPlan(level, week)[dayIndex]
        val todays = MockCurriculum.expressionsFor(level, week, dayIndex)
        // 어제 표현이 오늘 표현과 같으면(주마다 표현 2개인 주차) 몸풀기 복습을 넣지 않는다.
        val yesterday = if (dayIndex > 0) MockCurriculum.expressionsFor(level, week, dayIndex - 1).first().takeIf { it !in todays } else null

        return if (commute == CommuteType.MORNING) {
            LessonPlan(
                lessonNumber = lessonNumber, level = level, weekNumber = week, weekTitle = courseWeek.title,
                dayIndex = dayIndex, commute = commute, topic = day.morningTopic,
                newExpressions = todays, reviewExpressions = listOfNotNull(yesterday),
                items = LessonScripts.morning(todays, yesterday), speechRate = 1.0f,
            )
        } else {
            LessonPlan(
                lessonNumber = lessonNumber, level = level, weekNumber = week, weekTitle = courseWeek.title,
                dayIndex = dayIndex, commute = commute, topic = day.eveningTopic,
                newExpressions = emptyList(), reviewExpressions = todays,
                items = LessonScripts.evening(todays, corrections), speechRate = 0.9f,
            )
        }
    }
}
