package com.drivelingo.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayScaleTest {

    @Test
    fun `Pleos Connect 1440p 디스플레이는 1_8배`() {
        assertEquals(1.8f, DisplayScale.forSmallestWidth(1440), 0.001f)
    }

    @Test
    fun `일반 AAOS 에뮬레이터와 휴대폰은 그대로`() {
        assertEquals(1f, DisplayScale.forSmallestWidth(768), 0f)
        assertEquals(1f, DisplayScale.forSmallestWidth(411), 0f)
    }

    @Test
    fun `아주 큰 디스플레이도 2배를 넘지 않음`() {
        assertEquals(DisplayScale.MAX, DisplayScale.forSmallestWidth(2160), 0f)
    }
}
