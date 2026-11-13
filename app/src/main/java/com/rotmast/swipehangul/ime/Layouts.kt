package com.rotmast.swipehangul.ime

import android.graphics.RectF
import com.rotmast.swipehangul.gesture.Direction
import com.rotmast.swipehangul.gesture.Direction.E
import com.rotmast.swipehangul.gesture.Direction.N
import com.rotmast.swipehangul.gesture.Direction.NE
import com.rotmast.swipehangul.gesture.Direction.NW
import com.rotmast.swipehangul.gesture.Direction.S
import com.rotmast.swipehangul.gesture.Direction.SE
import com.rotmast.swipehangul.gesture.Direction.SW
import com.rotmast.swipehangul.gesture.Direction.W

enum class KeyKind {
    CONSONANT, CHAR, BACKSPACE, ENTER, SPACE, REPEAT, VOWEL_PAD, NUM_PAD,
    TO_NUMBERS, TO_SYMBOLS, LANG, TO_LETTERS, SETTINGS, PAGE, SHIFT, SPACER,
    SELECT_ALL, COPY, PASTE, UNDO, CURSOR_LEFT, CURSOR_RIGHT, TO_HANGUL, TO_ENGLISH,
}

/**
 * @param flicks 키를 밀었을 때 방향별로 입력할 문자
 */
class Key(
    val kind: KeyKind,
    val label: String = "",
    val text: String = label,
    val weight: Float = 1f,
    val flicks: Map<Direction, String> = emptyMap(),
) {
    val rect = RectF()
}

enum class Mode { HANGUL, ENGLISH, NUMBERS, SYMBOLS }

/**
 * 오른손 엄지 기준으로 배치한다.
 *  - 가장 자주 쓰는 ⌫ ! . ↵ 는 맨 오른쪽 열
 *  - 스페이스는 ㅅ 아래, 커서 ← → 는 ㅂ·ㅈ 아래 (스페이스를 좌우로 밀어도 커서 이동)
 *  - 가끔 쓰는 한/영 숫자 기호 모음 , ? ~ 는 그 왼쪽 두 열
 *  - 가장 먼 맨 왼쪽 열에는 드물게 쓰는 편집 키(전체선택·복사·붙여넣기)와 설정
 */
object Layouts {
    private fun c(ch: String) = Key(KeyKind.CONSONANT, ch)
    private fun t(ch: String, weight: Float = 1f) = Key(KeyKind.CHAR, ch, weight = weight)
    private fun chars(s: String) = s.map { t(it.toString()) }

    private val backspace get() = Key(KeyKind.BACKSPACE)
    private val enter get() = Key(KeyKind.ENTER)
    private fun space(weight: Float) = Key(KeyKind.SPACE, text = " ", weight = weight)
    /** 밀기 문자로 이 값이 나오면 엔터키를 누른 것과 같다 */
    const val ENTER_FLICK = "\n"

    /** . 키: 위로 밀면 ?, 아래로 밀면 엔터 */
    private val period get() = Key(KeyKind.CHAR, ".", flicks = mapOf(N to "?", S to ENTER_FLICK))

    /** 숫자 키: 탭 5, 전화 키패드 배치대로 밀기. ↓를 길게 밀거나 ↓ 갔다 돌아오면 0 */
    val NUM_PAD_DIGITS = mapOf(
        NW to "1", N to "2", NE to "3",
        W to "4", E to "6",
        SW to "7", S to "8", SE to "9",
    )

    /**
     *  전체선택  기호      설정   ㄱ  ㄴ  ㄷ  ⌫
     *  복사      숫자      모음   ㄹ  ㅁ  ㅇ  !
     *  붙여넣기  되돌리기  반복   ㅂ  ㅅ  ㅈ  .   (. 위로 밀면 ?, 아래로 밀면 엔터)
     * (                    ㅋ    ㅌ  ㅊ  ㅍ  ㅎ)   ← 5줄 모드 (설정에서 켬)
     *  한/영     ~         ,      ←  ␣  →  ↵
     */
    fun hangul(fiveRows: Boolean): List<List<Key>> = grid(fiveRows, symbolPage = null)

    /**
     * 기호 자판: 한글 자판과 같은 모양에서 자음 9칸만 기호로 바뀐다.
     * 기호 키 자리는 '한글'(돌아가기), 설정 자리는 '넘기기'(다음 기호 묶음).
     */
    fun symbols(page: Int): List<List<Key>> = grid(fiveRows = false, symbolPage = page)

    /** 자음 9칸에 들어가는 기호 묶음 (ㄱㄴㄷ / ㄹㅁㅇ / ㅂㅅㅈ 순서) */
    val SYMBOL_PAGES = listOf(
        listOf("!", "@", "#", "$", "%", "^", "&", "*", "~"),
        listOf("(", ")", "-", "_", "+", "=", "/", ":", ";"),
        listOf("'", "\"", "\\", "|", "`", "₩", "<", ">", "°"),
        listOf("[", "]", "{", "}", "·", "…", "※", "♡", "♥"),
        listOf("☆", "★", "→", "←", "↑", "↓", "○", "●", "◎"),
    )

    private fun grid(fiveRows: Boolean, symbolPage: Int?): List<List<Key>> = buildList {
        val symbols = symbolPage?.let { SYMBOL_PAGES[it] }
        fun cell(index: Int, consonant: String) = if (symbols != null) t(symbols[index]) else c(consonant)

        val modeKey = if (symbols != null) Key(KeyKind.TO_LETTERS) else Key(KeyKind.TO_SYMBOLS, "기호")
        val thirdKey = if (symbolPage != null) {
            Key(KeyKind.PAGE, "${symbolPage + 1}/${SYMBOL_PAGES.size}")
        } else {
            Key(KeyKind.SETTINGS, "설정")
        }
        add(listOf(Key(KeyKind.SELECT_ALL, "전체선택"), modeKey, thirdKey, cell(0, "ㄱ"), cell(1, "ㄴ"), cell(2, "ㄷ"), backspace))
        add(listOf(Key(KeyKind.COPY, "복사"), Key(KeyKind.NUM_PAD, "5", flicks = NUM_PAD_DIGITS), Key(KeyKind.VOWEL_PAD, "모음"), cell(3, "ㄹ"), cell(4, "ㅁ"), cell(5, "ㅇ"), t("!")))
        add(listOf(Key(KeyKind.PASTE, "붙여넣기"), Key(KeyKind.UNDO, "되돌리기"), Key(KeyKind.REPEAT, "반복"), cell(6, "ㅂ"), cell(7, "ㅅ"), cell(8, "ㅈ"), period))
        if (fiveRows) add(listOf(Key(KeyKind.SPACER), Key(KeyKind.SPACER), c("ㅋ"), c("ㅌ"), c("ㅊ"), c("ㅍ"), c("ㅎ")))
        add(
            listOf(
                Key(KeyKind.LANG, "한/영"), t("~"), t(","),
                Key(KeyKind.CURSOR_LEFT), space(1f), Key(KeyKind.CURSOR_RIGHT), enter,
            ),
        )
    }

    fun english(): List<List<Key>> = listOf(
        chars("1234567890"),
        chars("qwertyuiop"),
        listOf(Key(KeyKind.SPACER, weight = 0.5f)) + chars("asdfghjkl") + Key(KeyKind.SPACER, weight = 0.5f),
        listOf(Key(KeyKind.SHIFT, "⇧", weight = 1.5f)) + chars("zxcvbnm") + Key(KeyKind.BACKSPACE, weight = 1.5f),
        listOf(
            Key(KeyKind.LANG, "한/영", weight = 1.5f),
            Key(KeyKind.TO_NUMBERS, "123", weight = 1.2f),
            t(","),
            space(4f),
            period,
            Key(KeyKind.ENTER, weight = 1.5f),
        ),
    )

    /**
     * 숫자 자판 (영문 123 키, 숫자 키 길게 누르기). 왼쪽 열은 다른 자판과 같은 자리에 모드 전환 키.
     *
     *  붙여넣기  +  -   1  2  3  ⌫
     *  기호      *  /   4  5  6  ␣
     *  ABC       (  )   7  8  9  .
     *  한글      =  %   ,  0  :  ↵
     */
    fun numbers(): List<List<Key>> = listOf(
        listOf(Key(KeyKind.PASTE, "붙여넣기"), t("+"), t("-"), t("1"), t("2"), t("3"), backspace),
        listOf(Key(KeyKind.TO_SYMBOLS, "기호"), t("*"), t("/"), t("4"), t("5"), t("6"), space(1f)),
        listOf(Key(KeyKind.TO_ENGLISH, "ABC"), t("("), t(")"), t("7"), t("8"), t("9"), period),
        listOf(Key(KeyKind.TO_HANGUL, "한글"), t("="), t("%"), t(","), t("0"), t(":"), enter),
    )
}
