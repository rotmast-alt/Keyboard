package com.rotmast.swipehangul.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
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
import kotlin.math.min

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
        /** 한글 쿼티(두벌식) 모음: 앞 글자 받침을 넘겨받는다 */
        fun onDubeolsikVowel(v: Char)
        fun onText(text: String)
        fun onBackspace()
        fun onDeleteWord()
        fun onRepeat()
        fun onSpace()
        fun onEnter()
        fun onCursorMove(delta: Int)
        fun onFlush()
        fun onOpenSettings()
        /** android.R.id.selectAll / copy / paste */
        fun onEditAction(id: Int)
    }

    var listener: Listener? = null

    private enum class ShiftState { OFF, ONCE, LOCKED }

    private val prefs = context.getSharedPreferences(KeyboardPrefs.FILE, Context.MODE_PRIVATE)

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
    private val keyGap = dp(3f)
    private val keyRadius = dp(8f)
    private val padTop = dp(4f)
    private var bottomInset = 0

    private val classifier = SwipeClassifier(dp(16f), dp(64f))

    // ---------- 색상: 삼성 키보드풍 (밝은 회색 바탕 + 흰 키, 다크 모드 대응) ----------
    private val dark =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val colorBg = if (dark) 0xFF1C1C1E.toInt() else 0xFFEEEFF2.toInt()
    private val colorKey = if (dark) 0xFF3A3A3C.toInt() else 0xFFFFFFFF.toInt()
    private val colorKeyFunc = if (dark) 0xFF2C2C2E.toInt() else 0xFFDADCE1.toInt()
    private val colorKeyPressed = if (dark) 0xFF5A5A5E.toInt() else 0xFFC4C7CD.toInt()
    private val colorKeyActive = if (dark) 0xFF2B3E63.toInt() else 0xFFD3E3FF.toInt()
    private val colorShadow = if (dark) 0x00000000 else 0x26000000
    private val colorText = if (dark) 0xFFF2F2F2.toInt() else 0xFF1A1A1A.toInt()
    private val colorFunc = if (dark) 0xFFD0D0D0.toInt() else 0xFF3C3C3C.toInt()
    private val colorActiveText = if (dark) 0xFF8DB4FF.toInt() else 0xFF1B5FD6.toInt()
    private val colorDim = if (dark) 0xFF6E6E73.toInt() else 0xFFA0A3A8.toInt()
    private val colorHint = if (dark) 0xFF8E8E93.toInt() else 0xFF8A8D93.toInt()
    private val colorTrail = 0x663E91FF

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
        textSize = dp(12f)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (dark) 0xFF48484A.toInt() else 0xFFFFFFFF.toInt()
        if (!dark) setShadowLayer(dp(6f), 0f, dp(2f), 0x40000000)
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = if (dark) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt()
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
        if (newMode == Mode.HANGUL || newMode == Mode.ENGLISH || newMode == Mode.HANGUL_QWERTY) lastLetterMode = newMode
        mode = newMode
        symbolPage = 0
        shift = ShiftState.OFF
        vowelMode = false
        rebuild()
    }

    private fun rebuild() {
        fiveRows = prefs.getBoolean(PREF_FIVE_ROWS, false)
        rows = when (mode) {
            Mode.HANGUL -> Layouts.hangul(fiveRows)
            Mode.ENGLISH -> Layouts.english()
            Mode.HANGUL_QWERTY -> Layouts.hangulQwerty()
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
        // 스와이프 판정 거리는 키 크기(7칸 기준)에 비례
        val keyWidth = (width - side * 2) / 7f
        classifier.tapSlop = max(dp(14f), keyWidth * 0.25f)
        classifier.longDistance = max(dp(56f), keyWidth * 1.0f)
    }

    private fun keyAt(x: Float, y: Float): Key? {
        var best: Key? = null
        var bestDist = Float.MAX_VALUE
        for (row in rows) for (key in row) {
            if (key.kind == KeyKind.SPACER) {
                if (key.rect.contains(x, y)) return null // 빈칸을 누르면 무시
                continue
            }
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
        KeyKind.VOWEL_PAD -> vowelMode
        else -> false
    }

    private fun drawKey(canvas: Canvas, key: Key, pressed: Boolean) {
        val r = key.rect
        val active = isActive(key)
        val isLetter = key.kind == KeyKind.CONSONANT || key.kind == KeyKind.CHAR ||
            key.kind == KeyKind.SPACE || key.kind == KeyKind.NUM_PAD || key.kind == KeyKind.JAMO
        if (colorShadow != 0) {
            fillPaint.color = colorShadow
            tmpRect.set(r.left, r.top + dp(1f), r.right, r.bottom + dp(1f))
            canvas.drawRoundRect(tmpRect, keyRadius, keyRadius, fillPaint)
        }
        fillPaint.color = when {
            pressed -> colorKeyPressed
            active -> colorKeyActive
            isLetter -> colorKey
            else -> colorKeyFunc
        }
        canvas.drawRoundRect(r, keyRadius, keyRadius, fillPaint)
        iconPaint.color = colorFunc

        when (key.kind) {
            KeyKind.BACKSPACE -> drawBackspace(canvas, r)
            KeyKind.SPACE -> drawSpace(canvas, r)
            KeyKind.ENTER -> drawEnter(canvas, r)
            KeyKind.NUM_PAD -> drawNumPad(canvas, r)
            KeyKind.CURSOR_LEFT -> drawArrow(canvas, r, left = true)
            KeyKind.CURSOR_RIGHT -> drawArrow(canvas, r, left = false)
            KeyKind.CONSONANT, KeyKind.CHAR, KeyKind.JAMO -> {
                val label = displayLabel(key)
                labelPaint.typeface = Typeface.DEFAULT
                labelPaint.color = colorText
                labelPaint.textSize = when {
                    key.kind == KeyKind.CONSONANT -> dp(24f)
                    label.length > 1 -> dp(16f)
                    mode == Mode.ENGLISH || mode == Mode.HANGUL_QWERTY -> dp(21f)
                    else -> dp(22f)
                }
                drawCenteredText(canvas, label, r.centerX(), r.centerY(), labelPaint)
            }
            else -> {
                labelPaint.typeface = Typeface.DEFAULT
                labelPaint.color = if (active) colorActiveText else colorFunc
                labelPaint.textSize = if (mode == Mode.HANGUL) dp(16f) else dp(15f)
                val label = displayLabel(key)
                if (key.kind == KeyKind.PAGE) {
                    labelPaint.textSize = dp(14f)
                    val gap = labelPaint.textSize * 0.6f
                    drawCenteredText(canvas, "넘기기", r.centerX(), r.centerY() - gap, labelPaint)
                    labelPaint.color = colorHint
                    drawCenteredText(canvas, label, r.centerX(), r.centerY() + gap, labelPaint)
                } else if (label.length >= 4 && ' ' !in label) {
                    // '전체선택', '붙여넣기'는 두 줄로
                    labelPaint.textSize = dp(14f)
                    val half = label.length / 2
                    val gap = labelPaint.textSize * 0.6f
                    drawCenteredText(canvas, label.substring(0, half), r.centerX(), r.centerY() - gap, labelPaint)
                    drawCenteredText(canvas, label.substring(half), r.centerX(), r.centerY() + gap, labelPaint)
                } else {
                    drawCenteredText(canvas, label, r.centerX(), r.centerY(), labelPaint)
                }
            }
        }
        if (key.flicks.isNotEmpty() && key.kind != KeyKind.NUM_PAD) drawFlickHints(canvas, key)
        // 오른쪽으로 밀 수 있는 키 표시: 잘라내기(→복사), 쿼티의 한/영(→스와이프 한글)
        if (key.kind == KeyKind.CUT || (key.kind == KeyKind.LANG && mode != Mode.HANGUL)) {
            drawCenteredText(canvas, "›", r.right - dp(7f), r.centerY(), hintPaint)
        }
    }

    /** 숫자 키: 작은 3×3 키패드 모양. 가운데 5가 탭, 나머지는 그 방향으로 밀기 */
    private fun drawNumPad(canvas: Canvas, r: RectF) {
        val stepX = min(r.width() * 0.3f, dp(15f))
        val stepY = min(r.height() * 0.27f, dp(14f))
        for (row in 0..2) for (col in 0..2) {
            val digit = (row * 3 + col + 1).toString()
            val center = digit == "5"
            labelPaint.typeface = if (center) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            labelPaint.color = if (center) colorText else colorHint
            labelPaint.textSize = if (center) dp(17f) else dp(12f)
            drawCenteredText(canvas, digit, r.centerX() + (col - 1) * stepX, r.centerY() + (row - 1) * stepY, labelPaint)
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        canvas.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2, paint)
    }

    private fun drawFlickHints(canvas: Canvas, key: Key) {
        val r = key.rect
        val halfW = r.width() / 2 - dp(9f)
        val halfH = r.height() / 2 - dp(9f)
        for ((d, text) in key.flicks) {
            val x = r.centerX() + sign(d.unitX) * halfW
            val y = r.centerY() + sign(d.unitY) * halfH
            drawCenteredText(canvas, flickLabel(text), x, y, hintPaint)
        }
    }

    /** 밀기 문자를 화면에 보일 글자로 (엔터는 ↵) */
    private fun flickLabel(text: String) = if (text == Layouts.ENTER_FLICK) "↵" else text

    private fun sign(v: Float) = when {
        v > 0.3f -> 1f
        v < -0.3f -> -1f
        else -> 0f
    }

    private fun displayLabel(key: Key): String = when (key.kind) {
        KeyKind.CONSONANT -> key.label
        KeyKind.CHAR -> if (mode == Mode.ENGLISH && shift != ShiftState.OFF) key.label.uppercase() else key.label
        KeyKind.TO_LETTERS -> if (lastLetterMode == Mode.ENGLISH) "ABC" else "한글"
        KeyKind.JAMO -> if (shift != ShiftState.OFF) (Layouts.QWERTY_SHIFT[key.text[0]]?.toString() ?: key.label) else key.label
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
        val w = min(dp(26f), r.width() * 0.6f)
        val h = dp(18f)
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
            val input = resolveConsonant(p)
            val v = input.vowel
            when {
                v == null -> if (input.changed) input.consonant.toString() else null
                vowelMode -> v.toString()
                else -> Jamo.compose(input.consonant, v, null)
            }
        }
        KeyKind.VOWEL_PAD -> (classifier.classify(p.path) as? Gesture.Swipe)?.let { SwipeVowels.resolve(it).toString() }
        KeyKind.SPACE -> if (p.cursorMode) "◀ ▶" else null
        KeyKind.BACKSPACE -> if (!p.repeating && p.moved && isLeftward(p)) "단어 지우기" else null
        KeyKind.CUT -> if (isRightward(p)) "복사" else null
        KeyKind.LANG -> if (mode != Mode.HANGUL && isRightward(p)) "스와이프 한글" else null
        KeyKind.NUM_PAD -> when {
            p.moved -> flickText(p)
            p.longPressed -> "숫자판"
            else -> null
        }
        else -> if (p.key.flicks.isNotEmpty() && p.moved) flickText(p)?.let(::flickLabel) else null
    }

    private fun flickText(p: Pointer): String? {
        val g = classifier.classify(p.path) as? Gesture.Swipe ?: return null
        if (p.key.kind == KeyKind.NUM_PAD && g.first == Direction.S && (g.long || g.second == Direction.N)) {
            return "0" // 키패드에서 0은 8 아래: ↓ 길게 밀기 또는 ↓ 갔다 돌아오기
        }
        return p.key.flicks[g.first]
    }

    private fun drawTrails(canvas: Canvas) {
        for (p in pointers.values) {
            if (!p.moved || p.path.size < 2) continue
            if (p.key.kind != KeyKind.CONSONANT && p.key.kind != KeyKind.VOWEL_PAD) continue
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
        // 손가락 접촉점은 보는 위치보다 아래로 잡히므로 키 판정은 살짝 위로 올려서 한다
        // ('이'를 치려다 바로 아래 ㅈ이 눌리던 오타)
        val key = keyAt(x, y - dp(TOUCH_OFFSET_DP)) ?: return
        val p = Pointer(key, x, y)
        pointers[id] = p
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        when (key.kind) {
            KeyKind.CONSONANT ->
                if (Jamo.aspirate(key.text[0]) != null) postDelayed(p.longPressAction, LONG_PRESS_MS)
            KeyKind.BACKSPACE, KeyKind.CURSOR_LEFT, KeyKind.CURSOR_RIGHT ->
                postDelayed(p.repeatAction, REPEAT_DELAY_MS)
            KeyKind.NUM_PAD -> postDelayed(p.longPressAction, NUM_PAD_LONG_PRESS_MS)
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

        // 밀기 문자가 있는 키(. 위로 밀면 !)
        if (key.flicks.isNotEmpty() && p.moved) {
            when (val text = flickText(p)) {
                null -> Unit
                Layouts.ENTER_FLICK -> l.onEnter() // . 아래로 밀기
                else -> l.onText(text)
            }
            return
        }

        when (key.kind) {
            KeyKind.CONSONANT -> {
                val input = resolveConsonant(p)
                val v = input.vowel
                when {
                    v == null -> l.onConsonant(input.consonant)
                    vowelMode -> l.onVowel(v)
                    else -> l.onSyllable(input.consonant, v)
                }
                vowelMode = false
            }
            KeyKind.CHAR -> {
                val text = if (mode == Mode.ENGLISH && shift != ShiftState.OFF) key.text.uppercase() else key.text
                l.onText(text)
                if (shift == ShiftState.ONCE) shift = ShiftState.OFF
            }
            KeyKind.VOWEL_PAD -> {
                // 모음 키를 밀면 그 모음만 입력(ㅠㅠ), 탭하면 다음 자음 스와이프를 모음만 입력하는 모드
                val g = classifier.classify(p.path)
                if (g is Gesture.Swipe) {
                    l.onVowel(SwipeVowels.resolve(g))
                    vowelMode = false
                } else {
                    vowelMode = !vowelMode
                }
            }
            KeyKind.REPEAT -> l.onRepeat()
            KeyKind.SHIFT -> toggleShift()
            KeyKind.BACKSPACE -> when {
                p.repeating -> Unit
                p.moved && isLeftward(p) -> l.onDeleteWord()
                else -> l.onBackspace()
            }
            KeyKind.SPACE -> if (!p.cursorMode) l.onSpace()
            KeyKind.ENTER -> l.onEnter()
            KeyKind.TO_NUMBERS -> switchTo(if (mode == Mode.NUMBERS) lastLetterMode else Mode.NUMBERS)
            KeyKind.TO_SYMBOLS -> switchTo(Mode.SYMBOLS)
            // 한/영: 스와이프 한글 → 영문 쿼티 → 한글 쿼티 → 영문 쿼티 …
            // 쿼티에서 한/영을 오른쪽으로 밀면 스와이프 한글
            KeyKind.LANG -> switchTo(
                when {
                    mode != Mode.HANGUL && isRightward(p) -> Mode.HANGUL
                    mode == Mode.ENGLISH -> Mode.HANGUL_QWERTY
                    else -> Mode.ENGLISH
                },
            )
            KeyKind.JAMO -> {
                val base = key.text[0]
                val ch = if (shift != ShiftState.OFF) Layouts.QWERTY_SHIFT[base] ?: base else base
                if (Jamo.isJungseong(ch)) l.onDubeolsikVowel(ch) else l.onConsonant(ch)
                if (shift == ShiftState.ONCE) shift = ShiftState.OFF
            }
            KeyKind.TO_LETTERS -> switchTo(lastLetterMode)
            KeyKind.TO_HANGUL -> switchTo(Mode.HANGUL)
            KeyKind.TO_ENGLISH -> switchTo(Mode.ENGLISH)
            KeyKind.SETTINGS -> {
                l.onFlush()
                l.onOpenSettings()
            }
            // 숫자 키 탭은 5, 길게 누르면 숫자판
            KeyKind.NUM_PAD -> if (p.longPressed) switchTo(Mode.NUMBERS) else l.onText("5")
            KeyKind.SELECT_ALL -> l.onEditAction(android.R.id.selectAll)
            // 잘라내기, 오른쪽으로 밀면 복사
            KeyKind.CUT -> l.onEditAction(if (isRightward(p)) android.R.id.copy else android.R.id.cut)
            KeyKind.PASTE -> l.onEditAction(android.R.id.paste)
            KeyKind.UNDO -> l.onEditAction(android.R.id.undo)
            KeyKind.CURSOR_LEFT, KeyKind.CURSOR_RIGHT -> if (!p.repeating) fireRepeatable(key)
            KeyKind.PAGE -> {
                symbolPage = (symbolPage + 1) % Layouts.SYMBOL_PAGES.size
                rebuild()
            }
            KeyKind.SPACER -> Unit
        }
        if (key.kind != KeyKind.VOWEL_PAD && key.kind != KeyKind.CONSONANT) vowelMode = false
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

    private fun isRightward(p: Pointer): Boolean {
        if (!p.moved) return false
        val g = classifier.classify(p.path)
        return g is Gesture.Swipe && g.first in setOf(Direction.E, Direction.NE, Direction.SE)
    }

    private fun isLeftward(p: Pointer): Boolean {
        val g = classifier.classify(p.path)
        return g is Gesture.Swipe && g.first in setOf(Direction.W, Direction.NW, Direction.SW)
    }

    /** 자음 키 제스처 해석 결과. [vowel]이 null이면 자음만 입력. */
    private class ConsonantInput(val consonant: Char, val vowel: Char?, val changed: Boolean)

    private fun resolveConsonant(p: Pointer): ConsonantInput {
        dragTarget(p)?.let { (c, index) ->
            // 도착한 키에서 이어서 밀면 그 방향의 모음 (ㄱ→ㄷ 후 → 밀기 = 카)
            val g = classifier.classify(p.path.subList(index, p.path.size))
            return ConsonantInput(c, (g as? Gesture.Swipe)?.let(SwipeVowels::resolve), changed = true)
        }
        val c = consonantFor(p)
        val g = classifier.classify(p.path)
        return ConsonantInput(c, (g as? Gesture.Swipe)?.let(SwipeVowels::resolve), changed = p.longPressed)
    }

    /**
     * 같은 줄/칸에서 두 칸 떨어진 자음 키까지 끌었는지 본다.
     *  - 가로로 두 칸: 거센소리 (ㄱ→ㄷ 위치 = ㅋ, ㅇ→ㄹ 위치 = ㅎ, ㅂ→ㅈ = ㅍ, ㄷ→ㄱ = ㅌ, ㅈ→ㅂ = ㅊ)
     *  - 세로로 두 칸: 된소리 (ㄱ→ㅂ 위치 = ㄲ, ㄷ→ㅈ = ㄸ, ㅂ→ㄱ = ㅃ, ㅅ→ㄴ = ㅆ, ㅈ→ㄷ = ㅉ)
     * @return (바뀐 자음, 도착 지점의 path index)
     */
    private fun dragTarget(p: Pointer): Pair<Char, Int>? {
        if (mode != Mode.HANGUL) return null
        val row = rows.indexOfFirst { p.key in it }
        if (row < 0) return null
        val col = rows[row].indexOf(p.key)
        val base = p.key.text[0]
        // 목표 키 가운데까지 다 가지 않아도 되도록 넉넉하게 ('한'이 '연'으로 나오던 오타)
        val radius = min(p.key.rect.width(), p.key.rect.height()) * 0.62f
        val candidates = listOf(
            Triple(0, 2, Jamo.aspirate(base)), Triple(0, -2, Jamo.aspirate(base)),
            Triple(2, 0, Jamo.tense(base)), Triple(-2, 0, Jamo.tense(base)),
        )
        for ((dr, dc, variant) in candidates) {
            if (variant == null) continue
            val target = rows.getOrNull(row + dr)?.getOrNull(col + dc) ?: continue
            if (target.kind != KeyKind.CONSONANT) continue
            val cx = target.rect.centerX()
            val cy = target.rect.centerY()
            val closest = closestIndex(p.path, cx, cy, radius) ?: continue
            // 목표 키를 살짝 지나쳤다가 방향을 틀었으면, 튼 지점부터 모음으로 읽는다
            // (지나친 뒤 되돌아오는 곡선이 모음 판정에 섞이던 해→홰, 하→해 오타)
            val turn = p.path.indices.maxBy { p.path[it].x * dc + p.path[it].y * dr }
            val turnPt = p.path[turn]
            val end = p.path.last()
            val turnedBack = turn >= closest && hypot(end.x - turnPt.x, end.y - turnPt.y) >= classifier.tapSlop * 0.6f
            val index = if (turnedBack && hypot(turnPt.x - cx, turnPt.y - cy) <= radius * 1.3f) turn else closest
            return variant to index
        }
        return null
    }

    /** 경로가 (cx, cy)의 반경 안을 지나갔으면 가장 가까운 점의 index, 아니면 null. */
    private fun closestIndex(path: List<Pt>, cx: Float, cy: Float, radius: Float): Int? {
        var bestIndex = -1
        var bestDist = Float.MAX_VALUE
        for (i in path.indices) {
            val d = segmentDistance(path[max(0, i - 1)], path[i], cx, cy)
            if (d < bestDist) {
                bestDist = d
                bestIndex = i
            }
        }
        if (bestIndex < 0 || bestDist > radius) return null
        // 선분의 두 끝점 중 중심에 가까운 쪽
        val prev = max(0, bestIndex - 1)
        return if (hypot(path[prev].x - cx, path[prev].y - cy) < hypot(path[bestIndex].x - cx, path[bestIndex].y - cy)) {
            prev
        } else {
            bestIndex
        }
    }

    private fun segmentDistance(a: Pt, b: Pt, cx: Float, cy: Float): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((cx - a.x) * dx + (cy - a.y) * dy) / len2).coerceIn(0f, 1f)
        return hypot(a.x + t * dx - cx, a.y + t * dy - cy)
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
        const val PREF_FIVE_ROWS = KeyboardPrefs.FIVE_ROWS
        const val LONG_PRESS_MS = 300L
        const val NUM_PAD_LONG_PRESS_MS = 450L
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 50L
        const val DOUBLE_TAP_MS = 300L
        const val CURSOR_STEP_DP = 12f
        const val TOUCH_OFFSET_DP = 5f
    }
}
