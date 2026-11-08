package com.rotmast.swipehangul.gesture

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** 8방향. 화면 좌표계(y가 아래로 증가)를 기준으로 계산한다. */
enum class Direction(val angleDeg: Double) {
    E(0.0), NE(45.0), N(90.0), NW(135.0), W(180.0), SW(225.0), S(270.0), SE(315.0);

    val unitX: Float = cos(Math.toRadians(angleDeg)).toFloat()
    val unitY: Float = -sin(Math.toRadians(angleDeg)).toFloat()

    val opposite: Direction get() = entries[(ordinal + 4) % 8]

    companion object {
        fun of(dx: Float, dy: Float): Direction {
            val deg = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
            val norm = (deg + 360.0) % 360.0
            return entries[((norm + 22.5) / 45.0).toInt() % 8]
        }
    }
}

data class Pt(val x: Float, val y: Float)

sealed interface Gesture {
    data object Tap : Gesture

    /**
     * @param first 처음 간 자리(방향)
     * @param second 꺾은 뒤 도착한 자리. 출발한 자음 기준 방향 (꺾지 않았으면 null)
     * @param long 첫 방향으로 길게 밀었는지
     * @param stroke 꺾은 뒤 손가락이 움직인 방향 (꺾임 지점 기준). [second]로 겹모음이 안 될 때 참고
     */
    data class Swipe(
        val first: Direction,
        val second: Direction?,
        val long: Boolean,
        val stroke: Direction? = null,
    ) : Gesture
}

/**
 * 터치 경로를 탭 / 스와이프(방향, 꺾임, 길이)로 분류한다.
 *
 * @param tapSlop 이보다 짧게 움직이면 탭
 * @param longDistance 첫 방향으로 이만큼 이상 밀면 "길게 밀기"
 */
class SwipeClassifier(var tapSlop: Float, var longDistance: Float) {

    /**
     * 8방향 판정. 각 방향이 시작하는 각도(수학 좌표, 반시계방향)를 오른손 엄지 습관에 맞춰 잡았다.
     *  - → 는 아래로 많이 처져도 →  (마→미 오타)
     *  - ↑ 는 왼쪽으로 기울어도 ↑  (도→데 오타)
     *  - ↙ 는 조금 얕아도 ↙        (르→러 오타)
     *  상하좌우(ㅏㅓㅗㅜ)는 넓게, 대각선(ㅐㅔㅣㅡ)은 조금 좁게.
     */
    fun quantize(dx: Float, dy: Float): Direction {
        val deg = (Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())) + 360.0) % 360.0
        return when {
            deg < 20.0 -> Direction.E
            deg < 62.0 -> Direction.NE
            deg < 122.0 -> Direction.N
            deg < 158.0 -> Direction.NW
            deg < 200.0 -> Direction.W
            deg < 245.0 -> Direction.SW
            deg < 295.0 -> Direction.S
            deg < 328.0 -> Direction.SE
            else -> Direction.E
        }
    }

    fun classify(path: List<Pt>): Gesture {
        if (path.size < 2) return Gesture.Tap
        val start = path[0]
        val end = path.last()
        val firstFar = path.firstOrNull { dist(it, start) >= tapSlop } ?: return Gesture.Tap
        val initial = quantize(firstFar.x - start.x, firstFar.y - start.y)

        var corner = findCorner(path, initial)
        // ㅙ(ㅗ→ㅐ)·ㅟ(ㅜ→ㅣ)처럼 45°만 꺾는 겹모음은 휘어진 대각선 밀기와 헷갈리므로
        // 꺾인 지점이 뚜렷할 때만 꺾기로 본다 (해→홰 오타)
        if (corner != null && dist(start, end) >= tapSlop) {
            val c = corner
            val f = quantize(c.x - start.x, c.y - start.y)
            val pos = quantize(end.x - start.x, end.y - start.y)
            if (pos != f && isAdjacent(f, pos) && deviation(start, end, c) < max(tapSlop * 0.8f, dist(start, end) * 0.25f)) {
                corner = null
            }
        }
        val first = if (corner == null) {
            quantize(end.x - start.x, end.y - start.y)
        } else {
            quantize(corner.x - start.x, corner.y - start.y)
        }
        // 꺾기는 "자리" 기준: ㅗ 자리(↑)에 갔다가 ㅏ 자리(자음 오른쪽)로 가면 ㅘ.
        // 도착 자리가 첫 자리와 같으면(짧게 꺾음) 꺾은 방향을 쓴다.
        val turned = corner?.takeIf { dist(it, end) >= turnSlop }
        val stroke = turned?.let { quantize(end.x - it.x, end.y - it.y) }?.takeIf { it != first }
        val second = turned
            ?.let { quantize(end.x - start.x, end.y - start.y) }
            ?.takeIf { it != first }
            ?: stroke

        val reach = path.maxOf { (it.x - start.x) * first.unitX + (it.y - start.y) * first.unitY }
        // 밀었다가 출발한 자음 쪽으로 되돌아왔으면 방향이 조금 틀어져도 "되돌아오기"로 본다
        if (reach >= tapSlop && dist(end, start) <= max(tapSlop, reach * 0.5f)) {
            return Gesture.Swipe(first, first.opposite, reach >= longDistance)
        }
        return Gesture.Swipe(first, second, reach >= longDistance, stroke)
    }

    /**
     * 꺾인 지점을 찾는다. 없으면 null.
     *  1) 시작→끝 직선에서 가장 멀리 벗어난 점이 충분히 멀면 그 점 (ㄱ자 꺾기)
     *  2) 처음 방향으로 가장 멀리 간 점이 끝점보다 충분히 앞서 있으면 그 점 (되돌아오기)
     */
    private fun findCorner(path: List<Pt>, initial: Direction): Pt? {
        val start = path[0]
        val end = path.last()
        val chord = dist(start, end)

        if (chord >= tapSlop) {
            var best: Pt? = null
            var bestDev = 0f
            for (p in path) {
                val dev = abs((end.x - start.x) * (p.y - start.y) - (end.y - start.y) * (p.x - start.x)) / chord
                if (dev > bestDev) {
                    bestDev = dev
                    best = p
                }
            }
            if (best != null && bestDev >= max(tapSlop * 0.4f, chord * 0.15f) &&
                dist(best, end) >= turnSlop && dist(best, start) >= tapSlop
            ) {
                return best
            }
        }

        val far = path.maxBy { (it.x - start.x) * initial.unitX + (it.y - start.y) * initial.unitY }
        return if (dist(far, end) >= tapSlop && dist(far, start) >= tapSlop) far else null
    }

    /** 꺾은 뒤 이만큼만 움직여도 꺾기로 본다 (짧게 꺾는 ㅝ·ㅘ 대비) */
    private val turnSlop: Float get() = tapSlop * 0.6f

    private fun isAdjacent(a: Direction, b: Direction): Boolean {
        val d = abs(a.ordinal - b.ordinal)
        return d == 1 || d == 7
    }

    /** 점 p가 직선 a→b에서 벗어난 거리 */
    private fun deviation(a: Pt, b: Pt, p: Pt): Float {
        val len = dist(a, b)
        if (len == 0f) return dist(a, p)
        return abs((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)) / len
    }

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
}
