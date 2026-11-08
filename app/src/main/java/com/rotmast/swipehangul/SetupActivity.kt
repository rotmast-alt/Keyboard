package com.rotmast.swipehangul

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import com.rotmast.swipehangul.ime.KeyboardPrefs
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** 키보드 활성화 안내와 테스트 입력창, 사용법을 보여주는 화면. */
class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "스와이프 한글 키보드"
            textSize = 26f
            gravity = Gravity.START
        })

        root.addView(text("1. 아래 버튼으로 설정을 열고 '스와이프 한글 키보드'를 켜세요."))
        root.addView(Button(this).apply {
            text = "키보드 설정 열기"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })

        root.addView(text("2. 입력 방법을 '스와이프 한글 키보드'로 바꾸세요."))
        root.addView(Button(this).apply {
            text = "입력 방법 선택"
            setOnClickListener {
                getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
        })

        root.addView(text("설정"))
        val prefs = getSharedPreferences(KeyboardPrefs.FILE, MODE_PRIVATE)
        root.addView(Switch(this).apply {
            text = "5줄 모드 (ㅋ ㅌ ㅊ ㅍ ㅎ 줄 추가)"
            textSize = 16f
            isChecked = prefs.getBoolean(KeyboardPrefs.FIVE_ROWS, false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(KeyboardPrefs.FIVE_ROWS, checked).apply()
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(text("3. 여기서 바로 써 보세요."))
        root.addView(EditText(this).apply {
            hint = "여기를 눌러 입력"
            minLines = 3
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(text(USAGE).apply { textSize = 15f })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun text(s: String) = TextView(this).apply {
        text = s
        textSize = 16f
        setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private companion object {
        val USAGE = """
            사용법

            (빈칸) 한/영  123   ㄱ   ㄴ   ㄷ   ⌫
            (빈칸) 기호   모음  ㄹ   ㅁ   ㅇ   반복
            (빈칸) ,      ?     ㅂ   ㅅ   ㅈ   .
            (빈칸) 설정   [    스페이스     ]  ↵

            • 자음 키를 밀면 그 방향의 모음이 붙습니다.
                ↖ㅔ   ↑ㅗ   ↗ㅐ
                ←ㅓ   ㄱ    →ㅏ
                ↙ㅡ   ↓ㅜ   ↘ㅣ
              예) ㄱ을 →로 밀면 '가', ↓로 밀면 '구'

            • 밀었다가 자음으로 되돌아오면 ㅑ ㅕ ㅛ ㅠ ㅒ ㅖ, ㅡ는 ㅢ
              예) ㄱ에서 →로 갔다가 돌아오면 '갸', ↙로 갔다가 돌아오면 '긔'

            • 첫 모음 자리에 갔다가 둘째 모음 자리로 옮기면 겹모음
              ㅗ자리→ㅏ자리 ㅘ   ㅗ→ㅐ ㅙ   ㅗ→ㅣ ㅚ
              ㅜ자리→ㅓ자리 ㅝ   ㅜ→ㅔ ㅞ   ㅜ→ㅣ ㅟ   ㅡ→ㅣ ㅢ

            • 자음을 탭하면 받침. 다른 자음을 이어 탭하면 겹받침(ㄳ, ㄺ …)

            • 자음을 두 칸 옆 자음 키까지 끌면 거센소리
              ㄱ→ㄷ = ㅋ   ㄷ→ㄱ = ㅌ   ㅂ→ㅈ = ㅍ   ㅈ→ㅂ = ㅊ   ㅇ→ㄹ = ㅎ
            • 두 칸 아래/위 자음 키까지 끌면 된소리
              ㄱ→ㅂ = ㄲ   ㄷ→ㅈ = ㄸ   ㅂ→ㄱ = ㅃ   ㅈ→ㄷ = ㅉ   ㅅ→ㄴ = ㅆ
            • 끈 뒤에 이어서 밀면 모음이 붙습니다. 예) ㄱ→ㄷ 끈 뒤 ↓ = 쿠
            • 자음을 잠깐 누르고 있다가 밀거나 떼도 거센소리 (ㅅ은 ㅆ)

            • 반복: 된소리로 바꾸기 (가→까, 각→갂, 잇→있).
              된소리가 없으면 글자 반복 (ㅋ→ㅋㅋ, 하→하하)
            • 모음 키를 밀면 모음만 입력 (ㅠㅠ, ㅡㅡ)
            • . 키를 위로 밀면 !
            • 스페이스를 좌우로 밀면 커서 이동, ⌫를 왼쪽으로 밀면 단어 지우기
            • 설정 키: 이 화면을 엽니다 (5줄 모드 켜기/끄기)
        """.trimIndent()
    }
}
