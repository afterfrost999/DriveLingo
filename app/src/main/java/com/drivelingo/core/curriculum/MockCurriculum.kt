package com.drivelingo.core.curriculum

/** 프로토타입용 내장 코스 데이터. 시안의 L2 "회사의 하루" 1주차를 자세히, 나머지는 축약해서 둔다. */
internal object MockCurriculum {

    private val courses = mapOf(
        Level.L1 to Course(
            Level.L1, "인사와 기본 표현",
            listOf(
                CourseWeek(1, "처음 만나는 인사", "Nice to meet you."),
                CourseWeek(2, "내 소개하기", "I work in marketing."),
                CourseWeek(3, "길과 장소 묻기", "Where is the meeting room?"),
                CourseWeek(4, "부탁하고 고마워하기", "Can you help me?"),
            ),
        ),
        Level.L2 to Course(
            Level.L2, "회사의 하루",
            listOf(
                CourseWeek(1, "회의 스몰톡", "\"Shall we get started?\" · \"Thanks for coming, everyone.\""),
                CourseWeek(2, "일정 잡기와 바꾸기", "\"Does Tuesday work for you?\""),
                CourseWeek(3, "전화·화상회의 기본", "\"You're on mute.\""),
                CourseWeek(4, "업무 부탁하고 답하기", "\"Could you take a look at this?\""),
            ),
        ),
        Level.L3 to Course(
            Level.L3, "내 생각 말하기",
            listOf(
                CourseWeek(1, "의견 말하기", "In my opinion, ..."),
                CourseWeek(2, "이유 설명하기", "The main reason is ..."),
                CourseWeek(3, "반대 의견 부드럽게", "I see your point, but ..."),
                CourseWeek(4, "제안하기", "How about we ...?"),
            ),
        ),
        Level.L4 to Course(
            Level.L4, "회의를 이끌기",
            listOf(
                CourseWeek(1, "안건 정리하기", "Let's go over the agenda."),
                CourseWeek(2, "토론 조율하기", "Let's hear from everyone."),
                CourseWeek(3, "결론 내기", "So, we've agreed to ..."),
                CourseWeek(4, "후속 조치 정하기", "Who will take care of this?"),
            ),
        ),
    )

    private val l2Week1Days = listOf(
        DayPlan("월", "인사와 안부", "주말 얘기", false),
        DayPlan("화", "회의 시작 표현", "다음 · 오늘 회의 얘기", false),
        DayPlan("수", "의견 묻기", "월·화 표현 섞어 쓰기", false),
        DayPlan("목", "일정 조율", "이번 주 표현 롤플레이", false),
        DayPlan("금", "회의 마무리", "유닛 롤플레이", true),
    )

    private val l2Week1Expressions = listOf(
        listOf(Expression("How was your weekend?", "주말 어땠어요?"), Expression("Good to see you again.", "다시 봬서 반가워요.")),
        listOf(Expression("Thanks for coming, everyone.", "와 주셔서 감사합니다."), Expression("Shall we get started?", "시작할까요?")),
        listOf(Expression("What do you think about this?", "이거 어떻게 생각하세요?"), Expression("I see your point.", "무슨 말씀인지 알겠어요.")),
        listOf(Expression("Does Thursday work for you?", "목요일 괜찮으세요?"), Expression("Let's move it to next week.", "다음 주로 옮기죠.")),
        listOf(Expression("Let's wrap up here.", "여기서 마무리하죠."), Expression("Thanks for your input.", "의견 감사합니다.")),
    )

    fun course(level: Level): Course = courses.getValue(level)

    fun weekPlan(level: Level, week: Int): List<DayPlan> {
        if (level == Level.L2 && week == 1) return l2Week1Days
        val title = course(level).weeks[(week - 1).coerceIn(0, 3)].title
        return LessonPlan.DAY_LABELS.mapIndexed { i, day ->
            DayPlan(day, "$title ${i + 1}", if (i == 4) "유닛 롤플레이" else "아침 표현 다시 쓰기", isUnitRoleplay = i == 4)
        }
    }

    fun expressionsFor(level: Level, week: Int, dayIndex: Int): List<Expression> {
        if (level == Level.L2 && week == 1) return l2Week1Expressions[dayIndex]
        val key = course(level).weeks[(week - 1).coerceIn(0, 3)].keyPhrase.trim('"')
        return listOf(Expression(key, "이번 주 핵심 표현"), Expression("Could you say that again?", "다시 말씀해 주시겠어요?"))
    }
}

/** 표현 목록 → 수업 아이템. 출근은 새 표현 2개, 퇴근은 아침 표현 복습. */
internal object LessonScripts {

    fun morning(todays: List<Expression>, yesterday: Expression?): List<LessonItem> = buildList {
        if (yesterday != null) {
            add(
                LessonItem(
                    id = "warmup", kind = ItemKind.REVIEW, target = yesterday,
                    listenScript = "Quick review from yesterday. ${yesterday.english} Keep it in mind for today.",
                    shortQuestion = "Quick review. Your coworker is back from the weekend. What do you ask?",
                    longQuestion = "Tell me about your weekend. What did you do?",
                    modelAnswer = yesterday.english,
                ),
            )
        }
        todays.forEachIndexed { i, expr ->
            add(
                LessonItem(
                    id = "intro-$i", kind = ItemKind.INTRODUCE, target = expr,
                    listenScript = "New phrase. ${expr.english} ... ${expr.english}",
                    shortQuestion = "Repeat after me. ${expr.english}",
                    longQuestion = "Repeat after me. ${expr.english}",
                    modelAnswer = expr.english,
                ),
            )
            add(
                LessonItem(
                    id = "practice-$i", kind = ItemKind.PRACTICE, target = expr,
                    listenScript = "Here's how it sounds in a meeting. ${expr.english}",
                    shortQuestion = "Your team just joined the call. What do you say?",
                    longQuestion = "Imagine you're opening today's meeting. Say a few sentences using: ${expr.english}",
                    modelAnswer = expr.english,
                ),
            )
        }
        val combined = todays.joinToString(" ") { it.english }
        add(
            LessonItem(
                id = "roleplay", kind = ItemKind.ROLEPLAY, target = todays.last(),
                listenScript = "Listen to a short meeting opening. $combined",
                shortQuestion = "I'm your teammate. Sorry I'm a little late! What do you say?",
                longQuestion = "Let's role-play. Everyone is here now. Open the meeting in your own words.",
                modelAnswer = combined,
            ),
        )
        add(
            LessonItem(
                id = "personal", kind = ItemKind.PERSONAL, target = todays.first(),
                listenScript = "Think about your meetings today and where you can use these phrases.",
                shortQuestion = "Do you have a meeting today?",
                longQuestion = "Tell me about a meeting you have this week. Who will be there?",
                modelAnswer = "Yes, I have a team meeting at ten.",
            ),
        )
        add(wrapUp(todays))
    }

    fun evening(morning: List<Expression>, corrections: List<String>): List<LessonItem> = buildList {
        morning.forEachIndexed { i, expr ->
            add(
                LessonItem(
                    id = "recall-$i", kind = ItemKind.REVIEW, target = expr,
                    listenScript = "Remember this one from this morning? ${expr.english}",
                    shortQuestion = "From this morning: how do you say \"${expr.korean}\" in English?",
                    longQuestion = "Did you use \"${expr.english}\" today? Tell me what happened.",
                    modelAnswer = expr.english,
                ),
            )
        }
        corrections.take(2).forEachIndexed { i, corrected ->
            add(
                LessonItem(
                    id = "fix-$i", kind = ItemKind.REVIEW, target = Expression(corrected, "아침에 고친 표현"),
                    listenScript = "One more time, the natural way. $corrected",
                    shortQuestion = "Let's fix this together. Say it the natural way: $corrected",
                    longQuestion = "Let's fix this together. Say it the natural way: $corrected",
                    modelAnswer = corrected,
                ),
            )
        }
        add(
            LessonItem(
                id = "day-talk", kind = ItemKind.PERSONAL, target = morning.first(),
                listenScript = "You did great today. Let's slow down and relax on the way home.",
                shortQuestion = "How was your day?",
                longQuestion = "How was your day? Tell me about one meeting.",
                modelAnswer = "It was busy, but good. I had a long meeting.",
            ),
        )
        add(wrapUp(morning))
    }

    private fun wrapUp(expressions: List<Expression>) = LessonItem(
        id = "wrap-up", kind = ItemKind.WRAP_UP, target = expressions.first(),
        listenScript = "Great work. Today's phrases: ${expressions.joinToString(" ") { it.english }} See you next time.",
        shortQuestion = "", longQuestion = "", modelAnswer = expressions.first().english,
    )
}
