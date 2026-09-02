package com.animewidget.maid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.roundToInt

data class EditorModule(
    val id: String,
    val label: String,
    var x: Int,
    var y: Int,
    var frameWidth: Int,
    var frameHeight: Int,
    var visualLeft: Int = 0,
    var visualTop: Int = 0,
    var visualWidth: Int = frameWidth,
    var visualHeight: Int = frameHeight,
    var scale: Int = 100,
    var visible: Boolean = true
)

class DraggableModuleOverlay(context: Context) : View(context) {

    private val designW = WidgetLayoutCodec.DESIGN_WIDTH
    private val designH = WidgetLayoutCodec.DESIGN_HEIGHT

    private var contentRect: ContentRect? = null
    private var modules: List<EditorModule> = emptyList()
    private var selected: String? = null
    private var scalableIds: Set<String> = emptySet()

    var onModuleSelected: ((EditorModule) -> Unit)? = null
    var onModuleMoved: ((EditorModule) -> Unit)? = null
    var onModuleScaled: ((EditorModule) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressMs = 350L
    private val handler = Handler(Looper.getMainLooper())

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var hit: EditorModule? = null
    private var dragging = false
    private var moved = false
    private var resizing = false
    private var resizeHandle: ResizeHandle? = null
    private var resizeAnchorX = 0f
    private var resizeAnchorY = 0f
    private var resizeStartX = 0
    private var resizeStartY = 0
    private var resizeStartScale = 100

    private enum class ResizeHandle {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 16f * resources.displayMetrics.density
    }
    private val hiddenStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 160, 160, 160)
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(8f * resources.displayMetrics.density, 6f * resources.displayMetrics.density), 0f)
    }
    private val hiddenTextPaint = Paint(textPaint).apply {
        color = Color.argb(220, 180, 180, 180)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 80, 80, 80)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val handleRadiusPx = 6f * resources.displayMetrics.density
    private val handleHitRadiusPx = 14f * resources.displayMetrics.density
    private val minimumVisibleDesignPx = 32f

    fun setContentRect(rect: ContentRect) {
        contentRect = rect
        invalidate()
    }

    fun setModules(items: List<EditorModule>) {
        modules = items
        if (selected != null && items.none { it.id == selected }) selected = null
        invalidate()
    }

    fun setScalableIds(ids: Set<String>) {
        scalableIds = ids.toSet()
        invalidate()
    }

    fun selectedId(): String? = selected

    fun select(id: String?) {
        selected = if (id != null && modules.any { it.id == id }) id else null
        invalidate()
    }

    private val longPressRunnable = Runnable {
        val target = hit ?: return@Runnable
        if (contentRect?.width ?: 0f <= 0f || contentRect?.height ?: 0f <= 0f) return@Runnable
        dragging = true
        moved = false
        lastX = downX
        lastY = downY
        select(target.id)
        onModuleSelected?.invoke(target)
    }

    private fun clearCallbacks() {
        handler.removeCallbacks(longPressRunnable)
        hit = null
        dragging = false
        moved = false
        resizing = false
        resizeHandle = null
    }

    private fun toDesign(px: Float, py: Float): Pair<Float, Float> {
        val r = contentRect ?: return 0f to 0f
        return (px - r.left) / r.width * designW to (py - r.top) / r.height * designH
    }

    private fun findHit(px: Float, py: Float): EditorModule? {
        val r = contentRect ?: return null
        if (r.width <= 0f || r.height <= 0f || modules.isEmpty()) return null
        val (dx, dy) = toDesign(px, py)
        modules.firstOrNull { it.id == selected }?.let { current ->
            if (current.visible && contains(current, dx, dy)) return current
        }
        for (i in modules.lastIndex downTo 0) {
            val m = modules[i]
            if (m.id != selected && m.visible && contains(m, dx, dy)) return m
        }
        return null
    }

    private fun scaleFactor(module: EditorModule): Float = module.scale.coerceIn(50, 150) / 100f

    private fun visualBounds(module: EditorModule): FloatArray {
        val scale = scaleFactor(module)
        val left = module.x + module.visualLeft * scale
        val top = module.y + module.visualTop * scale
        return floatArrayOf(left, top, left + module.visualWidth * scale, top + module.visualHeight * scale)
    }

    private fun contains(module: EditorModule, x: Float, y: Float): Boolean =
        visualBounds(module).let { bounds ->
            x >= bounds[0] && x <= bounds[2] && y >= bounds[1] && y <= bounds[3]
        }

    private fun modulePixelBounds(module: EditorModule, rect: ContentRect): FloatArray {
        val visual = visualBounds(module)
        val left = rect.left + visual[0] / designW * rect.width
        val top = rect.top + visual[1] / designH * rect.height
        val right = rect.left + visual[2] / designW * rect.width
        val bottom = rect.top + visual[3] / designH * rect.height
        return floatArrayOf(left, top, right, bottom)
    }

    private fun clampPartiallyVisible(module: EditorModule) {
        val scale = scaleFactor(module)
        val visibleX = minOf(minimumVisibleDesignPx, module.visualWidth * scale)
        val visibleY = minOf(minimumVisibleDesignPx, module.visualHeight * scale)
        val minX = visibleX - (module.visualLeft + module.visualWidth) * scale
        val maxX = designW - visibleX - module.visualLeft * scale
        val minY = visibleY - (module.visualTop + module.visualHeight) * scale
        val maxY = designH - visibleY - module.visualTop * scale
        module.x = module.x.coerceIn(minX.roundToInt(), maxX.roundToInt())
        module.y = module.y.coerceIn(minY.roundToInt(), maxY.roundToInt())
    }

    fun scaleSelectedKeepingVisualCenter(newScale: Int): EditorModule? {
        val module = modules.firstOrNull { it.id == selected } ?: return null
        if (module.id !in scalableIds) return null
        val old = visualBounds(module)
        val centerX = (old[0] + old[2]) / 2f
        val centerY = (old[1] + old[3]) / 2f
        module.scale = newScale.coerceIn(50, 150)
        val scale = scaleFactor(module)
        module.x = (centerX - (module.visualLeft + module.visualWidth / 2f) * scale).roundToInt()
        module.y = (centerY - (module.visualTop + module.visualHeight / 2f) * scale).roundToInt()
        clampPartiallyVisible(module)
        invalidate()
        return module
    }

    private fun findResizeHandle(px: Float, py: Float): Pair<EditorModule, ResizeHandle>? {
        val rect = contentRect ?: return null
        if (rect.width <= 0f || rect.height <= 0f) return null
        val module = modules.firstOrNull { it.id == selected } ?: return null
        if (module.id !in scalableIds) return null
        val bounds = modulePixelBounds(module, rect)
        val handles = arrayOf(
            ResizeHandle.TOP_LEFT to (bounds[0] to bounds[1]),
            ResizeHandle.TOP_RIGHT to (bounds[2] to bounds[1]),
            ResizeHandle.BOTTOM_LEFT to (bounds[0] to bounds[3]),
            ResizeHandle.BOTTOM_RIGHT to (bounds[2] to bounds[3])
        )
        val hitRadiusSquared = handleHitRadiusPx * handleHitRadiusPx
        return handles.map { (handle, point) ->
            val deltaX = px - point.first
            val deltaY = py - point.second
            Triple(handle, point, deltaX * deltaX + deltaY * deltaY)
        }.filter { it.third <= hitRadiusSquared }
            .minByOrNull { it.third }
            ?.let { module to it.first }
    }

    private fun beginResize(module: EditorModule, handle: ResizeHandle) {
        handler.removeCallbacks(longPressRunnable)
        hit = module
        dragging = false
        moved = false
        resizing = true
        resizeHandle = handle
        resizeStartX = module.x
        resizeStartY = module.y
        resizeStartScale = module.scale
        val bounds = visualBounds(module)
        resizeAnchorX = when (handle) {
            ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT -> bounds[2]
            ResizeHandle.TOP_RIGHT, ResizeHandle.BOTTOM_RIGHT -> bounds[0]
        }
        resizeAnchorY = when (handle) {
            ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT -> bounds[3]
            ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM_RIGHT -> bounds[1]
        }
        onModuleSelected?.invoke(module)
    }

    private fun resizeTo(px: Float, py: Float) {
        val module = hit ?: return
        val handle = resizeHandle ?: return
        val (designX, designY) = toDesign(px, py)
        val xDirection = when (handle) {
            ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT -> -1f
            ResizeHandle.TOP_RIGHT, ResizeHandle.BOTTOM_RIGHT -> 1f
        }
        val yDirection = when (handle) {
            ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT -> -1f
            ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM_RIGHT -> 1f
        }
        val baseWidth = module.visualWidth.toFloat().coerceAtLeast(1f)
        val baseHeight = module.visualHeight.toFloat().coerceAtLeast(1f)
        val projectedScale = ((designX - resizeAnchorX) * xDirection * baseWidth +
            (designY - resizeAnchorY) * yDirection * baseHeight) /
            (baseWidth * baseWidth + baseHeight * baseHeight)
        val newScale = (projectedScale * 100f).roundToInt().coerceIn(50, 150)
        val factor = newScale / 100f
        module.scale = newScale
        module.x = when (handle) {
            ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT ->
                (resizeAnchorX - (module.visualLeft + module.visualWidth) * factor).roundToInt()
            ResizeHandle.TOP_RIGHT, ResizeHandle.BOTTOM_RIGHT ->
                (resizeAnchorX - module.visualLeft * factor).roundToInt()
        }
        module.y = when (handle) {
            ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT ->
                (resizeAnchorY - (module.visualTop + module.visualHeight) * factor).roundToInt()
            ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM_RIGHT ->
                (resizeAnchorY - module.visualTop * factor).roundToInt()
        }
        clampPartiallyVisible(module)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                val resizeHit = findResizeHandle(downX, downY)
                if (resizeHit != null) {
                    beginResize(resizeHit.first, resizeHit.second)
                    return true
                }
                hit = findHit(downX, downY)
                dragging = false
                moved = false
                if (hit != null) handler.postDelayed(longPressRunnable, longPressMs)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (resizing) {
                    resizeTo(event.x, event.y)
                    return true
                }
                if (!dragging) {
                    if (Math.abs(event.x - downX) > touchSlop || Math.abs(event.y - downY) > touchSlop) {
                        handler.removeCallbacks(longPressRunnable)
                        hit = null
                    }
                    return true
                }
                val r = contentRect ?: return true
                if (r.width <= 0f || r.height <= 0f) return true
                val dxDesign = (event.x - lastX) / r.width * designW
                val dyDesign = (event.y - lastY) / r.height * designH
                lastX = event.x
                lastY = event.y
                val m = hit ?: return true
                m.x += dxDesign.roundToInt()
                m.y += dyDesign.roundToInt()
                clampPartiallyVisible(m)
                moved = true
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (resizing) {
                    val resized = hit
                    val didResize = resized != null && (
                        resized.scale != resizeStartScale ||
                            resized.x != resizeStartX || resized.y != resizeStartY
                        )
                    clearCallbacks()
                    if (didResize && resized != null) onModuleScaled?.invoke(resized)
                    return true
                }
                val wasDragging = dragging
                val didMove = moved
                val dragged = hit
                clearCallbacks()
                if (wasDragging && didMove && dragged != null) onModuleMoved?.invoke(dragged)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                clearCallbacks()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = contentRect ?: return
        if (r.width <= 0f || r.height <= 0f || modules.isEmpty()) return
        val m = modules.firstOrNull { it.id == selected } ?: return
        val bounds = modulePixelBounds(m, r)
        val left = bounds[0]
        val top = bounds[1]
        val right = bounds[2]
        val bottom = bounds[3]
        if (m.visible) {
            canvas.drawRect(left, top, right, bottom, strokePaint)
            canvas.drawText(m.label, (left + right) / 2f,
                top - textPaint.descent() + 4f * resources.displayMetrics.density, textPaint)
        } else {
            canvas.drawRect(left, top, right, bottom, hiddenStrokePaint)
            canvas.drawText(m.label, (left + right) / 2f,
                (top + bottom) / 2f - (hiddenTextPaint.ascent() + hiddenTextPaint.descent()) / 2f,
                hiddenTextPaint)
        }
        if (m.id in scalableIds) {
            drawHandle(canvas, left, top)
            drawHandle(canvas, right, top)
            drawHandle(canvas, left, bottom)
            drawHandle(canvas, right, bottom)
        }
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadiusPx, handlePaint)
        canvas.drawCircle(x, y, handleRadiusPx, handleStrokePaint)
    }
}
