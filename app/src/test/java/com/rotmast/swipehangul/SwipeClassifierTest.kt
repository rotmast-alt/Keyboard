package com.rotmast.swipehangul

import com.rotmast.swipehangul.gesture.Direction
import com.rotmast.swipehangul.gesture.Gesture
import com.rotmast.swipehangul.gesture.Pt
import com.rotmast.swipehangul.gesture.SwipeClassifier
import com.rotmast.swipehangul.gesture.SwipeVowels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeClassifierTest {
    private val classifier = SwipeClassifier(tapSlop = 20f, longDistance = 80f)

    /** (dx, dy) 꼭짓점들을 잇는 경로를 촘촘한 점으로 만든다. 화면 좌표: y는 아래가 +. */
    private fun path(vararg corners: Pair<Float, Float>): List<Pt> {
        val pts = mutableListOf(Pt(100f, 100f))
        var cur = Pt(100f, 100f)
        for ((dx, dy) in corners) {
            val target = Pt(cur.x + dx, cur.y + dy)
            for (i in 1..10) pts.add(Pt(cur.x + dx * i / 10, cur.y + dy * i / 10))
            cur = target
        }
        return pts
    }

    private fun vowel(vararg corners: Pair<Float, Float>): Char {
        val g = classifier.classify(path(*corners))
        assertTrue("expected swipe but was $g", g is Gesture.Swipe)
        return SwipeVowels.resolve(g as Gesture.Swipe)
    }

    @Test fun tap() {
        assertEquals(Gesture.Tap, classifier.classify(path(5f to 3f)))
        assertEquals(Gesture.Tap, classifier.classify(listOf(Pt(0f, 0f))))
    }

    @Test fun directions() {
        assertEquals(Direction.E, Direction.of(10f, 1f))
        assertEquals(Direction.N, Direction.of(0f, -10f))
        assertEquals(Direction.SW, Direction.of(-10f, 10f))
        assertEquals(Direction.NE, Direction.of(10f, -10f))
    }

    @Test fun eightBasicVowels() {
        assertEquals('ㅏ', vowel(40f to 0f))
        assertEquals('ㅓ', vowel(-40f to 0f))
        assertEquals('ㅗ', vowel(0f to -40f))
        assertEquals('ㅜ', vowel(0f to 40f))
        assertEquals('ㅣ', vowel(30f to 30f))
        assertEquals('ㅡ', vowel(-30f to 30f))
        assertEquals('ㅐ', vowel(30f to -30f))
        assertEquals('ㅔ', vowel(-30f to -30f))
    }

    @Test fun slightlyCurvedSwipeKeepsDirection() {
        assertEquals('ㅏ', vowel(40f to 8f, 10f to -6f))
    }

    @Test fun longSwipeKeepsBaseVowel() {
        assertEquals('ㅏ', vowel(100f to 0f))
        assertEquals('ㅗ', vowel(0f to -100f))
        assertEquals('ㅣ', vowel(70f to 70f))
    }

    @Test fun swipeAndReturnIotizes() {
        assertEquals('ㅑ', vowel(40f to 0f, -40f to 0f))
        assertEquals('ㅠ', vowel(0f to 40f, 0f to -40f))
        assertEquals('ㅛ', vowel(0f to -40f, 0f to 40f))
        assertEquals('ㅕ', vowel(-40f to 0f, 40f to 0f))
        assertEquals('ㅒ', vowel(30f to -30f, -30f to 30f))
    }

    @Test fun sloppyReturnStillIotizes() {
        // 정확히 반대가 아니어도 출발점 근처로 돌아오면 ㅑ
        assertEquals('ㅑ', vowel(45f to 0f, -38f to 14f))
        assertEquals('ㅛ', vowel(0f to -45f, 12f to 38f))
        // 반쯤만 돌아와도
        assertEquals('ㅕ', vowel(-50f to 0f, 30f to 0f))
    }

    @Test fun turnsMakeCompoundVowels() {
        assertEquals('ㅘ', vowel(0f to -40f, 40f to 0f))
        assertEquals('ㅚ', vowel(0f to -40f, 30f to 30f))
        assertEquals('ㅝ', vowel(0f to 40f, -40f to 0f))
        assertEquals('ㅟ', vowel(0f to 40f, 30f to 30f))
        assertEquals('ㅞ', vowel(0f to 40f, -30f to -30f))
        assertEquals('ㅢ', vowel(-30f to 30f, 30f to 30f))
    }

    @Test fun gentleArcIsNotATurn() {
        // 살짝 휘어진 긴 스와이프는 꺾기가 아니다
        val arc = (0..20).map { i ->
            val t = i / 20f
            Pt(100f, 100f + 100f * t).let { Pt(it.x + 40f * t * (1 - t), it.y) }
        }
        val g = classifier.classify(arc) as Gesture.Swipe
        assertEquals('ㅜ', SwipeVowels.resolve(g))
    }

    @Test fun meaninglessTurnFallsBackToFirstVowel() {
        assertEquals('ㅏ', vowel(40f to 0f, 0f to 40f))
    }
}
