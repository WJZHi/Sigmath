package com.example

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    viewModel: MathViewModel,
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val input = viewModel.input
    val solutionResult by viewModel.solutionResult.collectAsStateWithLifecycle()
    val hasResult = solutionResult != null
    val isPlotActive = viewModel.isPlotActive
    val plotExpression = viewModel.plotExpression

    var showSteps by rememberSaveable { mutableStateOf(true) }
    var isInputFocused by rememberSaveable { mutableStateOf(true) }

    var showPasteEditDialog by rememberSaveable { mutableStateOf(false) }
    var pendingPasteText by rememberSaveable { mutableStateOf("") }

    val handleSmartPaste: () -> Unit = {
        val clipboardText = ClipboardUtils.getClipboardText(context)
        if (clipboardText.isNullOrBlank()) {
            Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
        } else {
            val classification = SmartMathClipboard.classify(clipboardText)
            when (classification) {
                MathTextClassification.NON_MATH -> {
                    Toast.makeText(context, "剪贴板内容不是有效算式，无法粘贴", Toast.LENGTH_LONG).show()
                }
                MathTextClassification.PURE_MATH -> {
                    val sanitized = SmartMathClipboard.sanitize(clipboardText)
                    viewModel.insertTextAtCursor(sanitized)
                    Toast.makeText(context, "已直接粘贴算式", Toast.LENGTH_SHORT).show()
                }
                MathTextClassification.NOISY_OR_INVALID_MATH -> {
                    pendingPasteText = clipboardText
                    showPasteEditDialog = true
                }
            }
        }
    }

    val handleCopy: () -> Unit = {
        if (input.text.isNotEmpty()) {
            ClipboardUtils.copyToClipboard(context, input.text, "LaTeX")
            Toast.makeText(context, "已复制公式", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "输入框为空，无复制内容", Toast.LENGTH_SHORT).show()
        }
    }

    // 如果用户没有生成结果，则强制调用键盘，激活输入框
    LaunchedEffect(hasResult) {
        if (!hasResult) {
            isInputFocused = true
        }
    }

    val effectiveKeyboardVisible = if (!hasResult) true else isInputFocused

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Bento Style Top App Header
        HeaderSection(
            onOpenHistory = onOpenHistory,
            onOpenSettings = onOpenSettings
        )

        // Scrollable Workspace Layout containing our beautiful Bento Cards
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (hasResult) {
                        isInputFocused = false
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Active Equation Input (Bento SecondaryContainer)
            LivePreviewSection(
                input = input,
                isFocused = effectiveKeyboardVisible,
                hasResult = hasResult,
                onFocusChange = { focus ->
                    if (hasResult) {
                        isInputFocused = focus
                    } else {
                        isInputFocused = true
                    }
                },
                onClearInput = { viewModel.updateInput(TextFieldValue("")) },
                onValueChange = { viewModel.updateInput(it) },
                onCopyRequest = handleCopy,
                onPasteRequest = handleSmartPaste
            )

            // Bento Solved Content: Spans when calculations are completed successfully
            if (solutionResult != null) {
                ResultSection(
                    result = solutionResult!!,
                    showSteps = showSteps,
                    onToggleSteps = { showSteps = !showSteps }
                )
            }

            // Card 2: Interactive Function Plotter (Dynamic Cartesian Plot)
            AnimatedVisibility(
                visible = isPlotActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                PlotterSection(
                    expression = plotExpression
                )
            }
        }

        // Custom Equation Keyboard: Shown automatically when EQUATION INPUT card is focused, hidden on loss of focus
        AnimatedVisibility(
            visible = effectiveKeyboardVisible,
            enter = slideInVertically(initialOffsetY = { it }) + expandVertically() + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically() + fadeOut()
        ) {
            MathKeyboard(
                fieldValue = input,
                onValueChange = { viewModel.updateInput(it) },
                onSolve = { viewModel.solveCurrentInput() },
                canHideKeyboard = hasResult,
                onHideKeyboard = {
                    if (hasResult) {
                        isInputFocused = false
                    }
                },
                onPasteRequest = handleSmartPaste
            )
        }

        if (!effectiveKeyboardVisible) {
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }

    if (showPasteEditDialog) {
        PasteEditDialog(
            initialText = pendingPasteText,
            onDismiss = { showPasteEditDialog = false },
            onConfirmPaste = { editedText ->
                viewModel.insertTextAtCursor(editedText)
                showPasteEditDialog = false
                Toast.makeText(context, "已粘贴算式", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun HeaderSection(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top-left history button
            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "查看历史记录",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = "Sigmath",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun LivePreviewSection(
    input: TextFieldValue,
    isFocused: Boolean,
    hasResult: Boolean = true,
    onFocusChange: (Boolean) -> Unit,
    onClearInput: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit = {},
    onCopyRequest: () -> Unit = {},
    onPasteRequest: () -> Unit = {}
) {
    var showFloatingMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFocusChange(true) }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                    Text(
                        text = if (isFocused) "输入公式" else "公式",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                if (isFocused && hasResult) {
                    IconButton(
                        onClick = { onFocusChange(false) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "收起键盘",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Real-time LaTeX Equation Render Box (Borderless)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (showFloatingMenu) {
                    EquationFloatingActionToolbar(
                        canCopy = input.text.isNotEmpty(),
                        onCopy = {
                            showFloatingMenu = false
                            onCopyRequest()
                        },
                        onPaste = {
                            showFloatingMenu = false
                            onPasteRequest()
                        },
                        onDismissRequest = {
                            showFloatingMenu = false
                        }
                    )
                }

                if (input.text.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        onFocusChange(true)
                                    },
                                    onLongPress = {
                                        onFocusChange(true)
                                        showFloatingMenu = true
                                    }
                                )
                            }
                    ) {
                        if (isFocused) {
                            BlinkingCursor(fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isFocused) "输入算式或方程..." else "点击此处输入算式...",
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                            color = if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                } else {
                    MathRenderer(
                        latex = input.text,
                        fontSize = 26.sp,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        cursorPosition = input.selection.start,
                        isFocused = isFocused,
                        onCursorPositionChange = { newPos ->
                            onValueChange(input.copy(selection = TextRange(newPos)))
                        },
                        onLongPress = {
                            onFocusChange(true)
                            showFloatingMenu = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PlotterSection(
    expression: String,
    onClosePlot: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    )
                    Text(
                        text = "INTERACTIVE PLOT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                if (onClosePlot != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .clickable { onClosePlot() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Close plot",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                MathPlotter(latexExpression = expression)
            }

            Text(
                text = "💡 拖动可平移，双指可缩放。精确定位函数零点与交点。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ResultSection(
    result: MathSolver.SolutionResult,
    showSteps: Boolean,
    onToggleSteps: () -> Unit
) {
    val context = LocalContext.current

    val irrationalItems = remember(result.exactResultLaTeX, result.inputLaTeX) {
        MathSolver.extractIrrationalItems(result)
    }

    val precisions = remember(result.exactResultLaTeX) {
        mutableStateMapOf<String, Int>()
    }

    var isIrrationalCardExpanded by remember { mutableStateOf(false) }

    val dynamicDecimalResult = remember(result, precisions.toMap()) {
        MathSolver.computeDecimalWithPrecisions(result, irrationalItems, precisions)
    }

    val displayResultLaTeX = remember(result.exactResultLaTeX, dynamicDecimalResult, precisions.toMap()) {
        if (precisions.values.any { it > 0 }) {
            dynamicDecimalResult
        } else {
            result.exactResultLaTeX
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Bento Card 1: Large Solutions Result Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROOTS / SOLUTIONS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                            .clickable {
                                ClipboardUtils.showCopySelector(context, displayResultLaTeX, "Result LaTeX")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Result LaTeX",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    MixedMathText(
                        text = displayResultLaTeX,
                        fontSize = 28.sp,
                        textColor = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${result.steps.size} STEPS AVAILABLE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (result.type == "equation") "EQUATION" else "CALCULATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (precisions.values.any { it > 0 }) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "精度已调整",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (irrationalItems.isNotEmpty()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isIrrationalCardExpanded = !isIrrationalCardExpanded }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "取整精度",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "共包含 ${irrationalItems.size} 个无理数/根式项",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val activeCount = precisions.values.count { it > 0 }
                            if (activeCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "已调 $activeCount 项",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (isIrrationalCardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isIrrationalCardExpanded) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isIrrationalCardExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            irrationalItems.forEach { item ->
                                val currentLevel = precisions[item.symbol] ?: 0

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            MixedMathText(
                                                text = item.latexSymbol,
                                                fontSize = 18.sp,
                                                textColor = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "(${item.displayName})",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (currentLevel == 0) MaterialTheme.colorScheme.secondaryContainer
                                                    else MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = MathSolver.getPrecisionLabel(currentLevel),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (currentLevel == 0) MaterialTheme.colorScheme.onSecondaryContainer
                                                        else MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "当前带入数值:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = MathSolver.formatSubstitutedValueDisplay(item, currentLevel),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Slider(
                                        value = currentLevel.toFloat(),
                                        onValueChange = { precisions[item.symbol] = it.roundToInt() },
                                        valueRange = 0f..6f,
                                        steps = 5,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        listOf("不取整", "整数", "1位", "2位", "3位", "4位", "6位").forEachIndexed { idx, label ->
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = if (currentLevel == idx) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                fontWeight = if (currentLevel == idx) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bento Card 2: Output forms
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "OUTPUT FORMS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text(
                            text = "精确解形式",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        MixedMathText(
                            text = result.exactResultLaTeX,
                            fontSize = 16.sp,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.1f))
                    )

                    Column {
                        Text(
                            text = "小数近似值",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        MixedMathText(
                            text = dynamicDecimalResult,
                            fontSize = 16.sp,
                            textColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        // Geometric Interpretation Bento Card
        result.geometricInterpretation?.let { interpretation ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(50))
                        )
                        Text(
                            text = "GEOMETRIC INTERPRETATION (几何解析)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }

                    MixedMathText(
                        text = interpretation,
                        fontSize = 15.sp,
                        textColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // Bento Accordion: Step-by-Step interactive layout
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleSteps() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "详细解题步骤",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Icon(
                        imageVector = if (showSteps) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = showSteps,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        result.steps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    MixedMathText(
                                        text = step,
                                        fontSize = 16.sp,
                                        textColor = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            ClipboardUtils.showCopySelector(context, step, "Step LaTeX")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy step LaTeX",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
