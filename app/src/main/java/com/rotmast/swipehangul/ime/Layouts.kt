package com.rotmast.swipehangul.ime

import android.graphics.RectF

enum class KeyKind { CONSONANT, VOWEL_PAD, CHAR, SHIFT, BACKSPACE, ENTER, SPACE, SYMBOLS, LANG, SPACER }

class Key(
    val kind: KeyKind,
    val label: String,
    val text: String = label,
    val weight: Float = 1f,
) {
    val rect = RectF()
}

enum class Mode { HANGUL, ENGLISH, SYMBOLS }

object Layouts {
    private fun c(ch: String) = Key(KeyKind.CONSONANT, ch)
    private fun t(ch: String, weight: Float = 1f) = Key(KeyKind.CHAR, ch, weight = weight)
    private fun chars(s: String) = s.map { t(it.toString()) }

    private fun bottomRow(symbolLabel: String) = listOf(
        Key(KeyKind.SYMBOLS, symbolLabel, weight = 1.5f),
        Key(KeyKind.LANG, "한/영", weight = 1.2f),
        t(","),
        Key(KeyKind.SPACE, "", " ", weight = 4f),
        t("."),
        Key(KeyKind.ENTER, "↵", weight = 1.5f),
    )

    fun hangul(): List<List<Key>> = listOf(
        listOf(c("ㅂ"), c("ㅈ"), c("ㄷ"), c("ㄱ"), c("ㅅ"), Key(KeyKind.BACKSPACE, "⌫")),
        listOf(c("ㅁ"), c("ㄴ"), c("ㅇ"), c("ㄹ"), c("ㅎ"), Key(KeyKind.VOWEL_PAD, "모음")),
        listOf(Key(KeyKind.SHIFT, "⇧"), c("ㅋ"), c("ㅌ"), c("ㅊ"), c("ㅍ"), t("?")),
        bottomRow("123"),
    )

    fun english(): List<List<Key>> = listOf(
        chars("qwertyuiop"),
        listOf(Key(KeyKind.SPACER, "", weight = 0.5f)) + chars("asdfghjkl") +
            Key(KeyKind.SPACER, "", weight = 0.5f),
        listOf(Key(KeyKind.SHIFT, "⇧", weight = 1.5f)) + chars("zxcvbnm") +
            Key(KeyKind.BACKSPACE, "⌫", weight = 1.5f),
        bottomRow("123"),
    )

    fun symbols(): List<List<Key>> = listOf(
        chars("1234567890"),
        chars("@#\$%&*-+()"),
        chars("!\"':;/?~") + Key(KeyKind.BACKSPACE, "⌫", weight = 2f),
        listOf(
            Key(KeyKind.SYMBOLS, "가A", weight = 1.5f),
            t("^^", 1.2f),
            t(","),
            Key(KeyKind.SPACE, "", " ", weight = 4f),
            t("."),
            Key(KeyKind.ENTER, "↵", weight = 1.5f),
        ),
    )
}
