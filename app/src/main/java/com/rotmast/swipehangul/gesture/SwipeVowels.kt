package com.rotmast.swipehangul.gesture

import com.rotmast.swipehangul.hangul.Jamo

/**
 * 스와이프 방향 → 모음.
 *
 *  ↖ㅔ   ↑ㅗ   ↗ㅐ
 *  ←ㅓ   (탭)   →ㅏ
 *  ↙ㅡ   ↓ㅜ   ↘ㅣ
 *
 *  - 밀었다가 자음으로 되돌아오기: ㅑ ㅕ ㅛ ㅠ ㅒ ㅖ, ㅡ는 ㅢ
 *  - 꺾기: 첫 모음 자리에 갔다가 둘째 모음 자리로 옮기면 합친다.
 *    ㅗ자리→ㅏ자리 ㅘ, ㅗ→ㅐ ㅙ, ㅗ→ㅣ ㅚ, ㅜ→ㅓ ㅝ, ㅜ→ㅔ ㅞ, ㅜ→ㅣ ㅟ, ㅡ→ㅣ ㅢ
 */
object SwipeVowels {
    fun base(d: Direction): Char = when (d) {
        Direction.E -> 'ㅏ'
        Direction.W -> 'ㅓ'
        Direction.N -> 'ㅗ'
        Direction.S -> 'ㅜ'
        Direction.SW -> 'ㅡ'
        Direction.SE -> 'ㅣ'
        Direction.NE -> 'ㅐ'
        Direction.NW -> 'ㅔ'
    }

    /** 밀었다가 자음으로 되돌아왔을 때: ㅏ→ㅑ ㅓ→ㅕ ㅗ→ㅛ ㅜ→ㅠ ㅐ→ㅒ ㅔ→ㅖ, 그리고 스페셜 ㅡ→ㅢ */
    fun returnVowel(v: Char): Char? = Jamo.iotize(v) ?: if (v == 'ㅡ') 'ㅢ' else null

    fun resolve(g: Gesture.Swipe): Char {
        val v1 = base(g.first)
        val second = g.second
        if (second != null) {
            if (second == g.first.opposite) {
                returnVowel(v1)?.let { return it }
            }
            Jamo.combineJung(v1, base(second))?.let { return it }
            g.stroke?.let { Jamo.combineJung(v1, base(it)) }?.let { return it }
            // 첫 자리가 대각선이면 ㅗ·ㅜ 자리까지 다 안 가고 질러간 것: 옆의 상하좌우 자리로 다시 본다
            // (ㅐ자리를 거쳐 ㅏ자리로 = ㅘ, 화가 해로 나오던 오타)
            if (g.first.ordinal % 2 == 1 && second != g.first.opposite) {
                for (c in neighbors(g.first)) {
                    if (c == second) continue
                    Jamo.combineJung(base(c), base(second))?.let { return it }
                }
            }
        }
        return v1
    }

    private val CONFUSABLE = mapOf('ㅐ' to 'ㅘ', 'ㅚ' to 'ㅘ', 'ㅙ' to 'ㅘ', 'ㅡ' to 'ㅝ', 'ㅞ' to 'ㅝ')

    private fun neighbors(d: Direction) = listOf(
        Direction.entries[(d.ordinal + 1) % 8],
        Direction.entries[(d.ordinal + 7) % 8],
    )

    /**
     * 오타 교정 후보: 실제로 민 방향의 바로 옆(±45°) 방향으로 밀었을 때 나왔을 모음들.
     *  - 단순 밀기: 옆 방향 모음 (어 → 에, 으). ↗·↙는 둥근 ㅘ·ㅝ도 (해 → 화, 으 → 워)
     *  - 꺾기(겹모음): 둘째 자리의 옆 자리 (ㅘ → ㅙ, ㅚ)
     *  - 되돌아오기: 옆 방향의 되돌아오기 + 되돌아오지 않은 모음 (ㅑ → ㅒ, ㅏ …)
     * 화면에서 왼쪽 방향 모음이 왼쪽에 오도록 방향의 x 순서(같으면 위쪽 먼저)로 정렬한다. 최대 [limit]개.
     */
    fun alternatives(g: Gesture.Swipe, limit: Int = 3): List<Char> {
        val current = resolve(g)
        val found = LinkedHashMap<Char, Direction>()
        fun add(alt: Gesture.Swipe, d: Direction) {
            val v = resolve(alt)
            if (v != current && v !in found) found[v] = d
        }
        val second = g.second
        when {
            second == null -> {
                // ↗(ㅐ)는 둥글게 친 ㅘ, ↙(ㅡ)는 둥글게 친 ㅝ일 수 있다 (해 → 화)
                when (g.first) {
                    Direction.NE -> add(Gesture.Swipe(Direction.N, Direction.E, false), Direction.E)
                    Direction.SW -> add(Gesture.Swipe(Direction.S, Direction.W, false), Direction.W)
                    else -> Unit
                }
                neighbors(g.first).forEach { add(Gesture.Swipe(it, null, false), it) }
            }
            second == g.first.opposite -> {
                neighbors(g.first).forEach { add(Gesture.Swipe(it, it.opposite, false), it) }
                add(Gesture.Swipe(g.first, null, false), g.first)
            }
            else -> neighbors(second).filter { it != g.first }.forEach { add(Gesture.Swipe(g.first, it, false), it) }
        }
        // 자주 헷갈리는 짝은 항상 후보에 넣는다 (해·회·홰 → 화, 으 → 워)
        CONFUSABLE[current]?.let { if (it !in found) found[it] = Direction.E }
        val sorted = found.entries.sortedWith(compareBy({ it.value.unitX }, { it.value.unitY }))
        var picked = sorted.take(limit)
        val must = CONFUSABLE[current]
        if (must != null && picked.none { it.key == must }) {
            picked = (picked.dropLast(1) + sorted.first { it.key == must })
                .sortedWith(compareBy({ it.value.unitX }, { it.value.unitY }))
        }
        return picked.map { it.key }
    }
}
