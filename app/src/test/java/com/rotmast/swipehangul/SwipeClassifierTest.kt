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

    @Test fun euReturnMakesUi() {
        // 스페셜: ㅡ자리(↙)에 갔다가 자음으로 돌아오면 ㅢ
        assertEquals('ㅢ', vowel(-30f to 30f, 30f to -30f))
        assertEquals('ㅢ', vowel(-32f to 28f, 26f to -20f))
        // ㅣ는 돌아와도 ㅣ
        assertEquals('ㅣ', vowel(30f to 30f, -30f to -30f))
    }

    @Test fun sloppyReturnStillIotizes() {
        // 정확히 반대가 아니어도 출발점 근처로 돌아오면 ㅑ
        assertEquals('ㅑ', vowel(45f to 0f, -38f to 14f))
        assertEquals('ㅛ', vowel(0f to -45f, 12f to 38f))
        // 반쯤만 돌아와도
        assertEquals('ㅕ', vowel(-50f to 0f, 30f to 0f))
    }

    /** 출발점 기준 자리들을 차례로 지나는 경로 (꺾기 = 첫 모음 자리 → 둘째 모음 자리) */
    private fun via(vararg spots: Pair<Float, Float>): Char {
        var prev = 0f to 0f
        val legs = spots.map { (x, y) -> (x - prev.first to y - prev.second).also { prev = x to y } }
        return vowel(*legs.toTypedArray())
    }

    @Test fun turnsBetweenVowelSpotsMakeCompoundVowels() {
        assertEquals('ㅘ', via(0f to -40f, 40f to 0f))     // ㅗ자리 → ㅏ자리
        assertEquals('ㅙ', via(0f to -40f, 30f to -30f))   // ㅗ자리 → ㅐ자리
        assertEquals('ㅚ', via(0f to -40f, 30f to 30f))    // ㅗ자리 → ㅣ자리
        assertEquals('ㅝ', via(0f to 40f, -40f to 0f))     // ㅜ자리 → ㅓ자리
        assertEquals('ㅞ', via(0f to 40f, -30f to -30f))   // ㅜ자리 → ㅔ자리
        assertEquals('ㅟ', via(0f to 40f, 30f to 30f))     // ㅜ자리 → ㅣ자리
        assertEquals('ㅢ', via(-30f to 30f, 30f to 30f))   // ㅡ자리 → ㅣ자리
    }

    @Test fun anthemTypo_gwaBecameGoe() {
        // '과·화'가 '괴·회'로 나오던 오타: ㅗ자리에서 ㅏ자리로 내려오며 이동
        assertEquals('ㅘ', via(0f to -45f, 42f to 5f))
        assertEquals('ㅘ', via(3f to -40f, 38f to -6f))
    }

    @Test fun anthem2_curvedDiagonalIsNotATurn() {
        // '해'가 '홰'로: 위로 출발해 오른쪽으로 휘는 ↗ 밀기는 ㅐ
        assertEquals('ㅐ', vowel(0f to -20f, 35f to -20f))
        assertEquals('ㅐ', vowel(5f to -18f, 15f to -12f, 15f to -8f))
        // 꺾인 지점이 뚜렷하면 여전히 ㅙ / ㅟ
        assertEquals('ㅙ', via(0f to -40f, 30f to -30f))
        assertEquals('ㅟ', via(0f to 40f, 30f to 30f))
    }

    @Test fun anthem2_leaningUpIsStillO() {
        // '도'가 '데'로: 왼쪽으로 기운 ↑ 밀기는 ㅗ
        assertEquals('ㅗ', vowel(-14f to -40f))
        assertEquals('ㅗ', vowel(-20f to -40f))
        // 살짝 위로 뜬 → 는 ㅏ ('하'가 '해'로)
        assertEquals('ㅏ', vowel(40f to -13f))
        // 진짜 대각선은 그대로
        assertEquals('ㅔ', vowel(-30f to -30f))
        assertEquals('ㅐ', vowel(30f to -30f))
    }

    @Test fun woIsUSpotThenEoSpot() {
        // '워·줘': ㅜ자리에 갔다가 ㅓ자리(자음 왼쪽)로 올라오며 이동
        assertEquals('ㅝ', via(0f to 45f, -42f to 5f))
        assertEquals('ㅝ', via(-3f to 40f, -38f to -6f))
        assertEquals('ㅝ', via(4f to 42f, -40f to 10f))
    }

    @Test fun anthemTypo_thumbDrift() {
        // '마·나·다'가 '미·니·디'로: 오른쪽으로 밀 때 엄지가 아래로 휨
        assertEquals('ㅏ', vowel(40f to 17f))
        assertEquals('ㅏ', vowel(35f to 6f, 10f to 8f))
        // '르'가 '러'로: 왼쪽 아래로 밀었는데 각도가 얕음
        assertEquals('ㅡ', vowel(-38f to 20f))
        // 그래도 대각선은 대각선
        assertEquals('ㅣ', vowel(30f to 34f))
        assertEquals('ㅓ', vowel(-40f to 4f))
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

    @Test fun shortTurnStillMakesCompoundVowel() {
        // ↓로 내려갔다가 ←로 조금만 꺾어도 ㅝ (ㅡ나 ㅜ가 되면 안 됨)
        assertEquals('ㅝ', vowel(0f to 40f, -15f to 0f))
        assertEquals('ㅝ', vowel(0f to 35f, -14f to 3f))
        assertEquals('ㅘ', vowel(0f to -40f, 15f to 0f))
    }

    @Test fun meaninglessTurnFallsBackToFirstVowel() {
        assertEquals('ㅏ', vowel(40f to 0f, 0f to 40f))
    }

    private fun swipe(first: Direction, second: Direction? = null) = Gesture.Swipe(first, second, false)

    @Test fun typoCandidates() {
        // 어(←) → 왼쪽 위 ㅔ, 왼쪽 아래 ㅡ
        assertEquals(listOf('ㅔ', 'ㅡ'), SwipeVowels.alternatives(swipe(Direction.W)))
        // 아(→) → ㅐ, ㅣ
        assertEquals(listOf('ㅐ', 'ㅣ'), SwipeVowels.alternatives(swipe(Direction.E)))
        // ㅘ(ㅗ자리→ㅏ자리) → ㅙ, ㅚ
        assertEquals(listOf('ㅙ', 'ㅚ'), SwipeVowels.alternatives(swipe(Direction.N, Direction.E)))
        // ㅝ(ㅜ자리→ㅓ자리) → ㅞ 포함
        assertTrue('ㅞ' in SwipeVowels.alternatives(swipe(Direction.S, Direction.W)))
        // ㅑ(→ 되돌아오기) → ㅒ, ㅏ 포함
        val ya = SwipeVowels.alternatives(swipe(Direction.E, Direction.W))
        assertTrue('ㅒ' in ya && 'ㅏ' in ya)
        // 현재 모음은 후보에 없음
        assertTrue('ㅓ' !in SwipeVowels.alternatives(swipe(Direction.W)))
    }

    /** 원호 경로: 중심 (cx, cy), 반지름 r, 각도 from→to (수학 각도, 화면은 y 아래로) */
    private fun arc(cx: Float, cy: Float, r: Float, from: Double, to: Double): List<Pt> =
        (0..24).map { i ->
            val a = Math.toRadians(from + (to - from) * i / 24)
            Pt(100f + cx + r * Math.cos(a).toFloat(), 100f + cy - r * Math.sin(a).toFloat())
        }

    @Test fun fastRoundedWaIsNotHae() {
        // 빨리 쳐서 둥글어진 ㅘ: ↑로 출발해 → 로 끝나는 원호 (화가 해로 나오던 오타)
        fun g(path: List<Pt>) = SwipeVowels.resolve(classifier.classify(path) as Gesture.Swipe)
        assertEquals('ㅘ', g(arc(50f, 0f, 50f, 180.0, 90.0)))
        assertEquals('ㅘ', g(arc(50f, 0f, 50f, 180.0, 70.0)))  // ㅏ자리 쪽으로 조금 내려옴
        assertEquals('ㅘ', g(arc(40f, 0f, 40f, 185.0, 95.0)))
        // 둥근 ㅝ: ↓로 출발해 ← 로 끝남
        assertEquals('ㅝ', g(arc(-50f, 0f, 50f, 0.0, -90.0)))
        // 진짜 ↗(ㅐ)는 그대로
        assertEquals('ㅐ', vowel(40f to -40f))
        assertEquals('ㅐ', vowel(0f to -20f, 35f to -20f))
    }

    @Test fun haeOffersHwa() {
        assertTrue('ㅘ' in SwipeVowels.alternatives(swipe(Direction.NE)))
        assertTrue('ㅘ' in SwipeVowels.alternatives(swipe(Direction.N, Direction.SE))) // 회 → 화
        assertTrue('ㅝ' in SwipeVowels.alternatives(swipe(Direction.SW)))
    }

    @Test fun waThroughHaeSpot() {
        // ㅗ자리까지 다 안 가고 ㅐ자리(↗)를 거쳐 ㅏ자리로 내려옴 → ㅘ (화가 해로 나오던 오타)
        assertEquals('ㅘ', via(30f to -30f, 40f to 0f))
        assertEquals('ㅘ', via(20f to -35f, 40f to 0f))
        assertEquals('ㅘ', via(28f to -32f, 45f to -6f))
        // ㅐ자리를 거쳐 ㅣ자리로 → ㅚ, ㅡ자리를 거쳐 ㅓ자리로 → ㅝ
        assertEquals('ㅚ', via(25f to -30f, 30f to 30f))
        assertEquals('ㅝ', via(-30f to 30f, -40f to 0f))
        // 그대로인 것들
        assertEquals('ㅐ', vowel(35f to -35f))
        assertEquals('ㅙ', via(0f to -40f, 30f to -30f))
        assertEquals('ㅢ', via(-30f to 30f, 30f to 30f))
    }

    @Test fun haeAlwaysOffersHwa() {
        assertTrue('ㅘ' in SwipeVowels.alternatives(swipe(Direction.NE)))
        assertTrue('ㅘ' in SwipeVowels.alternatives(swipe(Direction.NE, Direction.N)))
        // ㅐ자리 → ㅏ자리는 이제 바로 ㅘ
        assertEquals('ㅘ', SwipeVowels.resolve(swipe(Direction.NE, Direction.E)))
    }
}
