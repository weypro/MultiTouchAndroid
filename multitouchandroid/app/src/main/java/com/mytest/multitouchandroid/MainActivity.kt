package com.mytest.multitouchandroid

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.doOnNextLayout
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 应用主界面
            Scaffold(
                modifier = Modifier.fillMaxSize()
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color(0xFFF0F0F0))
                ) {
                    // 多点触控和FPS显示主组件
                    MultitouchFpsTracker()
                }
            }
        }
    }
}

// 用于FPS计算的类
class FpsCalculator {
    private val frameTimeHistory = ArrayDeque<Long>(120) // 保存最近120帧的时间戳
    private var lastFrameTimeNanos = 0L

    // 添加新的帧时间
    fun addFrame() {
        val currentTimeNanos = System.nanoTime()

        if (lastFrameTimeNanos > 0) {
            val frameDurationNanos = currentTimeNanos - lastFrameTimeNanos
            val frameTimeMillis = frameDurationNanos / 1_000_000 // 转换为毫秒

            // 保持队列在指定大小以内
            if (frameTimeHistory.size >= 120) {
                frameTimeHistory.removeFirst()
            }

            frameTimeHistory.addLast(frameTimeMillis)
        }

        lastFrameTimeNanos = currentTimeNanos
    }

    // 计算平均FPS
    fun calculateFps(): Int {
        if (frameTimeHistory.isEmpty()) return 0

        // 计算帧时间的平均值（毫秒）
        val averageFrameTimeMs = frameTimeHistory.average()

        // 将平均帧时间转换为FPS
        return if (averageFrameTimeMs > 0) (1000 / averageFrameTimeMs).toInt() else 0
    }

    // 重置计算器
    fun reset() {
        frameTimeHistory.clear()
        lastFrameTimeNanos = 0L
    }
}

// 触摸点数据类
data class TouchPoint(
    var position: Offset,
    val id: Int,
    val color: Color,
    val startTime: Long = System.currentTimeMillis()
) {
    val ripples = ConcurrentHashMap<Long, RippleCircle>()

    init {
        addRipple()
    }

    fun addRipple() {
        val now = System.currentTimeMillis()
        ripples[now] = RippleCircle(now)
    }

    class RippleCircle(val startTime: Long) {
        fun getRadius(): Float {
            val age = (System.currentTimeMillis() - startTime) / 1000f
            return min(300f, 100f * age) // 最大半径300像素
        }

        fun getAlpha(): Float {
            val age = (System.currentTimeMillis() - startTime) / 1000f
            return max(0f, 1f - age)
        }

        fun isExpired(): Boolean {
            return System.currentTimeMillis() - startTime > 1000 // 1秒后过期
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MultitouchFpsTracker() {
    // 状态管理
    val touchPoints = remember { mutableStateListOf<TouchPoint>() }
    var fps by remember { mutableStateOf(0) }
    var avgFps by remember { mutableStateOf(0) }
    var minFps by remember { mutableStateOf(Int.MAX_VALUE) }
    var maxFps by remember { mutableStateOf(0) }
    val fpsCalculator = remember { FpsCalculator() }

    val colors = remember {
        listOf(
            Color(0xFFFF5733), // 红橙色
            Color(0xFF33FF57), // 绿色
            Color(0xFF3357FF), // 蓝色
            Color(0xFFFF33F5), // 粉色
            Color(0xFFF5FF33), // 黄色
            Color(0xFF33FFF5)  // 青色
        )
    }

    val localView = LocalView.current

    // 设置FPS计算
    DisposableEffect(localView) {
        val vsyncFrameCallback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                // 添加新的帧
                fpsCalculator.addFrame()

                // 更新FPS值
                val currentFps = fpsCalculator.calculateFps()
                fps = currentFps

                // 更新最大/最小FPS
                if (currentFps > 0) { // 忽略0值（初始状态）
                    minFps = min(minFps, currentFps)
                    maxFps = max(maxFps, currentFps)
                }

                // 继续监听下一帧
                android.view.Choreographer.getInstance().postFrameCallback(this)
            }
        }

        // 确保视图添加到窗口后再开始监听帧回调
        localView.doOnNextLayout {
            android.view.Choreographer.getInstance().postFrameCallback(vsyncFrameCallback)
        }

        onDispose {
            // 清理资源
            android.view.Choreographer.getInstance().removeFrameCallback(vsyncFrameCallback)
        }
    }

    // 绘制主界面
    Box(modifier = Modifier.fillMaxSize()) {
        // 绘制画布
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                            val pointerIndex = event.actionIndex
                            val pointerId = event.getPointerId(pointerIndex)
                            val x = event.getX(pointerIndex)
                            val y = event.getY(pointerIndex)

                            val colorIndex = pointerId % colors.size
                            val newPoint = TouchPoint(
                                position = Offset(x, y),
                                id = pointerId,
                                color = colors[colorIndex]
                            )
                            touchPoints.add(newPoint)

                            // 清理已过期的触摸点
                            for (point in touchPoints) {
                                val iterator = point.ripples.entries.iterator()
                                while (iterator.hasNext()) {
                                    val (_, ripple) = iterator.next()
                                    if (ripple.isExpired()) {
                                        iterator.remove()
                                    }
                                }
                            }
                        }

                        MotionEvent.ACTION_MOVE -> {
                            for (i in 0 until event.pointerCount) {
                                val id = event.getPointerId(i)
                                val x = event.getX(i)
                                val y = event.getY(i)

                                touchPoints.find { it.id == id }?.let { point ->
                                    point.position = Offset(x, y)
                                }
                            }
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                            val pointerIndex = event.actionIndex
                            val pointerId = event.getPointerId(pointerIndex)
                            touchPoints.removeAll { it.id == pointerId }
                        }
                    }
                    true
                }
        ) {
            // 绘制所有触摸点和波纹
            for (point in touchPoints) {
                drawTouchPoint(point)
            }
        }

        // FPS信息卡片
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xB0000000)
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "当前FPS: $fps",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "最小FPS: ${if (minFps == Int.MAX_VALUE) 0 else minFps}",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "最大FPS: $maxFps",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        // 触摸信息卡片
        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .fillMaxWidth(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xB0000000)
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (touchPoints.isEmpty()) {
                    Text(
                        text = "等待触摸...",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                } else {
                    Text(
                        text = "检测到 ${touchPoints.size} 个触摸点:",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    for (point in touchPoints) {
                        Text(
                            text = "ID: ${point.id}, X: ${point.position.x.toInt()}, Y: ${point.position.y.toInt()}",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// 扩展DrawScope用于绘制触摸点
fun DrawScope.drawTouchPoint(point: TouchPoint) {
    // 绘制波纹
    for ((_, ripple) in point.ripples) {
        drawCircle(
            color = point.color.copy(alpha = ripple.getAlpha()),
            radius = ripple.getRadius(),
            center = point.position,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )
    }

    // 绘制触摸点圆圈
    drawCircle(
        color = point.color,
        radius = 30f,
        center = point.position
    )

    // 绘制触摸点ID
    drawContext.canvas.nativeCanvas.drawText(
        point.id.toString(),
        point.position.x,
        point.position.y + 10,
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 25f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    )
}
