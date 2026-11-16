package com.drivelingo.core.designsystem

/**
 * 큰 차량 디스플레이에서 앱 UI 전체를 키우는 배율.
 *
 * Pleos Connect 디스플레이(2560x1440)는 160dpi(mdpi)라서 1dp = 1px 이다. 앱의 크기 기준(본문 16sp 등)을
 * 그대로 쓰면 글자가 16px 로 나와, 본문이 약 28~30px 인 Pleos 시스템 UI 옆에서 너무 작고 화면 위쪽에 몰린다.
 * 그래서 디스플레이의 짧은 변이 [REFERENCE_SMALLEST_WIDTH_DP] 보다 크면 그 비율만큼 dp·sp 를 키운다.
 *
 * - 창 크기가 아니라 디스플레이 기준이다. 창을 2:1 ↔ 1:1 로 바꿔도 글자 크기가 같다.
 * - Pleos(짧은 변 1440dp) → 1.8배. 키운 뒤 가로 창은 약 934x672dp 라 기존 넓은 화면 레이아웃(720/840dp 기준)을 쓴다.
 * - 일반 AAOS 에뮬레이터(768dp)·휴대폰은 1배로 지금과 같다.
 */
object DisplayScale {
    /** 앱 크기 기준이 맞춰진 디스플레이의 짧은 변. 1440dp 에서 Pleos 시스템 UI 와 글자 크기가 맞도록 정했다. */
    const val REFERENCE_SMALLEST_WIDTH_DP = 800f
    const val MAX = 2f

    fun forSmallestWidth(smallestWidthDp: Int): Float =
        (smallestWidthDp / REFERENCE_SMALLEST_WIDTH_DP).coerceIn(1f, MAX)
}
