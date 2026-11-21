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
        // 꺾인 곳이 없거나 둥글게 휘었을 뿐이면, 둥근 ㅘ·ㅝ인지 본다
        if (corner == null || !isSharpCorner(path, corner)) {
            roundedTurn(path)?.let { (f, second) -> return Gesture.Swipe(f, second, false, second) }
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
            ?.let { secondSpot(first, end.x - start.x, end.y - start.y) }
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

    /**
     * 빨리 쳐서 모서리가 둥글게 뭉개진 ㅘ·ㅝ (화→해 오타).
     * 똑바로 ↑ 출발해서 끝에는 수평 → 로 움직이고 있으면 ㅗ자리→ㅏ자리(ㅘ),
     * 똑바로 ↓ 출발해서 끝에는 ← 로 움직이고 있으면 ㅜ자리→ㅓ자리(ㅝ).
     * 처음부터 대각선이거나 끝까지 대각선으로 가는 ↗·↙(ㅐ·ㅡ)는 해당하지 않는다.
     */
    private fun roundedTurn(path: List<Pt>): Pair<Direction, Direction>? {
        val start = path[0]
        val end = path.last()
        val chord = dist(start, end)
        if (chord < tapSlop) return null
        val head = pointAt(path, 0.35f)
        val tail = pointAt(path, 0.7f)
        if (dist(start, head) < tapSlop * 0.5f || dist(tail, end) < tapSlop * 0.4f) return null
        val headDir = quantize(head.x - start.x, head.y - start.y)
        val tailDir = quantize(end.x - tail.x, end.y - tail.y)
        val pos = quantize(end.x - start.x, end.y - start.y)
        val bend = path.maxOf { deviation(start, end, it) } / chord
        if (bend < 0.12f) return null
        return when {
            headDir == Direction.N && tailDir == Direction.E && (pos == Direction.NE || pos == Direction.E) ->
                Direction.N to Direction.E
            headDir == Direction.S && tailDir == Direction.W && (pos == Direction.SW || pos == Direction.W) ->
                Direction.S to Direction.W
            else -> null
        }
    }

    /**
     * 꺾은 뒤 도착 자리. 위(ㅗ·ㅐ자리)에 갔다가 오른쪽으로 내려올 때는 엄지가 ㅏ자리보다 아래로 처지기 쉬우므로
     * ㅏ자리를 -42°까지 넓게 본다 (화가 회로 나오던 오타). 정확히 ㅣ자리(-45° 근처 이하)로 가면 여전히 ㅚ.
     */
    private fun secondSpot(first: Direction, dx: Float, dy: Float): Direction {
        val spot = quantize(dx, dy)
        if (spot == Direction.SE && (first == Direction.N || first == Direction.NE)) {
            val deg = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
            if (deg >= -42.0) return Direction.E
        }
        return spot
    }

    /** 꺾인 지점 앞뒤(경로 길이의 15%)만 봤을 때 55° 넘게 방향이 바뀌면 ㄱ자로 꺾은 것, 아니면 둥근 곡선 */
    private fun isSharpCorner(path: List<Pt>, corner: Pt): Boolean {
        val lengths = FloatArray(path.size)
        for (i in 1 until path.size) lengths[i] = lengths[i - 1] + dist(path[i - 1], path[i])
        val total = lengths.last()
        if (total == 0f) return true
        val ci = path.indexOf(corner)
        val at = lengths[ci]
        val before = path[lengths.indexOfFirst { it >= at - total * 0.15f }.coerceAtLeast(0)]
        val afterIndex = lengths.indexOfFirst { it >= at + total * 0.15f }
        val after = path[if (afterIndex < 0) path.lastIndex else afterIndex]
        val ax = corner.x - before.x
        val ay = corner.y - before.y
        val bx = after.x - corner.x
        val by = after.y - corner.y
        val la = hypot(ax, ay)
        val lb = hypot(bx, by)
        if (la == 0f || lb == 0f) return true
        val cos = ((ax * bx + ay * by) / (la * lb)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cos.toDouble())) > 55.0
    }

    /** 경로 길이의 [fraction] 지점에 가장 가까운 점 */
    private fun pointAt(path: List<Pt>, fraction: Float): Pt {
        var total = 0f
        for (i in 1 until path.size) total += dist(path[i - 1], path[i])
        val target = total * fraction
        var acc = 0f
        for (i in 1 until path.size) {
            acc += dist(path[i - 1], path[i])
            if (acc >= target) return path[i]
        }
        return path.last()
    }

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
