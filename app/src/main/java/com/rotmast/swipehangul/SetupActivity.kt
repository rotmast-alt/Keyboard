package com.rotmast.swipehangul

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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

            • 자음 키를 밀면 그 방향의 모음이 붙은 글자가 됩니다.
                ↖ㅔ   ↑ㅗ   ↗ㅐ
                ←ㅓ   ㄱ    →ㅏ
                ↙ㅡ   ↓ㅜ   ↘ㅣ
              예) ㄱ을 →로 밀면 '가', ↓로 밀면 '구'

            • 길게 밀거나, 밀었다가 되돌아오면 ㅑ ㅕ ㅛ ㅠ ㅒ ㅖ
              예) ㄱ을 →로 길게 밀면 '갸'

            • 밀다가 꺾으면 겹모음
              ↑→ ㅘ   ↑↗ ㅙ   ↑↘ ㅚ   ↓← ㅝ   ↓↖ ㅞ   ↓↘ ㅟ   ↙↘ ㅢ

            • 자음을 탭하면 받침이 됩니다. 탭을 두 번 하면 겹받침(ㄳ, ㄺ …)
              예) ㅎ→ 밀기, ㄴ 탭, ㄱ↙ 밀기, ㄹ 탭 = 한글

            • 된소리(ㄲ ㄸ ㅃ ㅆ ㅉ): 자음을 잠깐 누르고 있다가 밀거나 떼기, 또는 ⇧
            • 모음 키를 밀면 모음만 입력 (ㅠㅠ, ㅡㅡ)
            • 스페이스를 좌우로 밀면 커서 이동, ⌫를 왼쪽으로 밀면 단어 지우기
        """.trimIndent()
    }
}
