package com.rotmast.swipehangul.gesture

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** 8방향. 화면 좌표계(y가 아래로 증가)를 기준으로 계산한다. */
enum class Direction(angleDeg: Double) {
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
     * @param first 처음 밀어낸 방향
     * @param second 꺾은 뒤의 방향 (꺾지 않았으면 null)
     * @param long 첫 방향으로 길게 밀었는지
     */
    data class Swipe(val first: Direction, val second: Direction?, val long: Boolean) : Gesture
}

/**
 * 터치 경로를 탭 / 스와이프(방향, 꺾임, 길이)로 분류한다.
 *
 * @param tapSlop 이보다 짧게 움직이면 탭
 * @param longDistance 첫 방향으로 이만큼 이상 밀면 "길게 밀기"
 */
class SwipeClassifier(var tapSlop: Float, var longDistance: Float) {

    fun classify(path: List<Pt>): Gesture {
        if (path.size < 2) return Gesture.Tap
        val start = path[0]
        val end = path.last()
        val firstFar = path.firstOrNull { dist(it, start) >= tapSlop } ?: return Gesture.Tap
        val initial = Direction.of(firstFar.x - start.x, firstFar.y - start.y)

        val corner = findCorner(path, initial)
        val first = if (corner == null) {
            Direction.of(end.x - start.x, end.y - start.y)
        } else {
            Direction.of(corner.x - start.x, corner.y - start.y)
        }
        val second = corner?.let { Direction.of(end.x - it.x, end.y - it.y) }?.takeIf { it != first }

        val reach = path.maxOf { (it.x - start.x) * first.unitX + (it.y - start.y) * first.unitY }
        // 밀었다가 출발한 자음 쪽으로 되돌아왔으면 방향이 조금 틀어져도 "되돌아오기"로 본다
        if (reach >= tapSlop && dist(end, start) <= max(tapSlop, reach * 0.5f)) {
            return Gesture.Swipe(first, first.opposite, reach >= longDistance)
        }
        return Gesture.Swipe(first, second, reach >= longDistance)
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
            if (best != null && bestDev >= max(tapSlop * 0.5f, chord * 0.15f) &&
                dist(best, end) >= tapSlop && dist(best, start) >= tapSlop
            ) {
                return best
            }
        }

        val far = path.maxBy { (it.x - start.x) * initial.unitX + (it.y - start.y) * initial.unitY }
        return if (dist(far, end) >= tapSlop && dist(far, start) >= tapSlop) far else null
    }

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
}
