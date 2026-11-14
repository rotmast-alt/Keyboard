package com.rotmast.swipehangul

import com.rotmast.swipehangul.hangul.HangulComposer
import com.rotmast.swipehangul.hangul.Jamo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HangulComposerTest {

    /** 입력 결과를 실제 입력창처럼 누적해 확인하는 도우미 */
    private class Editor {
        val composer = HangulComposer()
        val committed = StringBuilder()
        var composing = ""

        fun tap(c: Char) = apply { val r = composer.inputConsonant(c); committed.append(r.commit); composing = r.composing }
        fun swipe(c: Char, v: Char) = apply { val r = composer.inputSyllable(c, v); committed.append(r.commit); composing = r.composing }
        fun vowel(v: Char) = apply { val r = composer.inputVowel(v); committed.append(r.commit); composing = r.composing }
        fun rep() = apply {
            val r = composer.tenseLast() ?: composer.repeat()!!
            committed.append(r.commit); composing = r.composing
        }
        fun dv(v: Char) = apply { val r = composer.inputVowelDubeolsik(v); committed.append(r.commit); composing = r.composing }
        /** 두벌식 자판 그대로 치기 */
        fun qwerty(keys: String) = apply { for (k in keys) if (Jamo.isJungseong(k)) dv(k) else tap(k) }
        fun back() = apply {
            val r = composer.backspace()
            if (r == null) { if (committed.isNotEmpty()) committed.setLength(committed.length - 1) } else composing = r.composing
        }
        val text get() = committed.toString() + composing
    }

    @Test fun composeSyllables() {
        assertEquals("가", Jamo.compose('ㄱ', 'ㅏ', null))
        assertEquals("힣", Jamo.compose('ㅎ', 'ㅣ', 'ㅎ'))
        assertEquals("값", Jamo.compose('ㄱ', 'ㅏ', 'ㅄ'))
        assertEquals("ㄱ", Jamo.compose('ㄱ', null, null))
        assertEquals("ㅠ", Jamo.compose(null, 'ㅠ', null))
    }

    @Test fun typeHangul() {
        val e = Editor().swipe('ㅎ', 'ㅏ').tap('ㄴ').swipe('ㄱ', 'ㅡ').tap('ㄹ')
        assertEquals("한글", e.text)
        assertEquals("글", e.composing)
    }

    @Test fun noJongseongMigrationNeeded() {
        // 일반 두벌식에서는 '각' 뒤 모음이 받침을 가져가지만, 여기서는 스와이프가 새 음절을 만든다
        val e = Editor().swipe('ㄱ', 'ㅏ').tap('ㄱ').swipe('ㅇ', 'ㅏ')
        assertEquals("각아", e.text)
    }

    @Test fun doubleJongseong() {
        assertEquals("값", Editor().swipe('ㄱ', 'ㅏ').tap('ㅂ').tap('ㅅ').text)
        assertEquals("닭", Editor().swipe('ㄷ', 'ㅏ').tap('ㄹ').tap('ㄱ').text)
        // 겹받침이 안 되는 조합은 새 낱자
        assertEquals("각ㄴ", Editor().swipe('ㄱ', 'ㅏ').tap('ㄱ').tap('ㄴ').text)
    }

    @Test fun tenseConsonantCannotBeJongseongWhenInvalid() {
        assertEquals("가ㄸ", Editor().swipe('ㄱ', 'ㅏ').tap('ㄸ').text)
        assertEquals("밖", Editor().swipe('ㅂ', 'ㅏ').tap('ㄲ').text)
    }

    @Test fun bareConsonantsAndVowels() {
        assertEquals("ㅋㅋㅋ", Editor().tap('ㅋ').tap('ㅋ').tap('ㅋ').text)
        assertEquals("ㅠㅠ", Editor().vowel('ㅠ').vowel('ㅠ').text)
        assertEquals("ㅋㅋㅠㅠ", Editor().tap('ㅋ').tap('ㅋ').vowel('ㅠ').vowel('ㅠ').text)
    }

    @Test fun vowelPadCombinesWithPreviousVowel() {
        assertEquals("과", Editor().swipe('ㄱ', 'ㅗ').vowel('ㅏ').text)
        assertEquals("의", Editor().swipe('ㅇ', 'ㅡ').vowel('ㅣ').text)
        assertEquals("각ㅏ", Editor().swipe('ㄱ', 'ㅏ').tap('ㄱ').vowel('ㅏ').text)
    }

    @Test fun backspaceIsJamoWise() {
        val e = Editor().swipe('ㄱ', 'ㅘ').tap('ㄹ').tap('ㅁ')
        assertEquals("괆", e.text)
        assertEquals("괄", e.back().text)
        assertEquals("과", e.back().text)
        assertEquals("고", e.back().text)
        assertEquals("ㄱ", e.back().text)
        assertEquals("", e.back().text)
        assertNull(e.composer.backspace())
    }

    @Test fun backspaceAfterCommitDeletesCommittedText() {
        val e = Editor().swipe('ㄴ', 'ㅏ').swipe('ㄹ', 'ㅏ')
        assertEquals("나라", e.text)
        e.back().back()
        assertEquals("나", e.text)
        e.back()
        assertEquals("", e.text)
    }

    @Test fun repeatKeyMakesTenseThenRepeats() {
        assertEquals("까", Editor().swipe('ㄱ', 'ㅏ').rep().text)
        assertEquals("갂", Editor().swipe('ㄱ', 'ㅏ').tap('ㄱ').rep().text)
        assertEquals("있", Editor().swipe('ㅇ', 'ㅣ').tap('ㅅ').rep().text)
        assertEquals("ㄲ", Editor().tap('ㄱ').rep().text)
        assertEquals("ㅋㅋㅋ", Editor().tap('ㅋ').rep().rep().text)
        assertEquals("하하", Editor().swipe('ㅎ', 'ㅏ').rep().text)
        // 반복 뒤에도 받침을 붙일 수 있다
        assertEquals("하한", Editor().swipe('ㅎ', 'ㅏ').rep().tap('ㄴ').text)
        // ㄷ 받침은 된소리 받침이 없으므로 반복
        assertEquals("갇갇", Editor().swipe('ㄱ', 'ㅏ').tap('ㄷ').rep().text)
    }

    @Test fun aspirate() {
        assertEquals('ㅋ', Jamo.aspirate('ㄱ'))
        assertEquals('ㅎ', Jamo.aspirate('ㅇ'))
        assertEquals('ㅆ', Jamo.aspirate('ㅅ'))
        assertNull(Jamo.aspirate('ㄴ'))
    }

    @Test fun dubeolsikQwerty() {
        assertEquals("한글", Editor().qwerty("ㅎㅏㄴㄱㅡㄹ").text)
        assertEquals("가가", Editor().qwerty("ㄱㅏㄱㅏ").text)
        assertEquals("각가", Editor().qwerty("ㄱㅏㄱㄱㅏ").text)
        assertEquals("달가", Editor().qwerty("ㄷㅏㄹㄱㅏ").text)
        assertEquals("닭", Editor().qwerty("ㄷㅏㄹㄱ").text)
        assertEquals("과자", Editor().qwerty("ㄱㅗㅏㅈㅏ").text)
        assertEquals("의사", Editor().qwerty("ㅇㅡㅣㅅㅏ").text)
        assertEquals("ㅋ큐ㅠ", Editor().qwerty("ㅋㅋㅠㅠ").text) // 표준 두벌식과 같은 동작
        assertEquals("있어", Editor().qwerty("ㅇㅣㅆㅇㅓ").text)
        assertEquals("앉아", Editor().qwerty("ㅇㅏㄴㅈㅇㅏ").text)
    }
}
