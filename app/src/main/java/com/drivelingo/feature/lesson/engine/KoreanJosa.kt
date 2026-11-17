package com.drivelingo.feature.lesson.engine

/** 한국어 조사 고르기. 코치가 한국어로 설명할 때 "…이라는/…라는"을 받침에 맞춘다. */
object KoreanJosa {
    /** 마지막 글자에 받침이 있으면 "이라", 없으면 "라". 한글이 아니면 "라". */
    fun ira(word: String): String {
        val last = word.trim().lastOrNull { it in '가'..'힣' } ?: return "라"
        return if ((last - '가') % 28 != 0) "이라" else "라"
    }
}
