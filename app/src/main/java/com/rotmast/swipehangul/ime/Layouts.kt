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
    SELECT_ALL, CUT, PASTE, UNDO, JAMO, CURSOR_LEFT, CURSOR_RIGHT, TO_HANGUL, TO_ENGLISH,
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

/** HANGUL = 스와이프 한글, HANGUL_QWERTY = 한글 쿼티(두벌식) */
enum class Mode { HANGUL, HANGUL_QWERTY, ENGLISH, NUMBERS, SYMBOLS }

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
     *  전체선택  설정      기호   ㄱ  ㄴ  ㄷ  ⌫
     *  잘라내기  숫자      모음   ㄹ  ㅁ  ㅇ  !   (! 위로 &, 아래로 /)
     *  붙여넣기  되돌리기  반복   ㅂ  ㅅ  ㅈ  .   (. 위로 ?, 아래로 엔터)
     * (                    ㅋ    ㅌ  ㅊ  ㅍ  ㅎ)  ← 5줄 모드 (설정에서 켬)
     *  한/영     (         )      ←  ␣  →  ↵    (( 위로 ~, ) 위로 ,)
     *
     *  잘라내기를 오른쪽으로 밀면 복사.
     */
    fun hangul(fiveRows: Boolean): List<List<Key>> = grid(fiveRows, symbolPage = null)

    /**
     * 기호 자판: 한글 자판과 같은 모양에서 자음 9칸만 기호로 바뀐다.
     * 기호 키 자리는 '넘기기'(다음 기호 묶음), 한/영 자리는 '한글'(돌아가기).
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

        val symbolKey = if (symbolPage != null) {
            Key(KeyKind.PAGE, "${symbolPage + 1}/${SYMBOL_PAGES.size}")
        } else {
            Key(KeyKind.TO_SYMBOLS, "기호")
        }
        val bang = Key(KeyKind.CHAR, "!", flicks = mapOf(N to "&", S to "/"))
        val openParen = Key(KeyKind.CHAR, "(", flicks = mapOf(N to "~"))
        val closeParen = Key(KeyKind.CHAR, ")", flicks = mapOf(N to ","))
        val langKey = if (symbols != null) Key(KeyKind.TO_LETTERS) else Key(KeyKind.LANG, "한/영")

        add(listOf(Key(KeyKind.SELECT_ALL, "전체선택"), Key(KeyKind.SETTINGS, "설정"), symbolKey, cell(0, "ㄱ"), cell(1, "ㄴ"), cell(2, "ㄷ"), backspace))
        add(listOf(Key(KeyKind.CUT, "잘라내기"), Key(KeyKind.NUM_PAD, "5", flicks = NUM_PAD_DIGITS), Key(KeyKind.VOWEL_PAD, "모음"), cell(3, "ㄹ"), cell(4, "ㅁ"), cell(5, "ㅇ"), bang))
        add(listOf(Key(KeyKind.PASTE, "붙여넣기"), Key(KeyKind.UNDO, "되돌리기"), Key(KeyKind.REPEAT, "반복"), cell(6, "ㅂ"), cell(7, "ㅅ"), cell(8, "ㅈ"), period))
        if (fiveRows && symbols == null) add(listOf(Key(KeyKind.SPACER), Key(KeyKind.SPACER), c("ㅋ"), c("ㅌ"), c("ㅊ"), c("ㅍ"), c("ㅎ")))
        add(
            listOf(
                langKey, openParen, closeParen,
                Key(KeyKind.CURSOR_LEFT), space(1f), Key(KeyKind.CURSOR_RIGHT), enter,
            ),
        )
    }

    /** 쿼티 자판 아래 줄 (영문·한글 쿼티 공통). 한/영을 오른쪽으로 밀면 스와이프 한글 */
    private fun qwertyBottom() = listOf(
        Key(KeyKind.LANG, "한/영", weight = 1.5f),
        Key(KeyKind.TO_NUMBERS, "123", weight = 1.2f),
        t(","),
        space(4f),
        period,
        Key(KeyKind.ENTER, weight = 1.5f),
    )

    private fun jamo(s: String) = s.map { Key(KeyKind.JAMO, it.toString()) }

    /** 한글 쿼티 (두벌식): 영문 쿼티와 같은 모양 */
    fun hangulQwerty(): List<List<Key>> = listOf(
        chars("1234567890"),
        jamo("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ"),
        listOf(Key(KeyKind.SPACER, weight = 0.5f)) + jamo("ㅁㄴㅇㄹㅎㅗㅓㅏㅣ") + Key(KeyKind.SPACER, weight = 0.5f),
        listOf(Key(KeyKind.SHIFT, "⇧", weight = 1.5f)) + jamo("ㅋㅌㅊㅍㅠㅜㅡ") + Key(KeyKind.BACKSPACE, weight = 1.5f),
        qwertyBottom(),
    )

    /** 한글 쿼티에서 ⇧를 눌렀을 때 */
    val QWERTY_SHIFT = mapOf('ㅂ' to 'ㅃ', 'ㅈ' to 'ㅉ', 'ㄷ' to 'ㄸ', 'ㄱ' to 'ㄲ', 'ㅅ' to 'ㅆ', 'ㅐ' to 'ㅒ', 'ㅔ' to 'ㅖ')

    fun english(): List<List<Key>> = listOf(
        chars("1234567890"),
        chars("qwertyuiop"),
        listOf(Key(KeyKind.SPACER, weight = 0.5f)) + chars("asdfghjkl") + Key(KeyKind.SPACER, weight = 0.5f),
        listOf(Key(KeyKind.SHIFT, "⇧", weight = 1.5f)) + chars("zxcvbnm") + Key(KeyKind.BACKSPACE, weight = 1.5f),
        qwertyBottom(),
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
