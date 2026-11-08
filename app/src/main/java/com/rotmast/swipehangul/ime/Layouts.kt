package com.rotmast.swipehangul.ime

import android.graphics.RectF
import com.rotmast.swipehangul.gesture.Direction
import com.rotmast.swipehangul.gesture.Direction.N

enum class KeyKind {
    CONSONANT, CHAR, BACKSPACE, ENTER, SPACE, REPEAT, VOWEL_PAD,
    TO_NUMBERS, TO_SYMBOLS, LANG, TO_LETTERS, SETTINGS, PAGE, SHIFT, SPACER,
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
 *  - 가장 자주 쓰는 ⌫ 반복 . ↵ 는 맨 오른쪽 열
 *  - 스페이스는 아래 줄을 넓게 (좌우로 밀면 커서 이동)
 *  - 가끔 쓰는 한/영 123 기호 모음 , ? 는 엄지에서 먼 왼쪽 두 열
 *  - 맨 왼쪽 한 열은 비워 둔다 (7칸)
 */
object Layouts {
    /** 한글·숫자·기호 자판은 맨 왼쪽에 빈 칸을 하나 두어 키 전체를 오른쪽으로 민다 (7칸) */
    private fun rightAligned(rows: List<List<Key>>) = rows.map { listOf(Key(KeyKind.SPACER)) + it }

    private fun c(ch: String) = Key(KeyKind.CONSONANT, ch)
    private fun t(ch: String, weight: Float = 1f) = Key(KeyKind.CHAR, ch, weight = weight)
    private fun chars(s: String) = s.map { t(it.toString()) }

    private val backspace get() = Key(KeyKind.BACKSPACE)
    private val enter get() = Key(KeyKind.ENTER)
    private fun space(weight: Float) = Key(KeyKind.SPACE, text = " ", weight = weight)
    private val period get() = Key(KeyKind.CHAR, ".", flicks = mapOf(N to "!"))

    /**
     *  (빈칸) 한/영  123   ㄱ  ㄴ  ㄷ  ⌫
     *  (빈칸) 기호   모음  ㄹ  ㅁ  ㅇ  반복
     *  (빈칸) ,      ?     ㅂ  ㅅ  ㅈ  .
     * ((빈칸)        ㅋ    ㅌ  ㅊ  ㅍ  ㅎ)   ← 5줄 모드 (설정에서 켬)
     *  (빈칸) 설정   [    스페이스     ]  ↵
     */
    fun hangul(fiveRows: Boolean): List<List<Key>> = rightAligned(buildList {
        add(listOf(Key(KeyKind.LANG, "한/영"), Key(KeyKind.TO_NUMBERS, "123"), c("ㄱ"), c("ㄴ"), c("ㄷ"), backspace))
        add(listOf(Key(KeyKind.TO_SYMBOLS, "기호"), Key(KeyKind.VOWEL_PAD, "모음"), c("ㄹ"), c("ㅁ"), c("ㅇ"), Key(KeyKind.REPEAT, "반복")))
        add(listOf(t(","), t("?"), c("ㅂ"), c("ㅅ"), c("ㅈ"), period))
        if (fiveRows) add(listOf(Key(KeyKind.SPACER), c("ㅋ"), c("ㅌ"), c("ㅊ"), c("ㅍ"), c("ㅎ")))
        add(listOf(Key(KeyKind.SETTINGS, "설정"), space(4f), enter))
    })

    fun english(): List<List<Key>> = listOf(
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

    /** 전화 키패드처럼 1~9를 가운데 세 열에, 0은 8 아래 */
    fun numbers(): List<List<Key>> = rightAligned(listOf(
        listOf(Key(KeyKind.TO_LETTERS), t("+"), t("1"), t("2"), t("3"), backspace),
        listOf(Key(KeyKind.TO_SYMBOLS, "기호"), t("-"), t("4"), t("5"), t("6"), t(",")),
        listOf(t("("), t(")"), t("7"), t("8"), t("9"), t(".")),
        listOf(t("*"), t("/"), t("="), t("0"), space(1f), enter),
    ))

    fun symbols(page: Int): List<List<Key>> = rightAligned(if (page == 0) {
        listOf(
            chars("!?.,~") + backspace,
            chars("()@#&*"),
            chars("-_/:;") + Key(KeyKind.PAGE, "1/2"),
            listOf(Key(KeyKind.TO_LETTERS), t("'"), t("\""), space(2f), enter),
        )
    } else {
        listOf(
            chars("^%\$+=") + backspace,
            chars("<>[]{}"),
            chars("|\\`♡☆") + Key(KeyKind.PAGE, "2/2"),
            listOf(Key(KeyKind.TO_LETTERS), t("·"), t("…"), space(2f), enter),
        )
    })
}
