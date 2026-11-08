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
    CONSONANT, CHAR, BACKSPACE, ENTER, SPACE, REPEAT, VOWEL_MODE,
    CURSOR_LEFT, CURSOR_RIGHT, TO_NUMBERS, TO_SYMBOLS, TO_ENGLISH, TO_LETTERS,
    ROWS, PAGE, SHIFT, SPACER,
}

/**
 * @param flicks 키를 밀었을 때 방향별로 입력할 문자 (숫자·기호·문장부호 키)
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

object Layouts {
    /**
     * 오른손으로 치기 편하도록 한글·숫자·기호 자판은 6칸으로 하고,
     * 맨 왼쪽 칸은 비워 키 전체를 오른쪽으로 한 칸 민다.
     */
    private fun rightAligned(rows: List<List<Key>>) = rows.map { listOf(Key(KeyKind.SPACER)) + it }

    private fun c(ch: String) = Key(KeyKind.CONSONANT, ch)
    private fun t(ch: String, weight: Float = 1f) = Key(KeyKind.CHAR, ch, weight = weight)
    private fun chars(s: String) = s.map { t(it.toString()) }

    private val backspace get() = Key(KeyKind.BACKSPACE)
    private val space get() = Key(KeyKind.SPACE, text = " ")
    private val enter get() = Key(KeyKind.ENTER, flicks = mapOf(NW to "'", NE to "\"", W to "~"))

    /**
     *  (빈칸) Eng   ㄱ  ㄴ  ㄷ  ⌫
     *  (빈칸) 숫자  ㄹ  ㅁ  ㅇ  반복
     *  (빈칸) 기호  ㅂ  ㅅ  ㅈ  모음
     *  (빈칸) ㅋ    ㅌ  ㅊ  ㅍ  ㅎ    ← 5줄 모드
     *  (빈칸) 4/5줄 ⇦  ␣  ⇨  ↵
     */
    fun hangul(fiveRows: Boolean): List<List<Key>> = rightAligned(buildList {
        add(listOf(Key(KeyKind.TO_ENGLISH, "Eng"), c("ㄱ"), c("ㄴ"), c("ㄷ"), backspace))
        add(
            listOf(
                Key(KeyKind.TO_NUMBERS, "숫자", flicks = mapOf(N to "1", NE to "2", E to "3", SE to "4", S to "5")),
                c("ㄹ"), c("ㅁ"), c("ㅇ"),
                Key(KeyKind.REPEAT, "반복"),
            ),
        )
        add(
            listOf(
                Key(KeyKind.TO_SYMBOLS, "기호", flicks = mapOf(N to "6", NE to "7", E to "8", SE to "9", S to "0")),
                c("ㅂ"), c("ㅅ"), c("ㅈ"),
                Key(KeyKind.VOWEL_MODE, "모음", flicks = mapOf(NW to "^", NE to "?", W to ",", E to ".", SW to "!")),
            ),
        )
        if (fiveRows) add(listOf(c("ㅋ"), c("ㅌ"), c("ㅊ"), c("ㅍ"), c("ㅎ")))
        add(listOf(Key(KeyKind.ROWS), Key(KeyKind.CURSOR_LEFT), space, Key(KeyKind.CURSOR_RIGHT), enter))
    })

    fun english(): List<List<Key>> = listOf(
        chars("qwertyuiop"),
        listOf(Key(KeyKind.SPACER, weight = 0.5f)) + chars("asdfghjkl") + Key(KeyKind.SPACER, weight = 0.5f),
        listOf(Key(KeyKind.SHIFT, "⇧", weight = 1.5f)) + chars("zxcvbnm") + Key(KeyKind.BACKSPACE, weight = 1.5f),
        listOf(
            Key(KeyKind.TO_LETTERS, "한글", weight = 1.5f),
            Key(KeyKind.TO_NUMBERS, "123", weight = 1.2f),
            t(","),
            Key(KeyKind.SPACE, text = " ", weight = 4f),
            t("."),
            Key(KeyKind.ENTER, weight = 1.5f),
        ),
    )

    fun numbers(): List<List<Key>> = rightAligned(listOf(
        listOf(t("+"), t("1"), t("2"), t("3"), backspace),
        listOf(t("-"), t("4"), t("5"), t("6"), t(",")),
        listOf(t("*"), t("7"), t("8"), t("9"), t(".")),
        listOf(Key(KeyKind.TO_LETTERS), t("/"), t("0"), space, enter),
    ))

    fun symbols(page: Int): List<List<Key>> = rightAligned(if (page == 0) {
        listOf(
            chars("!?.,") + backspace,
            chars("()@#") + Key(KeyKind.PAGE, "1/2"),
            chars("-_/:;"),
            listOf(Key(KeyKind.TO_LETTERS), t("'"), t("\""), space, enter),
        )
    } else {
        listOf(
            chars("~^*&") + backspace,
            chars("+=%$") + Key(KeyKind.PAGE, "2/2"),
            chars("<>[]|"),
            listOf(Key(KeyKind.TO_LETTERS), t("{"), t("}"), space, enter),
        )
    })
}
