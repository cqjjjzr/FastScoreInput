package com.github.charlie.scoreinput

import org.junit.Assert.assertEquals
import org.junit.Test

class MainFrameInitKtTest {
    @Test
    fun testParse() {
        "100" shouldGet 100.0f
        "100.0" shouldGet 100.0f
        "1000" shouldGet 100.0f
        "99.9" shouldGet 99.9f
        "785" shouldGet 78.5f
        "10.0" shouldGet 10.0f
        "1235" shouldGet 123.5f
    }

    private infix fun String.shouldGet(f: Float) = assertEquals(parseScore(this), f)
}