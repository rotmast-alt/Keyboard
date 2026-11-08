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
     * 오른손 엄지는 화면에서 시계방향으로 휘게 미는 경향이 있다
     * (→가 ↘로, ↙가 ←로 읽히던 오타). 측정한 각도를 이만큼 반시계방향으로 보정한다.
     */
    var angleBiasDeg = 10.0

    /**
     * 8방향 판정. 상하좌우(ㅏㅓㅗㅜ)는 ±27°, 대각선(ㅐㅔㅣㅡ)은 ±18°로
     * 자주 쓰는 상하좌우를 넓게 잡는다.
     */
    fun quantize(dx: Float, dy: Float): Direction {
        val deg = (Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())) + angleBiasDeg + 720.0) % 360.0
        for (d in Direction.entries) {
            val half = if (d.ordinal % 2 == 0) CARDINAL_HALF_DEG else DIAGONAL_HALF_DEG
            val diff = abs(((deg - d.angleDeg + 540.0) % 360.0) - 180.0)
            if (diff <= half) return d
        }
        return Direction.of(dx, dy)
    }

    fun classify(path: List<Pt>): Gesture {
        if (path.size < 2) return Gesture.Tap
        val start = path[0]
        val end = path.last()
        val firstFar = path.firstOrNull { dist(it, start) >= tapSlop } ?: return Gesture.Tap
        val initial = quantize(firstFar.x - start.x, firstFar.y - start.y)

        val corner = findCorner(path, initial)
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

    private companion object {
        const val CARDINAL_HALF_DEG = 27.0
        const val DIAGONAL_HALF_DEG = 18.0
    }

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
}
