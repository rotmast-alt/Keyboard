package com.rotmast.swipehangul.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.rotmast.swipehangul.SetupActivity
import com.rotmast.swipehangul.hangul.EditResult
import com.rotmast.swipehangul.hangul.HangulComposer
import com.rotmast.swipehangul.hangul.TextEdit

class SwipeKeyboardService : InputMethodService(), KeyboardView.Listener {

    private val composer = HangulComposer()
    private var keyboardView: KeyboardView? = null

    override fun onCreateInputView(): View =
        KeyboardView(this).also {
            it.listener = this
            keyboardView = it
        }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composer.reset()
        keyboardView?.setMode(initialModeFor(info))
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        flush()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // 사용자가 커서를 다른 곳으로 옮기면 조합을 끝낸다.
        if (composer.isComposing &&
            (candidatesStart == -1 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
        ) {
            composer.reset()
            currentInputConnection?.finishComposingText()
        }
    }

    private fun initialModeFor(info: EditorInfo): Mode {
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return when {
            cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
                cls == InputType.TYPE_CLASS_DATETIME -> Mode.NUMBERS
            cls == InputType.TYPE_CLASS_TEXT && variation in ENGLISH_VARIATIONS -> Mode.ENGLISH
            else -> Mode.HANGUL
        }
    }

    // ---------- 입력 반영 ----------

    private fun apply(result: EditResult) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (result.commit.isNotEmpty()) ic.commitText(result.commit, 1)
        if (result.composing.isNotEmpty()) {
            ic.setComposingText(result.composing, 1)
        } else if (result.commit.isEmpty()) {
            ic.commitText("", 1) // 조합 중이던 글자가 모두 지워짐
        }
        ic.endBatchEdit()
    }

    private fun flush() {
        if (!composer.isComposing) return
        val text = composer.flush()
        currentInputConnection?.commitText(text, 1)
    }

    // ---------- KeyboardView.Listener ----------

    override fun onConsonant(c: Char) = apply(composer.inputConsonant(c))

    override fun onSyllable(c: Char, v: Char) = apply(composer.inputSyllable(c, v))

    override fun onVowel(v: Char) = apply(composer.inputVowel(v))

    override fun onText(text: String) {
        flush()
        currentInputConnection?.commitText(text, 1)
    }

    override fun onBackspace() {
        val result = composer.backspace()
        if (result != null) apply(result) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    override fun onDeleteWord() {
        flush()
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(64, 0) ?: return
        val n = TextEdit.wordDeleteLength(before)
        if (n > 0) ic.deleteSurroundingText(n, 0)
    }

    /** 반복 키: 마지막 자음을 된소리로(가→까, 갓→갔), 안 되면 글자를 한 번 더(ㅋ→ㅋㅋ). */
    override fun onRepeat() {
        val result = composer.tenseLast() ?: composer.repeat()
        if (result != null) {
            apply(result)
            return
        }
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2, 0) ?: return
        if (before.isEmpty()) return
        val last = if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) {
            before.toString()
        } else {
            before.last().toString()
        }
        ic.commitText(last, 1)
    }

    override fun onSpace() = onText(" ")

    override fun onEnter() {
        flush()
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val multiLine = info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (!noEnterAction && !multiLine &&
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    override fun onCursorMove(delta: Int) {
        flush()
        val code = if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(delta)) { sendDownUpKeyEvents(code) }
    }

    override fun onFlush() = flush()

    override fun onOpenSettings() {
        requestHideSelf(0)
        startActivity(
            Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        val ENGLISH_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
    }
}
