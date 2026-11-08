package com.rotmast.swipehangul.gesture

import com.rotmast.swipehangul.hangul.Jamo

/**
 * 스와이프 방향 → 모음.
 *
 *  ↖ㅔ   ↑ㅗ   ↗ㅐ
 *  ←ㅓ   (탭)   →ㅏ
 *  ↙ㅡ   ↓ㅜ   ↘ㅣ
 *
 *  - 밀었다가 자음으로 되돌아오기: ㅑ ㅕ ㅛ ㅠ ㅒ ㅖ
 *  - 꺾기: 두 모음을 합친다. ↑→ ㅘ, ↑↗ ㅙ, ↑↘ ㅚ, ↓← ㅝ, ↓↖ ㅞ, ↓↘ ㅟ, ↙↘ ㅢ
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

    fun resolve(g: Gesture.Swipe): Char {
        val v1 = base(g.first)
        val second = g.second
        if (second != null) {
            if (second == g.first.opposite) {
                Jamo.iotize(v1)?.let { return it }
            }
            Jamo.combineJung(v1, base(second))?.let { return it }
        }
        return v1
    }
}
