package com.rotmast.swipehangul.hangul

/**
 * 한글 자모 테이블과 조합 규칙.
 * 모든 자모는 호환용 자모(U+3131~U+3163)로 다룬다.
 */
object Jamo {
    private const val SYLLABLE_BASE = 0xAC00
    private const val JUNG_COUNT = 21
    private const val JONG_COUNT = 28

    /** 초성 19자 (유니코드 순서) */
    const val CHOSEONG = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

    /** 중성 21자 (유니코드 순서) */
    const val JUNGSEONG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"

    /** 종성 27자 (유니코드 순서, '없음'을 제외) */
    const val JONGSEONG = "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    private val TENSE = mapOf('ㄱ' to 'ㄲ', 'ㄷ' to 'ㄸ', 'ㅂ' to 'ㅃ', 'ㅅ' to 'ㅆ', 'ㅈ' to 'ㅉ')

    private val ASPIRATED = mapOf(
        'ㄱ' to 'ㅋ', 'ㄷ' to 'ㅌ', 'ㅂ' to 'ㅍ', 'ㅈ' to 'ㅊ', 'ㅇ' to 'ㅎ', 'ㅅ' to 'ㅆ',
    )

    private val IOTIZED = mapOf(
        'ㅏ' to 'ㅑ', 'ㅓ' to 'ㅕ', 'ㅗ' to 'ㅛ', 'ㅜ' to 'ㅠ', 'ㅐ' to 'ㅒ', 'ㅔ' to 'ㅖ',
    )

    private val JUNG_PAIRS = mapOf(
        "ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ',
        "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ',
        "ㅡㅣ" to 'ㅢ',
    )

    private val JONG_PAIRS = mapOf(
        "ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ',
        "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ', "ㄹㅂ" to 'ㄼ', "ㄹㅅ" to 'ㄽ',
        "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ',
    )

    private val JUNG_SPLIT = JUNG_PAIRS.entries.associate { (k, v) -> v to (k[0] to k[1]) }
    private val JONG_SPLIT = JONG_PAIRS.entries.associate { (k, v) -> v to (k[0] to k[1]) }

    fun isChoseong(c: Char) = CHOSEONG.indexOf(c) >= 0
    fun isJungseong(c: Char) = JUNGSEONG.indexOf(c) >= 0
    fun isJongseong(c: Char) = JONGSEONG.indexOf(c) >= 0

    /** ㄱ→ㄲ 처럼 된소리로 바꾼다. 없으면 null. */
    fun tense(c: Char): Char? = TENSE[c]

    /** 길게 누른 자음: ㄱ→ㅋ, ㄷ→ㅌ, ㅂ→ㅍ, ㅈ→ㅊ, ㅇ→ㅎ, ㅅ→ㅆ. 없으면 null. */
    fun aspirate(c: Char): Char? = ASPIRATED[c]

    /** ㅏ→ㅑ 처럼 이중모음(y계열)으로 바꾼다. 없으면 null. */
    fun iotize(v: Char): Char? = IOTIZED[v]

    fun combineJung(first: Char, second: Char): Char? = JUNG_PAIRS["$first$second"]
    fun combineJong(first: Char, second: Char): Char? = JONG_PAIRS["$first$second"]
    fun splitJung(v: Char): Pair<Char, Char>? = JUNG_SPLIT[v]
    fun splitJong(c: Char): Pair<Char, Char>? = JONG_SPLIT[c]

    /**
     * 초/중/종성을 화면에 표시할 문자열로 만든다.
     * 초성+중성이 있으면 완성형 음절, 아니면 낱자 그대로.
     */
    fun compose(cho: Char?, jung: Char?, jong: Char?): String {
        if (cho != null && jung != null) {
            val l = CHOSEONG.indexOf(cho)
            val v = JUNGSEONG.indexOf(jung)
            val t = if (jong == null) 0 else JONGSEONG.indexOf(jong) + 1
            require(l >= 0 && v >= 0 && t >= 0) { "invalid jamo: $cho $jung $jong" }
            return (SYLLABLE_BASE + (l * JUNG_COUNT + v) * JONG_COUNT + t).toChar().toString()
        }
        return buildString {
            cho?.let(::append)
            jung?.let(::append)
        }
    }
}
