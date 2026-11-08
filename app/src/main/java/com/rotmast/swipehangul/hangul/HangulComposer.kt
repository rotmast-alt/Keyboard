package com.rotmast.swipehangul.hangul

/**
 * 입력창에 반영할 변경 내용.
 * [commit]을 먼저 확정하고, 그 뒤에 [composing]을 조합 중 텍스트로 표시한다.
 */
data class EditResult(val commit: String, val composing: String)

/**
 * 스와이프 키보드용 한글 오토마타.
 *
 * 일반 두벌식과 달리 "자음+모음" 음절이 스와이프 한 번으로 들어오기 때문에
 * 받침이 다음 음절로 넘어가는(각+ㅏ → 가가) 처리가 필요 없다.
 *  - 자음 탭: 앞 음절에 받침으로 붙을 수 있으면 받침, 아니면 새 낱자
 *  - 자음 스와이프: 항상 새 음절(초성+중성)
 *  - 모음 패드: 앞 모음과 합쳐질 수 있으면 합치고(ㅗ+ㅏ=ㅘ), 아니면 낱모음
 */
class HangulComposer {
    private var cho: Char? = null
    private var jung: Char? = null
    private var jong: Char? = null

    val isComposing: Boolean get() = cho != null || jung != null

    fun composingText(): String = Jamo.compose(cho, jung, jong)

    fun reset() {
        cho = null
        jung = null
        jong = null
    }

    /** 조합 중인 글자를 확정하고 그 문자열을 돌려준다. */
    fun flush(): String {
        val text = composingText()
        reset()
        return text
    }

    fun inputConsonant(c: Char): EditResult {
        require(Jamo.isChoseong(c)) { "not a consonant: $c" }
        if (cho != null && jung != null) {
            val currentJong = jong
            if (currentJong == null) {
                if (Jamo.isJongseong(c)) {
                    jong = c
                    return EditResult("", composingText())
                }
            } else {
                val combined = Jamo.combineJong(currentJong, c)
                if (combined != null) {
                    jong = combined
                    return EditResult("", composingText())
                }
            }
        }
        val commit = flush()
        cho = c
        return EditResult(commit, composingText())
    }

    fun inputSyllable(c: Char, v: Char): EditResult {
        require(Jamo.isChoseong(c)) { "not a consonant: $c" }
        require(Jamo.isJungseong(v)) { "not a vowel: $v" }
        val commit = flush()
        cho = c
        jung = v
        return EditResult(commit, composingText())
    }

    fun inputVowel(v: Char): EditResult {
        require(Jamo.isJungseong(v)) { "not a vowel: $v" }
        val currentJung = jung
        if (currentJung != null && jong == null) {
            val combined = Jamo.combineJung(currentJung, v)
            if (combined != null) {
                jung = combined
                return EditResult("", composingText())
            }
        }
        val commit = flush()
        jung = v
        return EditResult(commit, composingText())
    }

    /**
     * 마지막 자음을 된소리로 바꾼다. 받침이 있으면 받침(각→갂, 갓→갔), 없으면 초성(가→까, ㄱ→ㄲ).
     * 바꿀 수 없으면 null.
     */
    fun tenseLast(): EditResult? {
        val currentJong = jong
        if (currentJong != null) {
            val t = Jamo.tense(currentJong)?.takeIf { Jamo.isJongseong(it) } ?: return null
            jong = t
            return EditResult("", composingText())
        }
        val currentCho = cho ?: return null
        cho = Jamo.tense(currentCho) ?: return null
        return EditResult("", composingText())
    }

    /** 조합 중인 글자를 확정하고 같은 글자를 다시 조합 상태로 둔다 (하 → 하하). */
    fun repeat(): EditResult? {
        if (!isComposing) return null
        val text = composingText()
        return EditResult(text, text)
    }

    /**
     * 자모 단위로 지운다(값→갑→가→ㄱ, 과→고→ㄱ).
     * 조합 중인 글자가 없으면 null을 돌려주고, 호출한 쪽이 일반 삭제를 처리한다.
     */
    fun backspace(): EditResult? {
        if (!isComposing) return null
        val currentJong = jong
        val currentJung = jung
        when {
            currentJong != null -> jong = Jamo.splitJong(currentJong)?.first
            currentJung != null -> jung = Jamo.splitJung(currentJung)?.first
            else -> cho = null
        }
        return EditResult("", composingText())
    }
}
