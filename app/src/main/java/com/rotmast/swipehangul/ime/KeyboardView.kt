package com.rotmast.swipehangul.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import com.rotmast.swipehangul.gesture.Direction
import com.rotmast.swipehangul.gesture.Gesture
import com.rotmast.swipehangul.gesture.Pt
import com.rotmast.swipehangul.gesture.SwipeClassifier
import com.rotmast.swipehangul.gesture.SwipeVowels
import com.rotmast.swipehangul.hangul.Jamo
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * 키를 직접 그리고 터치(탭/스와이프)를 해석하는 키보드 뷰.
 * 여러 손가락을 동시에 추적하므로 두 엄지로 빠르게 쳐도 된다.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onConsonant(c: Char)
        fun onSyllable(c: Char, v: Char)
        fun onVowel(v: Char)
        fun onText(text: String)
        fun onBackspace()
        fun onDeleteWord()
        fun onSpace()
        fun onEnter()
        fun onCursorMove(delta: Int)
        fun onFlush()
    }

    var listener: Listener? = null

    private enum class ShiftState { OFF, ONCE, LOCKED }

    private var mode = Mode.HANGUL
    private var lastLetterMode = Mode.HANGUL
    private var rows: List<List<Key>> = Layouts.hangul()
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val stripHeight = dp(40f)
    private val rowHeight = dp(56f)
    private val keyGap = dp(3f)
    private val keyRadius = dp(8f)
    private var bottomInset = 0

    private val classifier = SwipeClassifier(dp(16f), dp(64f))

    // ---------- 색상 ----------
    private val dark =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val colorBg = if (dark) 0xFF1B1B1F.toInt() else 0xFFE4E6EB.toInt()
    private val colorKey = if (dark) 0xFF3A3A40.toInt() else 0xFFFFFFFF.toInt()
    private val colorSpecial = if (dark) 0xFF2A2A2F.toInt() else 0xFFC9CDD4.toInt()
    private val colorPressed = if (dark) 0xFF55555E.toInt() else 0xFFB4BAC4.toInt()
    private val colorAccent = 0xFF3D7BFD.toInt()
    private val colorText = if (dark) 0xFFF1F1F4.toInt() else 0xFF15161A.toInt()
    private val colorHint = if (dark) 0xFF8E8E98.toInt() else 0xFF9AA0AA.toInt()

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = colorText
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = colorHint
        textSize = dp(10f)
    }
    private val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = colorText
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = colorAccent
        alpha = 120
    }

    // ---------- 터치 상태 ----------
    private inner class Pointer(val key: Key, x: Float, y: Float) {
        val path = mutableListOf(Pt(x, y))
        val downTime = SystemClock.uptimeMillis()
        var moved = false
        var longPressed = false
        var repeating = false
        var cursorMode = false
        var cursorAnchorX = x
        val longPressAction = Runnable {
            if (!moved) {
                longPressed = true
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                invalidate()
            }
        }
        val repeatAction: Runnable = object : Runnable {
            override fun run() {
                if (moved && !repeating) return
                repeating = true
                listener?.onBackspace()
                postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
    }

    private val pointers = LinkedHashMap<Int, Pointer>()
    private val trail = Path()

    // ---------- 외부 API ----------

    fun setMode(newMode: Mode) {
        if (newMode != Mode.SYMBOLS) lastLetterMode = newMode
        mode = newMode
        rows = when (newMode) {
            Mode.HANGUL -> Layouts.hangul()
            Mode.ENGLISH -> Layouts.english()
            Mode.SYMBOLS -> Layouts.symbols()
        }
        shift = ShiftState.OFF
        cancelAllPointers()
        layoutKeys()
        invalidate()
    }

    // ---------- 측정 / 배치 ----------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (stripHeight + rowHeight * 4 + dp(4f)).toInt() + bottomInset
        setMeasuredDimension(width, height)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        if (bottom != bottomInset) {
            bottomInset = bottom
            requestLayout()
        }
        return insets
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys()
    }

    private fun layoutKeys() {
        if (width == 0) return
        val side = dp(3f)
        var top = stripHeight
        for (row in rows) {
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val unit = (width - side * 2) / total
            var left = side
            for (key in row) {
                val w = unit * key.weight
                key.rect.set(left + keyGap, top + keyGap, left + w - keyGap, top + rowHeight - keyGap)
                left += w
            }
            top += rowHeight
        }
        // 스와이프 판정 거리는 키 크기에 비례
        val keyWidth = (width - side * 2) / 6f
        classifier.tapSlop = max(dp(14f), keyWidth * 0.28f)
        classifier.longDistance = max(dp(56f), keyWidth * 1.1f)
    }

    private fun keyAt(x: Float, y: Float): Key? {
        var best: Key? = null
        var bestDist = Float.MAX_VALUE
        for (row in rows) for (key in row) {
            if (key.kind == KeyKind.SPACER) continue
            val r = key.rect
            // 키 사이 틈을 눌러도 가장 가까운 키로
            val dx = max(0f, max(r.left - x, x - r.right))
            val dy = max(0f, max(r.top - y, y - r.bottom))
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = key
            }
        }
        return if (y >= stripHeight - keyGap) best else null
    }

    // ---------- 그리기 ----------

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(colorBg)
        drawStrip(canvas)
        val pressed = pointers.values.map { it.key }.toSet()
        for (row in rows) for (key in row) {
            if (key.kind == KeyKind.SPACER) continue
            drawKey(canvas, key, key in pressed)
        }
        drawTrails(canvas)
    }

    private fun drawKey(canvas: Canvas, key: Key, pressed: Boolean) {
        val r = key.rect
        val special = key.kind !in setOf(KeyKind.CONSONANT, KeyKind.CHAR, KeyKind.SPACE)
        keyPaint.color = when {
            pressed -> colorPressed
            key.kind == KeyKind.SHIFT && shift != ShiftState.OFF -> colorAccent
            special -> colorSpecial
            else -> colorKey
        }
        canvas.drawRoundRect(r, keyRadius, keyRadius, keyPaint)

        val label = displayLabel(key)
        labelPaint.textSize = when {
            key.kind == KeyKind.CONSONANT -> dp(22f)
            label.length > 2 -> dp(14f)
            else -> dp(18f)
        }
        labelPaint.typeface = if (key.kind == KeyKind.CONSONANT) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        labelPaint.color = if (key.kind == KeyKind.SHIFT && shift != ShiftState.OFF) 0xFFFFFFFF.toInt() else colorText
        if (key.kind == KeyKind.SHIFT && shift == ShiftState.LOCKED) labelPaint.isUnderlineText = true
        canvas.drawText(label, r.centerX(), r.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2, labelPaint)
        labelPaint.isUnderlineText = false

        when (key.kind) {
            KeyKind.CONSONANT -> drawHints(canvas, key, CARDINAL)
            KeyKind.VOWEL_PAD -> drawHints(canvas, key, Direction.entries)
            else -> Unit
        }
    }

    private fun drawHints(canvas: Canvas, key: Key, dirs: List<Direction>) {
        val r = key.rect
        val padX = dp(7f)
        val padY = dp(5f)
        val halfW = r.width() / 2 - padX
        val halfH = r.height() / 2 - padY
        val textOffset = -(hintPaint.ascent() + hintPaint.descent()) / 2
        for (d in dirs) {
            val x = r.centerX() + sign(d.unitX) * halfW
            val y = r.centerY() + sign(d.unitY) * halfH + textOffset
            canvas.drawText(SwipeVowels.base(d).toString(), x, y, hintPaint)
        }
    }

    private fun sign(v: Float) = when {
        v > 0.3f -> 1f
        v < -0.3f -> -1f
        else -> 0f
    }

    private fun displayLabel(key: Key): String = when (key.kind) {
        KeyKind.CONSONANT ->
            if (shift != ShiftState.OFF) Jamo.tense(key.text[0])?.toString() ?: key.label else key.label
        KeyKind.CHAR ->
            if (mode == Mode.ENGLISH && shift != ShiftState.OFF) key.label.uppercase() else key.label
        KeyKind.LANG -> if (lastLetterMode == Mode.HANGUL) "한/영" else "영/한"
        KeyKind.SPACE -> when (mode) {
            Mode.HANGUL -> "스와이프 한글"
            Mode.ENGLISH -> "English"
            Mode.SYMBOLS -> ""
        }
        else -> key.label
    }

    private fun drawStrip(canvas: Canvas) {
        val cy = stripHeight / 2
        val active = pointers.values.lastOrNull()
        val preview = active?.let { previewFor(it) }
        if (preview != null) {
            stripPaint.textSize = dp(24f)
            stripPaint.color = colorText
            stripPaint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(preview, width / 2f, cy - (stripPaint.ascent() + stripPaint.descent()) / 2, stripPaint)
            return
        }
        if (mode != Mode.HANGUL) return
        stripPaint.textSize = dp(12f)
        stripPaint.color = colorHint
        stripPaint.typeface = Typeface.DEFAULT
        canvas.drawText(
            "→ㅏ ←ㅓ ↑ㅗ ↓ㅜ ↘ㅣ ↙ㅡ ↗ㅐ ↖ㅔ · 길게: ㅑㅕㅛㅠ",
            width / 2f,
            cy - (stripPaint.ascent() + stripPaint.descent()) / 2,
            stripPaint,
        )
    }

    /** 손가락을 떼면 입력될 내용을 미리 보여준다. */
    private fun previewFor(p: Pointer): String? = when (p.key.kind) {
        KeyKind.CONSONANT -> {
            val c = consonantFor(p)
            when (val g = classifier.classify(p.path)) {
                Gesture.Tap -> c.toString()
                is Gesture.Swipe -> Jamo.compose(c, SwipeVowels.resolve(g), null)
            }
        }
        KeyKind.VOWEL_PAD -> when (val g = classifier.classify(p.path)) {
            Gesture.Tap -> "밀어서 모음 입력"
            is Gesture.Swipe -> SwipeVowels.resolve(g).toString()
        }
        KeyKind.SPACE -> if (p.cursorMode) "◀ 커서 이동 ▶" else null
        KeyKind.BACKSPACE -> if (!p.repeating && p.moved && isLeftward(p)) "단어 지우기" else null
        else -> null
    }

    private fun drawTrails(canvas: Canvas) {
        for (p in pointers.values) {
            if (!p.moved || p.path.size < 2) continue
            if (p.key.kind != KeyKind.CONSONANT && p.key.kind != KeyKind.VOWEL_PAD) continue
            trail.reset()
            trail.moveTo(p.path[0].x, p.path[0].y)
            for (i in 1 until p.path.size) trail.lineTo(p.path[i].x, p.path[i].y)
            canvas.drawPath(trail, trailPaint)
        }
    }

    // ---------- 터치 ----------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                onDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) onMove(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                onUp(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> cancelAllPointers()
        }
        invalidate()
        return true
    }

    private fun onDown(id: Int, x: Float, y: Float) {
        val key = keyAt(x, y) ?: return
        val p = Pointer(key, x, y)
        pointers[id] = p
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        when (key.kind) {
            KeyKind.CONSONANT -> if (Jamo.tense(key.text[0]) != null) postDelayed(p.longPressAction, LONG_PRESS_MS)
            KeyKind.BACKSPACE -> postDelayed(p.repeatAction, REPEAT_DELAY_MS)
            else -> Unit
        }
    }

    private fun onMove(id: Int, x: Float, y: Float) {
        val p = pointers[id] ?: return
        val last = p.path.last()
        if (last.x == x && last.y == y) return
        p.path.add(Pt(x, y))
        val start = p.path[0]
        if (!p.moved && hypot(x - start.x, y - start.y) >= classifier.tapSlop) {
            p.moved = true
            removeCallbacks(p.longPressAction)
            if (p.key.kind == KeyKind.SPACE && abs(x - start.x) > abs(y - start.y)) {
                p.cursorMode = true
                listener?.onFlush()
            }
        }
        if (p.cursorMode) {
            val step = dp(CURSOR_STEP_DP)
            while (x - p.cursorAnchorX >= step) {
                listener?.onCursorMove(1)
                p.cursorAnchorX += step
            }
            while (p.cursorAnchorX - x >= step) {
                listener?.onCursorMove(-1)
                p.cursorAnchorX -= step
            }
        }
    }

    private fun onUp(id: Int, x: Float, y: Float) {
        val p = pointers.remove(id) ?: return
        if (p.path.last().let { it.x != x || it.y != y }) p.path.add(Pt(x, y))
        removeCallbacks(p.longPressAction)
        removeCallbacks(p.repeatAction)
        val l = listener ?: return

        when (p.key.kind) {
            KeyKind.CONSONANT -> {
                val c = consonantFor(p)
                when (val g = classifier.classify(p.path)) {
                    Gesture.Tap -> l.onConsonant(c)
                    is Gesture.Swipe -> l.onSyllable(c, SwipeVowels.resolve(g))
                }
                consumeShift()
            }
            KeyKind.VOWEL_PAD -> {
                val g = classifier.classify(p.path)
                if (g is Gesture.Swipe) l.onVowel(SwipeVowels.resolve(g))
            }
            KeyKind.CHAR -> {
                val text = if (mode == Mode.ENGLISH && shift != ShiftState.OFF) p.key.text.uppercase() else p.key.text
                l.onText(text)
                consumeShift()
            }
            KeyKind.SHIFT -> toggleShift()
            KeyKind.BACKSPACE -> when {
                p.repeating -> Unit
                p.moved && isLeftward(p) -> l.onDeleteWord()
                else -> l.onBackspace()
            }
            KeyKind.SPACE -> if (!p.cursorMode) l.onSpace()
            KeyKind.ENTER -> l.onEnter()
            KeyKind.SYMBOLS -> {
                l.onFlush()
                setMode(if (mode == Mode.SYMBOLS) lastLetterMode else Mode.SYMBOLS)
            }
            KeyKind.LANG -> {
                l.onFlush()
                setMode(if (lastLetterMode == Mode.HANGUL) Mode.ENGLISH else Mode.HANGUL)
            }
            KeyKind.SPACER -> Unit
        }
    }

    private fun isLeftward(p: Pointer): Boolean {
        val g = classifier.classify(p.path)
        return g is Gesture.Swipe && g.first in setOf(Direction.W, Direction.NW, Direction.SW)
    }

    private fun consonantFor(p: Pointer): Char {
        val base = p.key.text[0]
        val tense = shift != ShiftState.OFF || p.longPressed
        return if (tense) Jamo.tense(base) ?: base else base
    }

    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        shift = when (shift) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> if (now - lastShiftTap < DOUBLE_TAP_MS) ShiftState.LOCKED else ShiftState.OFF
            ShiftState.LOCKED -> ShiftState.OFF
        }
        lastShiftTap = now
    }

    private fun consumeShift() {
        if (shift == ShiftState.ONCE) shift = ShiftState.OFF
    }

    private fun cancelAllPointers() {
        for (p in pointers.values) {
            removeCallbacks(p.longPressAction)
            removeCallbacks(p.repeatAction)
        }
        pointers.clear()
    }

    override fun onDetachedFromWindow() {
        cancelAllPointers()
        super.onDetachedFromWindow()
    }

    private companion object {
        val CARDINAL = listOf(Direction.E, Direction.W, Direction.N, Direction.S)
        const val LONG_PRESS_MS = 350L
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 50L
        const val DOUBLE_TAP_MS = 300L
        const val CURSOR_STEP_DP = 12f
    }
}
