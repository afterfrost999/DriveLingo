package com.drivelingo.core.curriculum

/**
 * 프로토타입용 내장 코스 데이터. 시안의 L2 "회사의 하루" 1주차는 요일마다 표현을 따로 두고,
 * 나머지 주차는 주마다 핵심 표현 2개를 요일마다 상황을 바꿔 연습한다.
 */
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
        DayPlan("화", "회의 시작 표현", "오늘 회의 얘기", false),
        DayPlan("수", "의견 묻기", "월·화 표현 섞어 쓰기", false),
        DayPlan("목", "일정 조율", "이번 주 표현 롤플레이", false),
        DayPlan("금", "회의 마무리", "유닛 롤플레이", true),
    )

    private val l2Week1Expressions = listOf(
        listOf(
            Expression(
                "How was your weekend?", "주말 어땠어요?",
                cue = "It's Monday morning, and your coworker just sat down next to you. What do you ask?",
                partner = "Morning! It's Monday again.",
            ),
            Expression(
                "Good to see you again.", "다시 봬서 반가워요.",
                cue = "You meet a client you haven't seen for a month. What do you say?",
                partner = "Hi! It's been a while, hasn't it?",
            ),
        ),
        listOf(
            Expression(
                "Thanks for coming, everyone.", "와 주셔서 감사합니다.",
                cue = "Everyone has joined your meeting. What's the first thing you say?",
                partner = "I think everyone is here now.",
            ),
            Expression(
                "Shall we get started?", "시작할까요?",
                cue = "It's time to begin the meeting. How do you suggest starting?",
                partner = "Okay, it's ten o'clock.",
            ),
        ),
        listOf(
            Expression(
                "What do you think about this?", "이거 어떻게 생각하세요?",
                cue = "You want your teammate's opinion on a new plan. What do you ask?",
                partner = "So that's my idea for the new design.",
            ),
            Expression(
                "I see your point.", "무슨 말씀인지 알겠어요.",
                cue = "Your coworker explains a different opinion, and you understand it. What do you say?",
                partner = "I think we need more time for testing.",
            ),
        ),
        listOf(
            Expression(
                "Does Thursday work for you?", "목요일 괜찮으세요?",
                cue = "You want to meet your manager on Thursday. How do you ask?",
                partner = "When should we meet to talk about it?",
            ),
            Expression(
                "Let's move it to next week.", "다음 주로 옮기죠.",
                cue = "This week is too busy for the meeting. What do you suggest?",
                partner = "We're all really busy this week.",
            ),
        ),
        listOf(
            Expression(
                "Let's wrap up here.", "여기서 마무리하죠.",
                cue = "The meeting is almost over. How do you end it?",
                partner = "I think that covers everything.",
            ),
            Expression(
                "Thanks for your input.", "의견 감사합니다.",
                cue = "A coworker shared helpful ideas in the meeting. How do you thank them?",
                partner = "That's all I wanted to add.",
            ),
        ),
    )

    /** L2 1주차 밖의 주차: 주마다 핵심 표현 2개. 키는 (레벨, 주차). */
    private val weeklyExpressions: Map<Pair<Level, Int>, List<Expression>> = mapOf(
        (Level.L1 to 1) to listOf(
            Expression(
                "Nice to meet you.", "만나서 반가워요.",
                cue = "You meet a new coworker for the first time. What do you say?",
            ),
            Expression(
                "Nice to meet you, too.", "저도 만나서 반가워요.",
                cue = "A new coworker says, \"Nice to meet you.\" How do you answer?",
                partner = "Hi, I'm Sam. Nice to meet you.",
            ),
        ),
        (Level.L1 to 2) to listOf(
            Expression(
                "I work in marketing.", "마케팅 일을 해요.",
                cue = "A new coworker asks what you do. Say you work in marketing.",
            ),
            Expression(
                "I've been here for three years.", "여기서 3년째 일하고 있어요.",
                cue = "Someone asks how long you've worked here. Say it's been three years.",
                partner = "How long have you worked here?",
            ),
        ),
        (Level.L1 to 3) to listOf(
            Expression(
                "Where is the meeting room?", "회의실이 어디예요?",
                cue = "You're visiting another office and can't find the meeting room. What do you ask?",
            ),
            Expression(
                "It's on the third floor.", "3층에 있어요.",
                cue = "A visitor is looking for the meeting room. Tell them it's on the third floor.",
                partner = "Excuse me, where is the meeting room?",
            ),
        ),
        (Level.L1 to 4) to listOf(
            Expression(
                "Can you help me?", "좀 도와줄 수 있어요?",
                cue = "You can't open a file and need a coworker's help. What do you ask?",
            ),
            Expression(
                "Thanks a lot for your help.", "도와줘서 정말 고마워요.",
                cue = "Your coworker just fixed your problem. How do you thank them?",
                partner = "Okay, it should work now.",
            ),
        ),
        (Level.L2 to 2) to listOf(
            Expression(
                "Does Tuesday work for you?", "화요일 괜찮으세요?",
                cue = "You want to set up a meeting on Tuesday. How do you ask?",
            ),
            Expression(
                "Can we push it back an hour?", "한 시간 미룰 수 있을까요?",
                cue = "Your meeting is at two, but you need more time. What do you ask?",
                partner = "See you at two o'clock, then.",
            ),
        ),
        (Level.L2 to 3) to listOf(
            Expression(
                "You're on mute.", "음소거돼 있어요.",
                cue = "On a video call, your coworker is talking, but there's no sound. What do you tell them?",
            ),
            Expression(
                "Can you hear me okay?", "제 목소리 잘 들리세요?",
                cue = "You just joined a video call. How do you check your sound?",
                partner = "Hi, welcome to the call.",
            ),
        ),
        (Level.L2 to 4) to listOf(
            Expression(
                "Could you take a look at this?", "이것 좀 봐 줄 수 있어요?",
                cue = "You want your manager to check your report. What do you ask?",
            ),
            Expression(
                "Sure, I'll get it done by Friday.", "네, 금요일까지 끝낼게요.",
                cue = "Your manager asks you to finish a report. Say you'll finish it by Friday.",
                partner = "Can you finish the report this week?",
            ),
        ),
        (Level.L3 to 1) to listOf(
            Expression(
                "In my opinion, we need more time.", "제 생각엔 시간이 더 필요해요.",
                cue = "Your team wants to finish the project this week, but you don't agree. Give your opinion.",
            ),
            Expression(
                "I think it's a good idea.", "좋은 생각 같아요.",
                cue = "Your coworker suggests a team lunch on Friday. You like it. What do you say?",
                partner = "How about a team lunch this Friday?",
            ),
        ),
        (Level.L3 to 2) to listOf(
            Expression(
                "The main reason is the cost.", "가장 큰 이유는 비용이에요.",
                cue = "Your manager asks why you chose the cheaper plan. Give the main reason.",
            ),
            Expression(
                "That's why I chose this option.", "그래서 이 안을 골랐어요.",
                cue = "You just explained the good points of your plan. How do you finish?",
                partner = "So, why this option?",
            ),
        ),
        (Level.L3 to 3) to listOf(
            Expression(
                "I see your point, but I have a different idea.", "무슨 말인지 알겠지만, 제 생각은 좀 달라요.",
                cue = "Your coworker wants to cancel the event. You disagree, politely. What do you say?",
            ),
            Expression(
                "I'm not sure that will work.", "그게 잘될지 모르겠어요.",
                cue = "Someone plans to finish a big project in two days. You have doubts. What do you say?",
                partner = "Let's finish the whole project in two days.",
            ),
        ),
        (Level.L3 to 4) to listOf(
            Expression(
                "How about we meet on Monday?", "월요일에 만나는 건 어때요?",
                cue = "You want to suggest meeting on Monday. How do you say it?",
            ),
            Expression(
                "Why don't we take a short break?", "잠깐 쉬었다 할까요?",
                cue = "The meeting is running long, and everyone looks tired. What do you suggest?",
                partner = "We've been talking for two hours.",
            ),
        ),
        (Level.L4 to 1) to listOf(
            Expression(
                "Let's go over the agenda.", "안건부터 살펴보죠.",
                cue = "You're leading a meeting, and everyone is ready. How do you start?",
            ),
            Expression(
                "First, let's look at the sales numbers.", "먼저 매출 수치부터 보죠.",
                cue = "The first item on the agenda is the sales numbers. How do you bring it up?",
                partner = "Okay, what's first on the agenda?",
            ),
        ),
        (Level.L4 to 2) to listOf(
            Expression(
                "Let's hear from everyone.", "모두의 의견을 들어 보죠.",
                cue = "Only one person has been talking in the meeting. How do you invite others to speak?",
            ),
            Expression(
                "Let's get back to the main topic.", "본론으로 돌아가죠.",
                cue = "The discussion has gone off topic. How do you bring it back?",
                partner = "And that reminds me of my trip last summer.",
            ),
        ),
        (Level.L4 to 3) to listOf(
            Expression(
                "So, we've agreed to launch in May.", "그럼 5월에 출시하기로 한 거죠.",
                cue = "The team decided to launch in May. How do you sum up the decision?",
            ),
            Expression(
                "Does everyone agree?", "모두 동의하시나요?",
                cue = "You want to check that the whole team is okay with the plan. What do you ask?",
                partner = "I think that plan works for me.",
            ),
        ),
        (Level.L4 to 4) to listOf(
            Expression(
                "Who will take care of this?", "이건 누가 맡을까요?",
                cue = "There's a task nobody has taken yet. What do you ask the team?",
            ),
            Expression(
                "Let's follow up next week.", "다음 주에 다시 확인하죠.",
                cue = "You want to check the progress again next week. How do you close the meeting?",
                partner = "I think we're done for today.",
            ),
        ),
    )

    /** 축약 주차의 요일별 주제. 같은 표현을 요일마다 다른 방식으로 연습한다. */
    private val genericMorningTopics = listOf("핵심 표현 배우기", "상황 바꿔 말하기", "질문하고 답하기", "내 얘기로 말하기", "이번 주 정리")
    private const val GENERIC_EVENING_TOPIC = "아침 표현 복습"

    fun course(level: Level): Course = courses.getValue(level)

    fun weekPlan(level: Level, week: Int): List<DayPlan> {
        if (level == Level.L2 && week == 1) return l2Week1Days
        return LessonPlan.DAY_LABELS.mapIndexed { i, day ->
            val unit = i == LessonPlan.DAY_LABELS.lastIndex
            DayPlan(day, genericMorningTopics[i], if (unit) "유닛 롤플레이" else GENERIC_EVENING_TOPIC, isUnitRoleplay = unit)
        }
    }

    fun expressionsFor(level: Level, week: Int, dayIndex: Int): List<Expression> {
        if (level == Level.L2 && week == 1) return l2Week1Expressions[dayIndex.coerceIn(0, l2Week1Expressions.lastIndex)]
        return weeklyExpressions.getValue(level to week.coerceIn(1, 4))
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
                    shortQuestion = "Quick review from yesterday. ${yesterday.cueOrDefault()}",
                    longQuestion = "Quick review from yesterday. ${yesterday.cueOrDefault()} Then tell me when you could use it today.",
                    modelAnswer = yesterday.english,
                ),
            )
        }
        todays.forEachIndexed { i, expr ->
            add(
                LessonItem(
                    id = "intro-$i", kind = ItemKind.INTRODUCE, target = expr,
                    listenScript = "Today's phrase. ${expr.english} ... ${expr.english}",
                    shortQuestion = "Repeat after me. ${expr.english}",
                    longQuestion = "Repeat after me. ${expr.english}",
                    modelAnswer = expr.english,
                ),
            )
            add(
                LessonItem(
                    id = "practice-$i", kind = ItemKind.PRACTICE, target = expr,
                    listenScript = "Here's when to use it. ${expr.cueOrDefault()} ... ${expr.english}",
                    shortQuestion = expr.cueOrDefault(),
                    longQuestion = "${expr.cueOrDefault()} Say it, and then add one more sentence of your own.",
                    modelAnswer = expr.english,
                ),
            )
        }
        val last = todays.last()
        val combined = todays.joinToString(" ") { it.english }
        add(
            LessonItem(
                id = "roleplay", kind = ItemKind.ROLEPLAY, target = last,
                listenScript = "Listen to a short conversation. ${last.partnerOrCue()} ... ${last.english}",
                shortQuestion = "Let's role-play. I'll start. ${last.partnerOrCue()}",
                longQuestion = "Let's role-play. I'll start. ${last.partnerOrCue()} Keep the conversation going, and use both of today's phrases.",
                modelAnswer = last.english,
            ),
        )
        add(
            LessonItem(
                id = "personal", kind = ItemKind.PERSONAL, target = todays.first(),
                listenScript = "Think about your day and where you can use today's phrases: $combined",
                shortQuestion = "Where could you use today's phrases? At a meeting, or with a coworker?",
                longQuestion = "Tell me about your plans for today. Who will you talk to?",
                modelAnswer = "I could use them at my team meeting.",
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
                    shortQuestion = "From this morning. ${expr.cueOrDefault()}",
                    longQuestion = "Did you get to use \"${expr.english}\" today? Tell me about it.",
                    modelAnswer = expr.english,
                ),
            )
        }
        corrections.take(2).forEachIndexed { i, corrected ->
            add(
                LessonItem(
                    // 아침에 다듬은 문장은 우리말 뜻이 따로 없다(화면은 뜻 대신 안내 문구, "한국어로"는 질문만 다시).
                    id = "fix-$i", kind = ItemKind.REVIEW, target = Expression(corrected, ""),
                    listenScript = "One more time, the natural way. $corrected",
                    shortQuestion = "Here's a sentence we fixed this morning. Say it the natural way: $corrected",
                    longQuestion = "Here's a sentence we fixed this morning. Say it the natural way: $corrected",
                    modelAnswer = corrected,
                ),
            )
        }
        add(
            LessonItem(
                id = "day-talk", kind = ItemKind.PERSONAL, target = morning.first(),
                listenScript = "You did great today. Let's slow down and relax on the way home.",
                shortQuestion = "How was your day?",
                longQuestion = "How was your day? Tell me about one thing that happened at work.",
                modelAnswer = "It was busy, but good.",
            ),
        )
        add(wrapUp(morning))
    }

    // 마무리 인사("That's all for…")는 엔진이 이어서 하므로 여기서는 작별 인사를 하지 않는다.
    private fun wrapUp(expressions: List<Expression>) = LessonItem(
        id = "wrap-up", kind = ItemKind.WRAP_UP, target = expressions.first(),
        listenScript = "Great work. Let's hear today's phrases once more. ${expressions.joinToString(" ") { it.english }}",
        shortQuestion = "", longQuestion = "", modelAnswer = expressions.first().english,
    )

    private fun Expression.cueOrDefault(): String =
        cue.ifBlank { "How do you say \"$korean\" in English?" }

    private fun Expression.partnerOrCue(): String = partner.ifBlank { cueOrDefault() }
}
