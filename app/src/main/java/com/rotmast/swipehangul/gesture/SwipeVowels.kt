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
}
