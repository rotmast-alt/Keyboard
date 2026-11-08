package com.rotmast.swipehangul

import com.rotmast.swipehangul.hangul.TextEdit
import org.junit.Assert.assertEquals
import org.junit.Test

class TextEditTest {
    @Test fun deletesLastWordAndTrailingSpaces() {
        assertEquals(2, TextEdit.wordDeleteLength("안녕 세계"))
        assertEquals(3, TextEdit.wordDeleteLength("안녕 세계 "))
        assertEquals(1, TextEdit.wordDeleteLength("안녕!"))
        assertEquals(0, TextEdit.wordDeleteLength(""))
    }
}
