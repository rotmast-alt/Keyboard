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

            (빈칸) Eng   ㄱ   ㄴ   ㄷ   ⌫
            (빈칸) 숫자  ㄹ   ㅁ   ㅇ   반복
            (빈칸) 기호  ㅂ   ㅅ   ㅈ   모음
            (빈칸) 4/5줄 ⇦   ␣    ⇨   ↵
            (오른손으로 치기 편하게 왼쪽 한 칸을 비웠습니다)

            • 자음 키를 밀면 그 방향의 모음이 붙습니다.
                ↖ㅔ   ↑ㅗ   ↗ㅐ
                ←ㅓ   ㄱ    →ㅏ
                ↙ㅡ   ↓ㅜ   ↘ㅣ
              예) ㄱ을 →로 밀면 '가', ↓로 밀면 '구'

            • 밀었다가 자음으로 되돌아오면 ㅑ ㅕ ㅛ ㅠ ㅒ ㅖ
              예) ㄱ에서 →로 갔다가 ㄱ으로 돌아오면 '갸', ↑로 갔다 돌아오면 '교'
            • 밀다가 꺾으면 겹모음
              ↑→ ㅘ   ↑↗ ㅙ   ↑↘ ㅚ   ↓← ㅝ   ↓↖ ㅞ   ↓↘ ㅟ   ↙↘ ㅢ

            • 자음을 탭하면 받침. 다른 자음을 이어 탭하면 겹받침(ㄳ, ㄺ …)
              예) ㅇ→ㄹ로 끈 뒤 → 밀기(하), ㄴ 탭, ㄱ↙ 밀기, ㄹ 탭 = 한글

            • 자음을 두 칸 옆 자음 키까지 끌면 거센소리
              ㄱ→ㄷ = ㅋ   ㄷ→ㄱ = ㅌ   ㅂ→ㅈ = ㅍ   ㅈ→ㅂ = ㅊ   ㅇ→ㄹ = ㅎ
            • 두 칸 아래/위 자음 키까지 끌면 된소리
              ㄱ→ㅂ = ㄲ   ㄷ→ㅈ = ㄸ   ㅂ→ㄱ = ㅃ   ㅈ→ㄷ = ㅉ   ㅅ→ㄴ = ㅆ
            • 끈 뒤에 이어서 밀면 모음이 붙습니다. 예) ㄱ→ㄷ 끈 뒤 ↓ = 쿠
            • 자음을 잠깐 누르고 있다가 밀거나 떼도 거센소리 (ㅅ은 ㅆ)
            • 4줄/5줄 키: 5줄로 바꾸면 ㅋ ㅌ ㅊ ㅍ ㅎ 줄이 따로 생깁니다.

            • 반복: 된소리로 바꾸기 (가→까, 각→갂, 잇→있).
              된소리가 없으면 글자 반복 (ㅋ→ㅋㅋ, 하→하하)
            • 모음 키: 탭한 뒤 자음을 밀면 모음만 입력 (ㅠㅠ, ㅡㅡ)
              밀면 문장부호: ↖^  ↗?  ←,  →.  ↙!
            • 숫자 키를 밀면 1~5, 기호 키를 밀면 6~0 (↑ ↗ → ↘ ↓ 순서)
            • ↵ 키를 밀면 ↖'  ↗"  ←~
            • ⇦ ⇨ 커서 이동 (누르고 있으면 계속), 스페이스를 좌우로 밀어도 커서 이동
            • ⌫ 왼쪽으로 밀면 단어 지우기
        """.trimIndent()
    }
}
