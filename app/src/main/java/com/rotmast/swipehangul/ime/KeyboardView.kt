package com.rotmast.swipehangul.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
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
        fun onRepeat()
        fun onSpace()
        fun onEnter()
        fun onCursorMove(delta: Int)
        fun onFlush()
    }

    var listener: Listener? = null

    private enum class ShiftState { OFF, ONCE, LOCKED }

    private val prefs = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)

    private var mode = Mode.HANGUL
    private var lastLetterMode = Mode.HANGUL
    private var symbolPage = 0
    private var fiveRows = prefs.getBoolean(PREF_FIVE_ROWS, false)
    private var rows: List<List<Key>> = Layouts.hangul(fiveRows)
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L
    /** '모음' 키를 탭하면 켜진다. 다음 자음 스와이프가 모음 낱자만 입력한다. */
    private var vowelMode = false

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val rowHeight = dp(58f)
    private val keyGap = dp(2.5f)
    private val keyRadius = dp(9f)
    private val padTop = dp(4f)
    private var bottomInset = 0

    private val classifier = SwipeClassifier(dp(16f), dp(64f))

    // ---------- 색상 (검정 바탕 + 테두리 키) ----------
    private val colorBg = 0xFF000000.toInt()
    private val colorKey = 0xFF060606.toInt()
    private val colorKeyPressed = 0xFF2C2C2E.toInt()
    private val colorKeyActive = 0xFF1C2B48.toInt()
    private val colorStroke = 0xFF6B6B6B.toInt()
    private val colorStrokeActive = 0xFF4F86F7.toInt()
    private val colorText = 0xFFEDEDED.toInt()
    private val colorFunc = 0xFFD5D5D5.toInt()
    private val colorDim = 0xFF6F6F6F.toInt()
    private val colorHint = 0xFFC9A1A1.toInt()
    private val colorTrail = 0x784F86F7

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = colorFunc
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = colorHint
        textSize = dp(15f)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF0303033.toInt() }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
        typeface = Typeface.DEFAULT_BOLD
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = colorTrail
    }
    private val path = Path()
    private val tmpRect = RectF()

    // ---------- 터치 상태 ----------
    private inner class Pointer(val key: Key, x: Float, y: Float) {
        val path = mutableListOf(Pt(x, y))
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
                fireRepeatable(key)
                postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
    }

    private val pointers = LinkedHashMap<Int, Pointer>()

    // ---------- 외부 API ----------

    fun setMode(newMode: Mode) {
        if (newMode == Mode.HANGUL || newMode == Mode.ENGLISH) lastLetterMode = newMode
        mode = newMode
        symbolPage = 0
        shift = ShiftState.OFF
        vowelMode = false
        rebuild()
    }

    private fun rebuild() {
        rows = when (mode) {
            Mode.HANGUL -> Layouts.hangul(fiveRows)
            Mode.ENGLISH -> Layouts.english()
            Mode.NUMBERS -> Layouts.numbers()
            Mode.SYMBOLS -> Layouts.symbols(symbolPage)
        }
        cancelAllPointers()
        requestLayout()
        layoutKeys()
        invalidate()
    }

    // ---------- 측정 / 배치 ----------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (padTop * 2 + rowHeight * rows.size).toInt() + bottomInset
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
        var top = padTop
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
        // 스와이프 판정 거리는 키 크기(5칸 기준)에 비례
        val keyWidth = (width - side * 2) / 5f
        classifier.tapSlop = max(dp(14f), keyWidth * 0.25f)
        classifier.longDistance = max(dp(56f), keyWidth * 1.0f)
    }

    private fun keyAt(x: Float, y: Float): Key? {
        var best: Key? = null
        var bestDist = Float.MAX_VALUE
        for (row in rows) for (key in row) {
            if (key.kind == KeyKind.SPACER) continue
            val r = key.rect
            val dx = max(0f, max(r.left - x, x - r.right))
            val dy = max(0f, max(r.top - y, y - r.bottom))
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                best = key
            }
        }
        return best
    }

    // ---------- 그리기 ----------

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(colorBg)
        val pressed = pointers.values.map { it.key }.toSet()
        for (row in rows) for (key in row) {
            if (key.kind == KeyKind.SPACER) continue
            drawKey(canvas, key, key in pressed)
        }
        drawTrails(canvas)
        pointers.values.lastOrNull()?.let { drawBubble(canvas, it) }
    }

    private fun isActive(key: Key) = when (key.kind) {
        KeyKind.SHIFT -> shift != ShiftState.OFF
        KeyKind.VOWEL_MODE -> vowelMode
        else -> false
    }

    private fun drawKey(canvas: Canvas, key: Key, pressed: Boolean) {
        val r = key.rect
        val active = isActive(key)
        fillPaint.color = when {
            pressed -> colorKeyPressed
            active -> colorKeyActive
            else -> colorKey
        }
        strokePaint.color = if (active) colorStrokeActive else colorStroke
        canvas.drawRoundRect(r, keyRadius, keyRadius, fillPaint)
        canvas.drawRoundRect(r, keyRadius, keyRadius, strokePaint)

        when (key.kind) {
            KeyKind.BACKSPACE -> drawBackspace(canvas, r)
            KeyKind.SPACE -> drawSpace(canvas, r)
            KeyKind.ENTER -> drawEnter(canvas, r)
            KeyKind.CURSOR_LEFT -> drawArrow(canvas, r, left = true)
            KeyKind.CURSOR_RIGHT -> drawArrow(canvas, r, left = false)
            KeyKind.ROWS -> drawRowsToggle(canvas, r)
            KeyKind.CONSONANT, KeyKind.CHAR -> {
                val label = displayLabel(key)
                labelPaint.typeface = Typeface.DEFAULT
                labelPaint.color = colorText
                labelPaint.textSize = when {
                    key.kind == KeyKind.CONSONANT -> dp(22f)
                    label.length > 1 -> dp(16f)
                    mode == Mode.HANGUL || mode == Mode.NUMBERS || mode == Mode.SYMBOLS -> dp(22f)
                    else -> dp(20f)
                }
                drawCenteredText(canvas, label, r.centerX(), r.centerY(), labelPaint)
            }
            else -> {
                labelPaint.typeface = Typeface.DEFAULT_BOLD
                labelPaint.color = if (active) 0xFFFFFFFF.toInt() else colorFunc
                labelPaint.textSize = if (mode == Mode.HANGUL) dp(19f) else dp(16f)
                val label = displayLabel(key)
                if (key.flicks.isEmpty()) {
                    drawCenteredText(canvas, label, r.centerX(), r.centerY(), labelPaint)
                } else {
                    // 라벨은 왼쪽 아래로 비켜 두고, 밀기 힌트를 둘레에 그린다
                    drawCenteredText(canvas, label, r.centerX() - r.width() * 0.1f, r.centerY() + r.height() * 0.04f, labelPaint)
                }
            }
        }
        if (key.flicks.isNotEmpty()) drawFlickHints(canvas, key)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        canvas.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2, paint)
    }

    private fun drawFlickHints(canvas: Canvas, key: Key) {
        val r = key.rect
        val halfW = r.width() / 2 - dp(10f)
        val halfH = r.height() / 2 - dp(10f)
        for ((d, text) in key.flicks) {
            val x = r.centerX() + sign(d.unitX) * halfW
            val y = r.centerY() + sign(d.unitY) * halfH
            drawCenteredText(canvas, text, x, y, hintPaint)
        }
    }

    private fun sign(v: Float) = when {
        v > 0.3f -> 1f
        v < -0.3f -> -1f
        else -> 0f
    }

    private fun displayLabel(key: Key): String = when (key.kind) {
        KeyKind.CONSONANT -> key.label
        KeyKind.CHAR -> if (mode == Mode.ENGLISH && shift != ShiftState.OFF) key.label.uppercase() else key.label
        KeyKind.TO_LETTERS -> if (mode == Mode.ENGLISH || lastLetterMode == Mode.HANGUL) "한글" else "ABC"
        else -> key.label
    }

    // --- 아이콘 (폰트에 없을 수 있는 기호는 직접 그린다) ---

    private fun drawBackspace(canvas: Canvas, r: RectF) {
        val w = dp(30f)
        val h = dp(20f)
        val cx = r.centerX()
        val cy = r.centerY()
        path.reset()
        path.moveTo(cx - w / 2, cy)
        path.lineTo(cx - w / 4, cy - h / 2)
        path.lineTo(cx + w / 2, cy - h / 2)
        path.lineTo(cx + w / 2, cy + h / 2)
        path.lineTo(cx - w / 4, cy + h / 2)
        path.close()
        val x = h * 0.22f
        val xc = cx + w * 0.1f
        path.moveTo(xc - x, cy - x)
        path.lineTo(xc + x, cy + x)
        path.moveTo(xc + x, cy - x)
        path.lineTo(xc - x, cy + x)
        canvas.drawPath(path, iconPaint)
    }

    private fun drawSpace(canvas: Canvas, r: RectF) {
        val w = dp(30f)
        val h = dp(10f)
        val cx = r.centerX()
        val cy = r.centerY() + dp(3f)
        iconPaint.strokeWidth = dp(4f)
        path.reset()
        path.moveTo(cx - w / 2, cy - h)
        path.lineTo(cx - w / 2, cy)
        path.lineTo(cx + w / 2, cy)
        path.lineTo(cx + w / 2, cy - h)
        canvas.drawPath(path, iconPaint)
        iconPaint.strokeWidth = dp(2.2f)
    }

    private fun drawArrow(canvas: Canvas, r: RectF, left: Boolean) {
        val w = dp(32f)
        val h = dp(20f)
        val s = if (left) 1f else -1f
        val cx = r.centerX()
        val cy = r.centerY()
        path.reset()
        path.moveTo(cx - s * w / 2, cy)
        path.lineTo(cx - s * w / 8, cy - h / 2)
        path.lineTo(cx - s * w / 8, cy - h / 4)
        path.lineTo(cx + s * w / 2, cy - h / 4)
        path.lineTo(cx + s * w / 2, cy + h / 4)
        path.lineTo(cx - s * w / 8, cy + h / 4)
        path.lineTo(cx - s * w / 8, cy + h / 2)
        path.close()
        canvas.drawPath(path, iconPaint)
    }

    private fun drawEnter(canvas: Canvas, r: RectF) {
        val w = dp(26f)
        val h = dp(20f)
        val cx = r.centerX() + dp(4f)
        val cy = r.centerY() + dp(4f)
        iconPaint.strokeWidth = dp(3f)
        path.reset()
        path.moveTo(cx + w / 2, cy - h / 2)
        path.lineTo(cx + w / 2, cy + h / 4)
        path.lineTo(cx - w / 2, cy + h / 4)
        path.moveTo(cx - w / 2 + h / 3, cy + h / 4 - h / 3)
        path.lineTo(cx - w / 2, cy + h / 4)
        path.lineTo(cx - w / 2 + h / 3, cy + h / 4 + h / 3)
        canvas.drawPath(path, iconPaint)
        iconPaint.strokeWidth = dp(2.2f)
    }

    private fun drawRowsToggle(canvas: Canvas, r: RectF) {
        labelPaint.typeface = Typeface.DEFAULT_BOLD
        labelPaint.textSize = dp(15f)
        val cx = r.centerX()
        val gap = r.height() * 0.22f
        labelPaint.color = if (!fiveRows) colorText else colorDim
        drawCenteredText(canvas, "4줄", cx, r.centerY() - gap, labelPaint)
        labelPaint.color = if (fiveRows) colorText else colorDim
        drawCenteredText(canvas, "5줄", cx, r.centerY() + gap, labelPaint)
        iconPaint.strokeWidth = dp(1.5f)
        canvas.drawLine(cx - r.width() * 0.3f, r.centerY(), cx + r.width() * 0.3f, r.centerY(), iconPaint)
        iconPaint.strokeWidth = dp(2.2f)
    }

    // --- 미리보기 말풍선 / 손가락 궤적 ---

    private fun drawBubble(canvas: Canvas, p: Pointer) {
        val text = previewFor(p) ?: return
        val r = p.key.rect
        bubbleTextPaint.textSize = if (text.length > 2) dp(16f) else dp(30f)
        val w = max(dp(60f), bubbleTextPaint.measureText(text) + dp(24f))
        val h = dp(60f)
        var left = r.centerX() - w / 2
        left = left.coerceIn(dp(2f), width - w - dp(2f))
        val top = max(dp(2f), r.top - h * 0.75f)
        tmpRect.set(left, top, left + w, top + h)
        canvas.drawRoundRect(tmpRect, dp(12f), dp(12f), bubblePaint)
        drawCenteredText(canvas, text, tmpRect.centerX(), tmpRect.centerY(), bubbleTextPaint)
    }

    /** 손가락을 떼면 입력될 내용. 보여줄 게 없으면 null. */
    private fun previewFor(p: Pointer): String? = when (p.key.kind) {
        KeyKind.CONSONANT -> {
            val c = consonantFor(p)
            when (val g = classifier.classify(p.path)) {
                Gesture.Tap -> if (p.longPressed) c.toString() else null
                is Gesture.Swipe -> {
                    val v = SwipeVowels.resolve(g)
                    if (vowelMode) v.toString() else Jamo.compose(c, v, null)
                }
            }
        }
        KeyKind.SPACE -> if (p.cursorMode) "◀ ▶" else null
        KeyKind.BACKSPACE -> if (!p.repeating && p.moved && isLeftward(p)) "단어 지우기" else null
        else -> if (p.key.flicks.isNotEmpty() && p.moved) flickText(p) else null
    }

    private fun flickText(p: Pointer): String? {
        val g = classifier.classify(p.path) as? Gesture.Swipe ?: return null
        return p.key.flicks[g.first]
    }

    private fun drawTrails(canvas: Canvas) {
        for (p in pointers.values) {
            if (!p.moved || p.path.size < 2 || p.key.kind != KeyKind.CONSONANT) continue
            path.reset()
            path.moveTo(p.path[0].x, p.path[0].y)
            for (i in 1 until p.path.size) path.lineTo(p.path[i].x, p.path[i].y)
            canvas.drawPath(path, trailPaint)
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
            KeyKind.CONSONANT ->
                if (Jamo.aspirate(key.text[0]) != null) postDelayed(p.longPressAction, LONG_PRESS_MS)
            KeyKind.BACKSPACE, KeyKind.CURSOR_LEFT, KeyKind.CURSOR_RIGHT ->
                postDelayed(p.repeatAction, REPEAT_DELAY_MS)
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
        val key = p.key

        // 숫자·기호·모음·엔터 키를 밀면 방향별 문자를 입력
        if (key.flicks.isNotEmpty() && p.moved) {
            flickText(p)?.let { l.onText(it) }
            return
        }

        when (key.kind) {
            KeyKind.CONSONANT -> {
                val c = consonantFor(p)
                when (val g = classifier.classify(p.path)) {
                    Gesture.Tap -> l.onConsonant(c)
                    is Gesture.Swipe -> {
                        val v = SwipeVowels.resolve(g)
                        if (vowelMode) l.onVowel(v) else l.onSyllable(c, v)
                    }
                }
                vowelMode = false
            }
            KeyKind.CHAR -> {
                val text = if (mode == Mode.ENGLISH && shift != ShiftState.OFF) key.text.uppercase() else key.text
                l.onText(text)
                if (shift == ShiftState.ONCE) shift = ShiftState.OFF
            }
            KeyKind.VOWEL_MODE -> vowelMode = !vowelMode
            KeyKind.REPEAT -> l.onRepeat()
            KeyKind.SHIFT -> toggleShift()
            KeyKind.BACKSPACE -> when {
                p.repeating -> Unit
                p.moved && isLeftward(p) -> l.onDeleteWord()
                else -> l.onBackspace()
            }
            KeyKind.CURSOR_LEFT, KeyKind.CURSOR_RIGHT -> if (!p.repeating) fireRepeatable(key)
            KeyKind.SPACE -> if (!p.cursorMode) l.onSpace()
            KeyKind.ENTER -> l.onEnter()
            KeyKind.TO_NUMBERS -> switchTo(if (mode == Mode.NUMBERS) lastLetterMode else Mode.NUMBERS)
            KeyKind.TO_SYMBOLS -> switchTo(Mode.SYMBOLS)
            KeyKind.TO_ENGLISH -> switchTo(Mode.ENGLISH)
            KeyKind.TO_LETTERS -> switchTo(if (mode == Mode.ENGLISH) Mode.HANGUL else lastLetterMode)
            KeyKind.PAGE -> {
                symbolPage = 1 - symbolPage
                rebuild()
            }
            KeyKind.ROWS -> {
                fiveRows = !fiveRows
                prefs.edit().putBoolean(PREF_FIVE_ROWS, fiveRows).apply()
                rebuild()
            }
            KeyKind.SPACER -> Unit
        }
        if (key.kind != KeyKind.VOWEL_MODE && key.kind != KeyKind.CONSONANT) vowelMode = false
    }

    private fun switchTo(newMode: Mode) {
        listener?.onFlush()
        setMode(newMode)
    }

    private fun fireRepeatable(key: Key) {
        val l = listener ?: return
        when (key.kind) {
            KeyKind.BACKSPACE -> l.onBackspace()
            KeyKind.CURSOR_LEFT -> l.onCursorMove(-1)
            KeyKind.CURSOR_RIGHT -> l.onCursorMove(1)
            else -> Unit
        }
    }

    private fun isLeftward(p: Pointer): Boolean {
        val g = classifier.classify(p.path)
        return g is Gesture.Swipe && g.first in setOf(Direction.W, Direction.NW, Direction.SW)
    }

    /** 길게 눌렀으면 격음(ㄱ→ㅋ, ㄷ→ㅌ, ㅂ→ㅍ, ㅈ→ㅊ, ㅇ→ㅎ, ㅅ→ㅆ) */
    private fun consonantFor(p: Pointer): Char {
        val base = p.key.text[0]
        return if (p.longPressed) Jamo.aspirate(base) ?: base else base
    }

    private fun toggleShift() {
        val now = android.os.SystemClock.uptimeMillis()
        shift = when (shift) {
            ShiftState.OFF -> ShiftState.ONCE
            ShiftState.ONCE -> if (now - lastShiftTap < DOUBLE_TAP_MS) ShiftState.LOCKED else ShiftState.OFF
            ShiftState.LOCKED -> ShiftState.OFF
        }
        lastShiftTap = now
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
        const val PREF_FIVE_ROWS = "five_rows"
        const val LONG_PRESS_MS = 300L
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 50L
        const val DOUBLE_TAP_MS = 300L
        const val CURSOR_STEP_DP = 12f
    }
}
