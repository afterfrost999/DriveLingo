package com.drivelingo.core.speech.command

import com.drivelingo.core.speech.command.VoiceCommand.DONT_KNOW
import com.drivelingo.core.speech.command.VoiceCommand.END_TODAY
import com.drivelingo.core.speech.command.VoiceCommand.FASTER
import com.drivelingo.core.speech.command.VoiceCommand.HELP
import com.drivelingo.core.speech.command.VoiceCommand.KOREAN
import com.drivelingo.core.speech.command.VoiceCommand.LISTEN_ONLY
import com.drivelingo.core.speech.command.VoiceCommand.NEXT
import com.drivelingo.core.speech.command.VoiceCommand.PAUSE
import com.drivelingo.core.speech.command.VoiceCommand.REPEAT
import com.drivelingo.core.speech.command.VoiceCommand.SLOWER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandParserTest {

    private fun assertCommand(expected: VoiceCommand, vararg utterances: String) {
        utterances.forEach { assertEquals("\"$it\"", expected, VoiceCommandParser.parse(it)?.command) }
    }

    private fun assertAnswer(vararg utterances: String, expected: String? = null) {
        utterances.forEach { assertNull("\"$it\" should be an answer", VoiceCommandParser.parse(it, expected)) }
    }

    @Test
    fun `pause - many ways to say wait`() = assertCommand(
        PAUSE,
        "Wait", "wait!", "Wait wait", "Hold on", "Hang on a sec", "Give me a second", "gimme a sec",
        "One moment please", "Just a minute... wait a minute", "Okay, pause", "Pause the lesson", "Stop",
        "Stop for now", "Um, hold on please", "paws", "Coach, wait",
        "잠깐", "잠깐만요", "잠시만", "잠시만 기다려 줘", "멈춰", "일시정지", "스톱", "그만", "jamkkan",
    )

    @Test
    fun `repeat - many ways to ask again`() = assertCommand(
        REPEAT,
        "Again", "Again please", "Say that again", "Can you say that again?", "Could you repeat that?",
        "Repeat", "repeet", "One more time", "Once more please", "Come again?", "Pardon?", "Pardon me",
        "Sorry?", "Sorry, what?", "What?", "Huh?", "I didn't catch that", "Sorry I didn't get that",
        "What did you say?", "What was the question?", "I can't hear you", "Sorry, I didn't catch that, can you say it again",
        "다시", "다시요", "다시 한 번 말해 주세요", "한 번 더", "뭐라고?", "잘 못 들었어요", "안 들려요", "dasi",
    )

    @Test
    fun `next - many ways to skip`() = assertCommand(
        NEXT,
        "Next", "Next one", "Next please", "Okay next", "Skip", "Skip this", "Skip this one", "Can we move on?",
        "Let's move on", "Pass", "necks", "Next question", "Give me another question",
        "다음", "다음 거", "다음으로 넘어가", "넘어가자", "건너뛰어", "패스", "스킵",
    )

    @Test
    fun `slower and faster`() {
        assertCommand(
            SLOWER,
            "Slower", "Slow down", "Slow down please", "Can you speak slower?", "Speak more slowly", "Too fast",
            "That's too fast", "A bit slower", "Could you say it slowly?", "천천히", "천천히 말해 줘", "좀 느리게", "너무 빨라요",
        )
        assertCommand(
            FASTER,
            "Faster", "Speed up", "Can you speak faster?", "Too slow", "Normal speed please", "빠르게", "원래 속도로", "너무 느려",
        )
    }

    @Test
    fun `korean explanation`() = assertCommand(
        KOREAN,
        "In Korean", "Korean please", "Say it in Korean", "What does that mean?", "What does it mean", "What's the meaning?",
        "Translate", "Translate it please", "I don't understand", "Meaning?",
        "한국어로", "한국어로 말해 줘", "한국말로 해줘", "무슨 뜻이야?", "뜻이 뭐예요", "해석해 줘", "번역", "이해가 안 돼요", "hangugeoro",
    )

    @Test
    fun `dont know asks for a model answer`() = assertCommand(
        DONT_KNOW,
        "I don't know", "I dunno", "No idea", "I have no idea", "I'm not sure", "Help", "Help me",
        "Give me a hint", "Hint", "What should I say?", "How do I say that?", "I'm stuck", "You go first",
        "Can you show me how?", "I forgot the word",
        "모르겠어", "모르겠어요", "잘 모르겠는데", "몰라", "힌트", "힌트 줘", "어떻게 말해?", "생각이 안 나", "먼저 해 줘", "도와줘",
    )

    @Test
    fun `end today`() = assertCommand(
        END_TODAY,
        "That's enough", "That's enough for today", "Enough", "Let's stop here", "Let's call it a day",
        "I'm done for today", "Stop the lesson", "End the lesson", "Let's finish", "Quit", "Let's wrap up",
        "오늘은 그만", "오늘은 그만할래", "그만하자", "그만할게요", "이제 그만", "여기까지 할게", "끝내자", "수업 끝",
    )

    @Test
    fun `listen only and help`() {
        assertCommand(
            LISTEN_ONLY,
            "I'll just listen", "Just listen", "Listen only", "I can't talk right now", "I can't speak now",
            "I have a passenger", "Someone is with me",
            "듣기만 할게", "오늘은 듣기만 할래", "지금 말 못 해", "동승자가 있어",
        )
        assertCommand(HELP, "What can I say?", "What are the commands?", "Voice commands", "명령어", "뭐라고 말하면 돼?", "사용법")
    }

    @Test
    fun `negated commands are not commands`() = assertAnswer(
        "Don't stop", "Do not repeat it", "Never stop", "멈추지 마", "그만하지 마", "다시 하지 마", "안 멈춰도 돼",
    )

    @Test
    fun `ordinary answers are not commands`() = assertAnswer(
        "I skip breakfast on weekdays",
        "Nice to see you again",
        "I finish work at six",
        "I run faster than my brother",
        "What a day",
        "The bus stops here",
        "I translate documents at work",
        "I went camping with my family and we cooked dinner outside",
        "My manager will wait for the report",
        "Thanks for coming everyone shall we get started",
        "How was your weekend",
        "Yes I have a meeting at ten",
        "Something else entirely",
        "Help me with the report please, I need it by Friday",
    )

    @Test
    fun `an answer that matches the expected phrase wins over a command`() {
        // L1 표현 "Could you say that again?" 를 따라 말하는 턴에서는 대답이다.
        assertAnswer("Could you say that again?", "could you say that again", expected = "Could you say that again?")
        assertAnswer("Can you help me?", expected = "Can you help me?")
        // 다른 턴에서는 명령이다.
        assertEquals(REPEAT, VoiceCommandParser.parse("Could you say that again?", "Nice to meet you.")?.command)
    }

    @Test
    fun `more specific command wins`() {
        assertEquals(SLOWER, VoiceCommandParser.parse("Can you say it again more slowly?")?.command)
        assertEquals(KOREAN, VoiceCommandParser.parse("Sorry, what does that mean?")?.command)
        assertEquals(END_TODAY, VoiceCommandParser.parse("오늘은 그만")?.command)
        assertEquals(PAUSE, VoiceCommandParser.parse("그만")?.command)
    }

    @Test
    fun `long sentences only count when the command is at the start or the end`() {
        assertEquals(REPEAT, VoiceCommandParser.parse("Sorry I was driving and I didn't hear, can you say it again")?.command)
        assertNull(VoiceCommandParser.parse("I think we should slow down the project a little this quarter"))
    }

    @Test
    fun `empty and noise`() {
        assertNull(VoiceCommandParser.parse(""))
        assertNull(VoiceCommandParser.parse("   "))
        assertNull(VoiceCommandParser.parse("um uh"))
        assertNull(VoiceCommandParser.parse("..."))
    }
}
