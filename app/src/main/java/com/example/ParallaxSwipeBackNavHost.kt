package com.example

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * 屏幕物理圆角数据结构（从系统 WindowInsets.RoundedCorner 获取）
 */
data class SystemScreenCorners(
    val topLeft: Dp = 0.dp,
    val topRight: Dp = 0.dp,
    val bottomRight: Dp = 0.dp,
    val bottomLeft: Dp = 0.dp
) {
    val hasRadius: Boolean get() = topLeft > 0.dp || topRight > 0.dp || bottomRight > 0.dp || bottomLeft > 0.dp

    fun toShape(): RoundedCornerShape = RoundedCornerShape(
        topStart = topLeft,
        topEnd = topRight,
        bottomEnd = bottomRight,
        bottomStart = bottomLeft
    )
}

/**
 * 获取系统真实物理屏幕四角圆角半径 (Android 12 / API 31+)
 */
@Composable
fun rememberSystemScreenCorners(): SystemScreenCorners {
    val view = LocalView.current
    val density = LocalDensity.current
    var corners by remember { mutableStateOf(SystemScreenCorners()) }

    DisposableEffect(view, density) {
        fun updateCorners(insets: android.view.WindowInsets?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && insets != null) {
                val tl = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
                val tr = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0
                val br = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
                val bl = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
                with(density) {
                    val newCorners = SystemScreenCorners(
                        topLeft = tl.toDp(),
                        topRight = tr.toDp(),
                        bottomRight = br.toDp(),
                        bottomLeft = bl.toDp()
                    )
                    if (newCorners != corners) {
                        corners = newCorners
                    }
                }
            }
        }

        updateCorners(view.rootWindowInsets)

        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            updateCorners(view.rootWindowInsets)
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    return corners
}

/**
 * 路由枚举定义
 */
enum class AppScreen {
    MAIN,
    SETTINGS,
    ABOUT,
    PROJECT_INFO,
    OPEN_SOURCE_LICENSES
}

/**
 * 真正的双层视差平滑手势返回导航容器 (Parallax Interactive Swipe-Back Navigator)
 *
 * 核心动效特性：
 * 1. 【真实双层实时渲染】：当前页面与上一级页面在同一层级内同时渲染，手指滑动多少，当前页面平移多少，实时露出底层界面。
 * 2. 【底层页面视差联动 (纯平移无缩放)】：底层页面随着手指拖动从 -33% 宽度位置向右平滑微移至 0%，不产生放大变形。
 * 3. 【下一层渐变暗度】：下一层带有暗度遮罩，随着手指滑动幅度增大，暗度组件平滑递减至完全明亮。
 * 4. 【系统真实屏幕圆角】：第一层上下采用从系统 WindowInsets 获取的真实屏幕圆角弧度，严密贴合屏幕物理轮廓。
 * 5. 【无多余人工阴影】：边缘不加人造渐变阴影，纯粹依靠下层暗度与系统圆角构建真实光影层次。
 * 6. 【系统预测返回与全交互手势联动】：PredictiveBackHandler 与手势拖拽驱动同一套物理模型。
 */
@Composable
fun ParallaxSwipeBackNavHost(
    viewModel: MathViewModel,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 维护页面路由栈
    val backStack = remember { mutableStateListOf(AppScreen.MAIN) }
    val coroutineScope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // 获取系统真实屏幕圆角
    val systemScreenCorners = rememberSystemScreenCorners()

    // 正在进行的过渡状态
    var isTransitioning by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val screenWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)

        // 顶层页面的水平偏移量 (0f 代表静止完全覆盖，screenWidthPx 代表完全滑出)
        val topScreenOffsetX = remember { Animatable(0f) }
        var isDragging by remember { mutableStateOf(false) }
        var isPredictiveBackActive by remember { mutableStateOf(false) }

        // 返回操作（带丝滑视差动画）
        fun popBack() {
            if (backStack.size <= 1 || isTransitioning) return
            isTransitioning = true
            coroutineScope.launch {
                topScreenOffsetX.animateTo(
                    targetValue = screenWidthPx,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
                if (backStack.size > 1) {
                    backStack.removeAt(backStack.lastIndex)
                }
                topScreenOffsetX.snapTo(0f)
                isTransitioning = false
            }
        }

        // 前进操作（新页面从右滑入，底页向左微移）
        fun pushScreen(screen: AppScreen) {
            if (isTransitioning) return
            isTransitioning = true
            coroutineScope.launch {
                // 先将新页面加入栈顶，初始偏移在右侧屏幕外
                backStack.add(screen)
                topScreenOffsetX.snapTo(screenWidthPx)
                topScreenOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                )
                isTransitioning = false
            }
        }

        val canGoBack = backStack.size > 1

        // 1. 系统级 Android 14+ 预测式返回手势监听
        PredictiveBackHandler(enabled = canGoBack && !isTransitioning) { progressFlow ->
            isPredictiveBackActive = true
            try {
                progressFlow.collect { backEvent ->
                    val targetProgress = backEvent.progress
                    val targetOffset = targetProgress * screenWidthPx
                    topScreenOffsetX.snapTo(targetOffset)
                }
                // 手势完成
                topScreenOffsetX.animateTo(
                    targetValue = screenWidthPx,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                )
                if (backStack.size > 1) {
                    backStack.removeAt(backStack.lastIndex)
                }
                topScreenOffsetX.snapTo(0f)
            } catch (e: CancellationException) {
                // 手势取消：弹簧回弹复原
                coroutineScope.launch {
                    topScreenOffsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            } finally {
                isPredictiveBackActive = false
            }
        }

        // 2. 屏幕手势滑动返回（支持手指/鼠标水平拖拽）
        val draggableState = rememberDraggableState { delta ->
            if (canGoBack && !isTransitioning) {
                val effectiveDelta = if (isRtl) -delta else delta
                val newOffset = (topScreenOffsetX.value + effectiveDelta).coerceIn(0f, screenWidthPx)
                coroutineScope.launch {
                    topScreenOffsetX.snapTo(newOffset)
                }
            }
        }

        val dragModifier = if (canGoBack && !isTransitioning) {
            Modifier.draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    isDragging = true
                },
                onDragStopped = { velocity ->
                    isDragging = false
                    val effectiveVelocity = if (isRtl) -velocity else velocity
                    val currentProgress = topScreenOffsetX.value / screenWidthPx

                    // 释放判定：拖拽超过 25% 宽度或快速向右挥动 (>700px/s) 则完成返回，否则回弹
                    if (currentProgress > 0.25f || effectiveVelocity > 700f) {
                        coroutineScope.launch {
                            topScreenOffsetX.animateTo(
                                targetValue = screenWidthPx,
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                            )
                            if (backStack.size > 1) {
                                backStack.removeAt(backStack.lastIndex)
                            }
                            topScreenOffsetX.snapTo(0f)
                        }
                    } else {
                        coroutineScope.launch {
                            topScreenOffsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                }
            )
        } else {
            Modifier
        }

        // 当前滑动进度 (0.0f = 静止覆盖, 1.0f = 完全移出)
        val progress = (topScreenOffsetX.value / screenWidthPx).coerceIn(0f, 1f)

        // 视差参数计算：
        // 底层页面：纯平移（无缩放），从 -33% 屏幕宽度位置平滑微移到 0 位置
        val bottomParallaxTranslationX = if (isRtl) {
            screenWidthPx * 0.33f * (1f - progress)
        } else {
            -screenWidthPx * 0.33f * (1f - progress)
        }

        // 下一层加暗：随手指挪动幅度越来越大，暗度逐渐减小
        val bottomScrimAlpha = ((1f - progress) * 0.40f).coerceIn(0f, 1f)

        // 顶层页面：纯跟手位移
        val topTranslationX = if (isRtl) -topScreenOffsetX.value else topScreenOffsetX.value

        // 渲染页面栈
        val currentTopIndex = backStack.lastIndex
        val underlyingIndex = if (currentTopIndex > 0) currentTopIndex - 1 else null

        // 1. 底层页面渲染（纯平移 + 随拖动逐渐减小的暗度蒙版）
        if (underlyingIndex != null) {
            val bottomScreen = backStack[underlyingIndex]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .graphicsLayer {
                        translationX = bottomParallaxTranslationX
                        // 第二层不搞放大，保持 scale 为 1.0
                        scaleX = 1f
                        scaleY = 1f
                    }
            ) {
                RenderScreenContent(
                    screen = bottomScreen,
                    viewModel = viewModel,
                    onOpenHistory = onOpenHistory,
                    onNavigateToSettings = { pushScreen(AppScreen.SETTINGS) },
                    onNavigateToAbout = { pushScreen(AppScreen.ABOUT) },
                    onNavigateToProjectInfo = { pushScreen(AppScreen.PROJECT_INFO) },
                    onNavigateToOpenSourceLicenses = { pushScreen(AppScreen.OPEN_SOURCE_LICENSES) },
                    onBack = { popBack() }
                )

                // 下层加暗遮罩：随着手指挪动幅度增大，暗度逐渐减小
                if (bottomScrimAlpha > 0.001f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = bottomScrimAlpha))
                    )
                }
            }
        }

        // 2. 顶层页面渲染（随手势位移 + 系统屏幕圆角裁剪，无人工阴影）
        val topScreen = backStack[currentTopIndex]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .then(dragModifier)
                .graphicsLayer {
                    translationX = topTranslationX
                    if (canGoBack && systemScreenCorners.hasRadius) {
                        clip = true
                        shape = systemScreenCorners.toShape()
                    }
                }
                .background(MaterialTheme.colorScheme.surface)
        ) {
            RenderScreenContent(
                screen = topScreen,
                viewModel = viewModel,
                onOpenHistory = onOpenHistory,
                onNavigateToSettings = { pushScreen(AppScreen.SETTINGS) },
                onNavigateToAbout = { pushScreen(AppScreen.ABOUT) },
                onNavigateToProjectInfo = { pushScreen(AppScreen.PROJECT_INFO) },
                onNavigateToOpenSourceLicenses = { pushScreen(AppScreen.OPEN_SOURCE_LICENSES) },
                onBack = { popBack() }
            )
        }
    }
}

/**
 * 分发渲染各屏幕内容
 */
@Composable
private fun RenderScreenContent(
    screen: AppScreen,
    viewModel: MathViewModel,
    onOpenHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToProjectInfo: () -> Unit,
    onNavigateToOpenSourceLicenses: () -> Unit,
    onBack: () -> Unit
) {
    when (screen) {
        AppScreen.MAIN -> {
            MainScreen(
                viewModel = viewModel,
                onOpenHistory = onOpenHistory,
                onOpenSettings = onNavigateToSettings
            )
        }
        AppScreen.SETTINGS -> {
            SettingsScreen(
                viewModel = viewModel,
                onBack = onBack,
                onOpenAbout = onNavigateToAbout
            )
        }
        AppScreen.ABOUT -> {
            AboutScreen(
                onBack = onBack,
                onNavigateToProjectInfo = onNavigateToProjectInfo,
                onNavigateToOpenSourceLicenses = onNavigateToOpenSourceLicenses
            )
        }
        AppScreen.PROJECT_INFO -> {
            ProjectInfoScreen(
                onBack = onBack
            )
        }
        AppScreen.OPEN_SOURCE_LICENSES -> {
            OpenSourceLicensesScreen(
                onBack = onBack
            )
        }
    }
}
