package com.rotmast.swipehangul.hangul

object TextEdit {
    /** 커서 앞 텍스트에서 "단어 지우기"로 지울 글자 수. (뒤쪽 공백 + 단어 하나) */
    fun wordDeleteLength(before: CharSequence): Int {
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        if (i > 0 && before[i - 1].isLetterOrDigit()) {
            while (i > 0 && before[i - 1].isLetterOrDigit()) i--
        } else if (i > 0) {
            i--
        }
        return before.length - i
    }
}
