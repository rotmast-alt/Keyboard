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
        }
        return v1
    }

    private fun neighbors(d: Direction) = listOf(
        Direction.entries[(d.ordinal + 1) % 8],
        Direction.entries[(d.ordinal + 7) % 8],
    )

    /**
     * 오타 교정 후보: 실제로 민 방향의 바로 옆(±45°) 방향으로 밀었을 때 나왔을 모음들.
     *  - 단순 밀기: 옆 방향 모음 (어 → 에, 으)
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
            second == null -> neighbors(g.first).forEach { add(Gesture.Swipe(it, null, false), it) }
            second == g.first.opposite -> {
                neighbors(g.first).forEach { add(Gesture.Swipe(it, it.opposite, false), it) }
                add(Gesture.Swipe(g.first, null, false), g.first)
            }
            else -> neighbors(second).filter { it != g.first }.forEach { add(Gesture.Swipe(g.first, it, false), it) }
        }
        return found.entries
            .sortedWith(compareBy({ it.value.unitX }, { it.value.unitY }))
            .take(limit)
            .map { it.key }
    }
}
