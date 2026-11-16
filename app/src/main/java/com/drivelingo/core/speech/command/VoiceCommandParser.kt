package com.drivelingo.core.speech.command

import java.text.Normalizer

/**
 * 수업 중 음성 명령(기획서 "손을 쓰지 않는 조작", UX 기획서 4장 "음성 명령").
 * 말할 차례에 들은 말이 대답이 아니라 명령이면 엔진이 대답 대신 명령을 처리한다.
 */
enum class VoiceCommand(val label: String) {
    PAUSE("잠깐"),
    REPEAT("다시"),
    NEXT("다음"),
    SLOWER("천천히"),
    FASTER("빠르게"),
    KOREAN("한국어로"),
    DONT_KNOW("모르겠어"),
    END_TODAY("오늘은 그만"),
    LISTEN_ONLY("듣기만 할게"),
    HELP("도움말"),
}

data class CommandMatch(val command: VoiceCommand, val matched: String)

/**
 * STT 결과 → 음성 명령. 순수 함수라 단위 테스트로 표현 범위를 고정한다(VoiceCommandParserTest).
 *
 * 같은 뜻을 여러 방식으로 말해도 알아듣도록 다섯 단계를 거친다.
 * 1. 정규화: 소문자, 문장 부호 제거, 축약형 펼치기(don't → do not), 앞뒤 군말(um, okay, please, 음, 저기) 제거,
 *    부탁 머리말(can you, could you, let's) 제거.
 * 2. 표현 사전: 명령마다 두 단어 이상의 구문(strong)과 한 단어(weak)를 둔다.
 *    - weak 는 발화 전체가 그 명령의 단어와 짝꿍 단어("one", "it", "again")로만 되어 있을 때만 쓴다.
 *      그래서 "next", "next one", "sorry, what?" 은 명령이고, "I skip breakfast" 는 대답이다.
 *    - strong 은 6단어 이하 발화면 어디에 있어도 명령으로 본다. 더 긴 발화면 맨 끝에 있거나,
 *      맨 앞에 있고 뒤에 3단어 이하만 붙을 때만 명령이다("Help me with the report, I need it by Friday" 는 대답).
 * 3. 오인식 보정: 단어 단위 편집 거리(5글자 이상 1글자, 8글자 이상 2글자)와 흔한 동음이의어(next → necks).
 * 4. 부정 거르기: 명령 구문 바로 앞에 not/never 가 오면("do not stop") 명령이 아니다. 한국어는 "~지 마", "안 ~".
 * 5. 대답과 구분: 발화가 이번 턴의 기대 답과 비슷하면(따라 말하기 문장 "Could you say that again?") 대답으로 둔다.
 *    너무 긴 발화(12단어 초과)도 대답으로 둔다.
 *
 * 한국어: STT 인식 언어가 영어라 한국어 명령이 한글로 오지 않을 수 있다. 한글로 오면(서버 STT·한국어 설정)
 * 띄어쓰기를 무시하고 어간으로 찾고, 영어 STT 가 받아 적는 흔한 로마자 표기(jamkkan, dasi)도 받는다.
 */
object VoiceCommandParser {

    /** 명령으로 볼 수 있는 최대 단어 수(군말 제거 후). 이보다 길면 대답이다. */
    const val MAX_COMMAND_WORDS = 16
    /** weak 단어로 명령이 되는 최대 단어 수. "next", "next one", "sorry what". */
    const val MAX_WEAK_WORDS = 3
    /** strong 구문이 발화 중간에 있어도 명령으로 보는 최대 단어 수. */
    const val MAX_ANYWHERE_WORDS = 6
    /** 긴 발화 맨 앞의 명령 뒤에 붙어도 되는 단어 수("Help me, I'm stuck"). */
    const val MAX_TAIL_WORDS = 3
    /** 기대 답과 이만큼 비슷하면 명령이 아니라 대답이다. */
    const val ANSWER_SIMILARITY = 0.6f
    /** 한국어 명령으로 볼 수 있는 최대 글자 수(공백 제외). */
    private const val MAX_KOREAN_CHARS = 20
    private const val MAX_KOREAN_WEAK_CHARS = 8

    fun parse(utterance: String, expectedAnswer: String? = null): CommandMatch? {
        val raw = normalizeBase(utterance)
        if (raw.isBlank()) return null
        if (expectedAnswer != null && englishSimilarity(raw, normalizeBase(expectedAnswer)) >= ANSWER_SIMILARITY) return null

        return parseKorean(raw) ?: parseEnglish(raw)
    }

    // ---------------------------------------------------------------- English

    private class Entry(val command: VoiceCommand, val tokens: List<String>, val strong: Boolean)

    private fun parseEnglish(text: String): CommandMatch? {
        val tokens = stripFillers(expandContractions(text.split(' ').filter { it.isNotBlank() }))
        if (tokens.isEmpty() || tokens.size > MAX_COMMAND_WORDS) return null

        var best: Pair<Entry, Int>? = null
        for (entry in ENTRIES) {
            val at = findSpan(tokens, entry.tokens) ?: continue
            if (entry.strong) {
                val end = at + entry.tokens.size
                // 긴 발화: 명령이 맨 끝에 있거나("…, can you say it again"), 맨 앞에 있고 뒤에 짧은 말만 붙을 때.
                val atEdge = end == tokens.size || (at <= 1 && tokens.size - end <= MAX_TAIL_WORDS)
                if (tokens.size > MAX_ANYWHERE_WORDS && !atEdge) continue
            } else {
                if (tokens.size > MAX_WEAK_WORDS) continue
                val allowed = WEAK_WORDS.getValue(entry.command) + WEAK_COMPANIONS
                if (!tokens.all { t -> allowed.any { tokenMatches(t, it) } }) continue
            }
            if (isNegated(tokens, at)) continue
            val current = best
            // 더 긴(구체적인) 구문, 같으면 strong, 같으면 사전 순서(앞쪽이 우선).
            if (current == null || better(entry, current.first)) best = entry to at
        }
        return best?.let { CommandMatch(it.first.command, it.first.tokens.joinToString(" ")) }
    }

    /** strong 이 weak 보다, 구체적인 명령(천천히·한국어로)이 일반적인 명령(다시·잠깐)보다, 긴 구문이 짧은 구문보다 우선. */
    private fun better(a: Entry, b: Entry): Boolean = when {
        a.strong != b.strong -> a.strong
        PRIORITY.getValue(a.command) != PRIORITY.getValue(b.command) -> PRIORITY.getValue(a.command) < PRIORITY.getValue(b.command)
        a.tokens.size != b.tokens.size -> a.tokens.size > b.tokens.size
        else -> false
    }

    private val PRIORITY = mapOf(
        VoiceCommand.SLOWER to 0, VoiceCommand.FASTER to 0, VoiceCommand.KOREAN to 1, VoiceCommand.DONT_KNOW to 2,
        VoiceCommand.LISTEN_ONLY to 3, VoiceCommand.END_TODAY to 4, VoiceCommand.HELP to 5, VoiceCommand.REPEAT to 6,
        VoiceCommand.NEXT to 6, VoiceCommand.PAUSE to 7,
    )

    /** [pattern] 이 [tokens] 안에 연속으로 나오는 첫 위치. 단어마다 오인식을 허용한다. */
    private fun findSpan(tokens: List<String>, pattern: List<String>): Int? {
        if (pattern.size > tokens.size) return null
        for (start in 0..tokens.size - pattern.size) {
            if (pattern.indices.all { tokenMatches(tokens[start + it], pattern[it]) }) return start
        }
        return null
    }

    private fun tokenMatches(heard: String, word: String): Boolean {
        if (heard == word) return true
        if (HOMOPHONES[heard] == word) return true
        val allowed = when {
            word.length >= 8 -> 2
            word.length >= 5 -> 1
            else -> 0
        }
        if (allowed == 0 || kotlin.math.abs(heard.length - word.length) > allowed) return false
        return editDistance(heard, word) <= allowed
    }

    private fun isNegated(tokens: List<String>, spanStart: Int): Boolean {
        val before = tokens.subList(maxOf(0, spanStart - 2), spanStart)
        return before.any { it in NEGATIONS }
    }

    private fun expandContractions(tokens: List<String>): List<String> = tokens.flatMap { t ->
        CONTRACTIONS[t] ?: when {
            t.endsWith("n't") -> listOf(t.removeSuffix("n't"), "not")
            t.endsWith("'re") -> listOf(t.removeSuffix("'re"), "are")
            t.endsWith("'ll") -> listOf(t.removeSuffix("'ll"), "will")
            t.endsWith("'s") -> listOf(t.removeSuffix("'s"), "is")
            t.endsWith("'m") -> listOf(t.removeSuffix("'m"), "am")
            else -> listOf(t.replace("'", ""))
        }
    }

    /** 군말은 어디서든, 부탁 머리말은 앞에서만 뗀다. */
    private fun stripFillers(tokens: List<String>): List<String> {
        var out = tokens.filter { it !in FILLERS_ANYWHERE }
        var changed = true
        while (changed && out.isNotEmpty()) {
            changed = false
            if (out.first() in FILLERS_EDGE) { out = out.drop(1); changed = true; continue }
            if (out.last() in FILLERS_EDGE) { out = out.dropLast(1); changed = true; continue }
            for (prefix in LEADING_PHRASES) {
                if (out.size > prefix.size && out.subList(0, prefix.size) == prefix) {
                    out = out.drop(prefix.size); changed = true; break
                }
            }
        }
        return out
    }

    // ---------------------------------------------------------------- Korean

    private class KoreanStem(val command: VoiceCommand, val stem: String, val weak: Boolean = false)

    private fun parseKorean(text: String): CommandMatch? {
        if (text.none { it in '가'..'힣' }) return null
        val compact = text.filter { it in '가'..'힣' }
        if (compact.isEmpty() || compact.length > MAX_KOREAN_CHARS) return null

        var best: KoreanStem? = null
        for (k in KOREAN_STEMS) {
            if (k.weak && compact.length > MAX_KOREAN_WEAK_CHARS) continue
            val at = compact.indexOf(k.stem)
            if (at < 0) continue
            if (koreanNegated(compact, at, k.stem)) continue
            val current = best
            if (current == null || k.stem.length > current.stem.length) best = k
        }
        return best?.let { CommandMatch(it.command, it.stem) }
    }

    /** "멈추지 마", "그만하지 말고", "안 멈춰" 처럼 부정이면 명령이 아니다. */
    private fun koreanNegated(compact: String, at: Int, stem: String): Boolean {
        val after = compact.substring(at + stem.length)
        if (KOREAN_NEGATION_AFTER.any { after.startsWith(it) || after.contains(it) && after.indexOf(it) <= 3 }) return true
        return at > 0 && compact[at - 1] == '안' && stem !in setOf("안들려", "안돼")
    }

    // ---------------------------------------------------------------- helpers

    private fun normalizeBase(text: String): String {
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase()
            .replace('’', '\'').replace('‘', '\'').replace('`', '\'')
        // 영문·숫자·한글·아포스트로피만 남기고 나머지는 공백.
        return nfc.map { c -> if (c.isLetterOrDigit() || c == '\'' || c in '가'..'힣') c else ' ' }
            .joinToString("").replace(Regex("\\s+"), " ").trim()
    }

    private fun englishSimilarity(a: String, b: String): Float {
        val x = a.split(' ').filter { it.isNotBlank() && it.any { c -> c in 'a'..'z' } }
        val y = b.split(' ').filter { it.isNotBlank() && it.any { c -> c in 'a'..'z' } }
        if (x.isEmpty() || y.isEmpty()) return 0f
        val dp = Array(x.size + 1) { IntArray(y.size + 1) }
        for (i in 1..x.size) for (j in 1..y.size) {
            dp[i][j] = if (x[i - 1].replace("'", "") == y[j - 1].replace("'", "")) dp[i - 1][j - 1] + 1
            else maxOf(dp[i - 1][j], dp[i][j - 1])
        }
        return 2f * dp[x.size][y.size] / (x.size + y.size)
    }

    private fun editDistance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }

    // ---------------------------------------------------------------- lexicon

    private val NEGATIONS = setOf("not", "never")
    private val WEAK_COMPANIONS = setOf("one", "it", "that", "this", "again", "the", "me", "a", "now", "sorry", "coach", "just")

    private val CONTRACTIONS = mapOf(
        "don't" to listOf("do", "not"), "dont" to listOf("do", "not"), "doesn't" to listOf("does", "not"),
        "didn't" to listOf("did", "not"), "didnt" to listOf("did", "not"), "can't" to listOf("can", "not"),
        "cant" to listOf("can", "not"), "cannot" to listOf("can", "not"), "won't" to listOf("will", "not"),
        "couldn't" to listOf("could", "not"), "i'm" to listOf("i", "am"), "im" to listOf("i", "am"),
        "i'll" to listOf("i", "will"), "ill" to listOf("i", "will"), "let's" to listOf("let", "us"), "lets" to listOf("let", "us"),
        "that's" to listOf("that", "is"), "thats" to listOf("that", "is"), "what's" to listOf("what", "is"),
        "whats" to listOf("what", "is"), "it's" to listOf("it", "is"), "i'd" to listOf("i", "would"),
        "gimme" to listOf("give", "me"), "wanna" to listOf("want", "to"), "gonna" to listOf("going", "to"),
        "sec" to listOf("second"),
    )

    private val FILLERS_ANYWHERE = setOf("um", "umm", "uh", "uhh", "erm", "er", "hmm", "hm", "ah", "please", "pls", "plz")
    private val FILLERS_EDGE = setOf(
        "okay", "ok", "oh", "so", "well", "hey", "coach", "alright", "right", "yeah", "yes", "then", "actually",
        "maybe", "the", "a", "thanks", "thank",
    )
    private val LEADING_PHRASES = listOf(
        "can you", "could you", "would you", "will you", "can we", "could we", "let us", "i want to",
        "i would like to", "would you mind", "do you mind", "excuse me", "thank you",
    ).map { it.split(' ') }

    /** STT 가 자주 헷갈리는 같은 소리 단어 → 사전 단어. */
    private val HOMOPHONES = mapOf(
        "necks" to "next", "nexed" to "next", "nest" to "next", "paws" to "pause", "pores" to "pause",
        "weight" to "wait", "repeats" to "repeat", "skipped" to "skip", "slowed" to "slow", "korea" to "korean",
        "hangul" to "korean",
    )

    private val ENTRIES: List<Entry> = buildList {
        fun strong(cmd: VoiceCommand, vararg phrases: String) = phrases.forEach { add(Entry(cmd, it.split(' '), true)) }
        fun weak(cmd: VoiceCommand, vararg words: String) = words.forEach { add(Entry(cmd, it.split(' '), false)) }

        // 구체적인 명령이 일반적인 명령보다 앞에 온다(같은 길이일 때 앞쪽 우선).
        strong(
            VoiceCommand.SLOWER, "slow down", "speak slower", "speak slowly", "speak more slowly",
            "talk slower", "talk slowly", "too fast", "so fast", "bit slower", "little slower", "more slowly",
            "say it slowly", "say it slower", "say that slowly", "say that slower",
        )
        weak(VoiceCommand.SLOWER, "slow", "slowly", "slower")
        strong(
            VoiceCommand.FASTER, "speed up", "speak faster", "talk faster", "too slow", "so slow",
            "bit faster", "little faster", "normal speed", "regular speed", "original speed", "speak normally",
        )
        weak(VoiceCommand.FASTER, "faster", "quicker", "quickly")
        strong(
            VoiceCommand.KOREAN, "in korean", "korean please", "say it in korean", "explain in korean", "korean translation",
            "what does that mean", "what does it mean", "what does this mean", "what is the meaning", "what is that mean",
            "what that means", "meaning of that", "translate it", "translate that", "translate please",
            "i do not understand", "do not understand", "i did not understand",
        )
        weak(VoiceCommand.KOREAN, "korean", "meaning", "translate", "translation", "hangugeoro", "hangukeoro", "hangugmallo")
        strong(
            VoiceCommand.DONT_KNOW, "i do not know", "do not know", "i dunno", "i have no idea", "no idea", "not sure", "i am not sure",
            "help me", "give me a hint", "hint please", "what should i say", "how do i say", "how do i say that",
            "how should i say", "i forgot it", "i forgot the word", "i forget the word", "i am stuck", "you go first", "you first",
            "show me how", "tell me the answer", "what is the answer", "i do not remember",
        )
        weak(VoiceCommand.DONT_KNOW, "hint", "help", "molla", "moreugesseo", "idk")
        strong(
            VoiceCommand.LISTEN_ONLY, "just listen", "i will just listen", "listen only", "only listen",
            "listening only", "i can not talk", "i can not speak", "i can not talk now", "i can not speak now",
            "i do not want to talk", "i do not want to speak", "no speaking", "someone is with me", "i have a passenger",
            "i am not alone", "let me listen", "let me just listen",
        )
        strong(
            VoiceCommand.END_TODAY, "that is enough", "that is enough for today", "enough for today", "enough for now",
            "stop here", "call it a day", "done for today", "i am done for today", "stop the lesson", "end the lesson",
            "finish the lesson", "end lesson", "stop lesson", "no more today", "that is all for today",
            "wrap up", "wrap it up", "i want to stop", "quit the lesson", "finish for today",
        )
        weak(VoiceCommand.END_TODAY, "enough", "quit", "finish", "geuman", "kuman")
        strong(
            VoiceCommand.REPEAT, "say that again", "say it again", "say again", "repeat that", "repeat it", "repeat the question",
            "one more time", "once more", "come again", "again please", "i did not catch that", "did not catch that",
            "i did not get that", "did not get that", "i did not hear", "did not hear", "can not hear", "could not hear",
            "what did you say", "what was that", "what was the question", "pardon me", "i beg your pardon",
            "say the question again", "play it again", "repeat please",
        )
        weak(VoiceCommand.REPEAT, "again", "repeat", "pardon", "what", "huh", "sorry", "dasi", "dashi")
        strong(
            VoiceCommand.NEXT, "skip this", "skip it", "skip this one", "skip that", "next one", "next question",
            "next please", "move on", "go next", "go to the next", "go to the next one", "pass this", "pass this one",
            "another question",
        )
        weak(VoiceCommand.NEXT, "next", "skip", "pass", "daeum")
        strong(
            VoiceCommand.HELP, "what can i say", "what can i do", "what are the commands", "voice commands",
            "list commands", "how does this work", "help commands", "show commands",
        )
        weak(VoiceCommand.HELP, "commands")
        strong(
            VoiceCommand.PAUSE, "hold on", "hang on", "give me a second", "give me a minute", "give me a moment",
            "one second", "one moment", "just a second", "just a moment", "wait a second", "wait a minute",
            "wait a moment", "pause the lesson", "pause it", "stop for now", "stop for a second",
        )
        weak(VoiceCommand.PAUSE, "wait", "stop", "hold", "pause", "freeze", "jamkkan", "jamkan", "chamkan")
    }

    private val WEAK_WORDS: Map<VoiceCommand, Set<String>> =
        ENTRIES.filter { !it.strong }.groupBy { it.command }.mapValues { (_, e) -> e.flatMap { it.tokens }.toSet() }
            .withDefault { emptySet() }

    private val KOREAN_STEMS: List<KoreanStem> = listOf(
        // 천천히 / 빠르게
        KoreanStem(VoiceCommand.SLOWER, "천천히"), KoreanStem(VoiceCommand.SLOWER, "느리게"),
        KoreanStem(VoiceCommand.SLOWER, "너무빨라"), KoreanStem(VoiceCommand.SLOWER, "빨라요", weak = true),
        KoreanStem(VoiceCommand.SLOWER, "느릿느릿"),
        KoreanStem(VoiceCommand.FASTER, "빠르게"), KoreanStem(VoiceCommand.FASTER, "원래속도"),
        KoreanStem(VoiceCommand.FASTER, "보통속도"), KoreanStem(VoiceCommand.FASTER, "너무느려"),
        KoreanStem(VoiceCommand.FASTER, "빨리말해"),
        // 한국어로
        KoreanStem(VoiceCommand.KOREAN, "한국어로"), KoreanStem(VoiceCommand.KOREAN, "한국말로"),
        KoreanStem(VoiceCommand.KOREAN, "우리말로"), KoreanStem(VoiceCommand.KOREAN, "무슨뜻"),
        KoreanStem(VoiceCommand.KOREAN, "뜻이뭐"), KoreanStem(VoiceCommand.KOREAN, "뜻알려"),
        KoreanStem(VoiceCommand.KOREAN, "해석"),
        KoreanStem(VoiceCommand.KOREAN, "번역"), KoreanStem(VoiceCommand.KOREAN, "무슨말"),
        KoreanStem(VoiceCommand.KOREAN, "이해가안"), KoreanStem(VoiceCommand.KOREAN, "이해못"),
        // 모르겠어
        KoreanStem(VoiceCommand.DONT_KNOW, "모르겠"), KoreanStem(VoiceCommand.DONT_KNOW, "몰라"),
        KoreanStem(VoiceCommand.DONT_KNOW, "모름", weak = true), KoreanStem(VoiceCommand.DONT_KNOW, "힌트"),
        KoreanStem(VoiceCommand.DONT_KNOW, "어떻게말해"), KoreanStem(VoiceCommand.DONT_KNOW, "어떻게말하"),
        KoreanStem(VoiceCommand.DONT_KNOW, "뭐라고해야"), KoreanStem(VoiceCommand.DONT_KNOW, "기억이안"),
        KoreanStem(VoiceCommand.DONT_KNOW, "생각이안"), KoreanStem(VoiceCommand.DONT_KNOW, "먼저해"),
        KoreanStem(VoiceCommand.DONT_KNOW, "정답알려"), KoreanStem(VoiceCommand.DONT_KNOW, "답알려"),
        KoreanStem(VoiceCommand.DONT_KNOW, "도와줘"), KoreanStem(VoiceCommand.DONT_KNOW, "도와주"),
        // 듣기만
        KoreanStem(VoiceCommand.LISTEN_ONLY, "듣기만"), KoreanStem(VoiceCommand.LISTEN_ONLY, "듣기모드"),
        KoreanStem(VoiceCommand.LISTEN_ONLY, "들을게"), KoreanStem(VoiceCommand.LISTEN_ONLY, "들으면서"),
        KoreanStem(VoiceCommand.LISTEN_ONLY, "말못해"), KoreanStem(VoiceCommand.LISTEN_ONLY, "말을못해"),
        KoreanStem(VoiceCommand.LISTEN_ONLY, "말하기싫"), KoreanStem(VoiceCommand.LISTEN_ONLY, "말안할"),
        KoreanStem(VoiceCommand.LISTEN_ONLY, "동승자"), KoreanStem(VoiceCommand.LISTEN_ONLY, "옆에사람"),
        // 오늘은 그만
        KoreanStem(VoiceCommand.END_TODAY, "오늘은그만"), KoreanStem(VoiceCommand.END_TODAY, "오늘그만"),
        KoreanStem(VoiceCommand.END_TODAY, "그만할래"), KoreanStem(VoiceCommand.END_TODAY, "그만하자"),
        KoreanStem(VoiceCommand.END_TODAY, "그만할게"), KoreanStem(VoiceCommand.END_TODAY, "그만해"),
        KoreanStem(VoiceCommand.END_TODAY, "끝내자"), KoreanStem(VoiceCommand.END_TODAY, "끝낼래"),
        KoreanStem(VoiceCommand.END_TODAY, "끝내줘"), KoreanStem(VoiceCommand.END_TODAY, "끝낼게"),
        KoreanStem(VoiceCommand.END_TODAY, "수업끝"), KoreanStem(VoiceCommand.END_TODAY, "이제그만"),
        KoreanStem(VoiceCommand.END_TODAY, "여기까지"), KoreanStem(VoiceCommand.END_TODAY, "마칠게"),
        KoreanStem(VoiceCommand.END_TODAY, "마치자"),
        // 다시
        KoreanStem(VoiceCommand.REPEAT, "다시"), KoreanStem(VoiceCommand.REPEAT, "한번더"),
        KoreanStem(VoiceCommand.REPEAT, "뭐라고"), KoreanStem(VoiceCommand.REPEAT, "못들었"),
        KoreanStem(VoiceCommand.REPEAT, "안들려"), KoreanStem(VoiceCommand.REPEAT, "잘못들"),
        KoreanStem(VoiceCommand.REPEAT, "반복"),
        // 다음
        KoreanStem(VoiceCommand.NEXT, "다음"), KoreanStem(VoiceCommand.NEXT, "넘어가"),
        KoreanStem(VoiceCommand.NEXT, "넘겨"), KoreanStem(VoiceCommand.NEXT, "건너뛰"),
        KoreanStem(VoiceCommand.NEXT, "패스"), KoreanStem(VoiceCommand.NEXT, "스킵"),
        KoreanStem(VoiceCommand.NEXT, "다른거"), KoreanStem(VoiceCommand.NEXT, "다른문제"),
        // 도움말
        KoreanStem(VoiceCommand.HELP, "명령어"), KoreanStem(VoiceCommand.HELP, "뭐라고말하면"),
        KoreanStem(VoiceCommand.HELP, "사용법"), KoreanStem(VoiceCommand.HELP, "뭐할수있"),
        KoreanStem(VoiceCommand.HELP, "어떻게써"),
        // 잠깐
        KoreanStem(VoiceCommand.PAUSE, "잠깐"), KoreanStem(VoiceCommand.PAUSE, "잠시만"),
        KoreanStem(VoiceCommand.PAUSE, "멈춰"), KoreanStem(VoiceCommand.PAUSE, "멈춤"),
        KoreanStem(VoiceCommand.PAUSE, "일시정지"), KoreanStem(VoiceCommand.PAUSE, "기다려"),
        KoreanStem(VoiceCommand.PAUSE, "스톱"), KoreanStem(VoiceCommand.PAUSE, "정지"),
        KoreanStem(VoiceCommand.PAUSE, "그만", weak = true), KoreanStem(VoiceCommand.PAUSE, "쉬자"),
        KoreanStem(VoiceCommand.PAUSE, "쉬었다"),
    )

    private val KOREAN_NEGATION_AFTER = listOf("지마", "지말", "면안돼", "지않")
}
