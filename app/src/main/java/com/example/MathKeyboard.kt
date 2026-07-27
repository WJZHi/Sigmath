package com.example

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.withTimeoutOrNull

enum class KeyboardPage {
    BASIC, ADVANCED, ALPHABET
}

@Composable
fun KeyboardTabChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MathKeyboard(
    fieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSolve: () -> Unit,
    onHideKeyboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableStateOf(KeyboardPage.BASIC) }
    var isAnyPopupActive by remember { mutableStateOf(false) }

    fun handleInsert(textToInsert: String, cursorShift: Int = 0) {
        val text = fieldValue.text
        val selection = fieldValue.selection
        val selStart = selection.start.coerceIn(0, text.length)
        val selEnd = selection.end.coerceIn(0, text.length)
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)

        val before = text.substring(0, start)
        val after = text.substring(end, text.length)
        val newText = before + textToInsert + after

        val newCursorPos = (start + textToInsert.length + cursorShift).coerceIn(0, newText.length)
        onValueChange(
            TextFieldValue(
                text = newText,
                selection = TextRange(newCursorPos, newCursorPos)
            )
        )
    }

    fun handleBackspace() {
        val text = fieldValue.text
        val selection = fieldValue.selection
        val selStart = selection.start.coerceIn(0, text.length)
        val selEnd = selection.end.coerceIn(0, text.length)
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)

        if (start != end) {
            // Delete selection
            val before = text.substring(0, start)
            val after = text.substring(end, text.length)
            onValueChange(
                TextFieldValue(
                    text = before + after,
                    selection = TextRange(start, start)
                )
            )
        } else if (start > 0) {
            // Check for LaTeX structures to delete as a whole if possible, or just standard backspace
            val before = text.substring(0, start - 1)
            val after = text.substring(start, text.length)
            onValueChange(
                TextFieldValue(
                    text = before + after,
                    selection = TextRange(start - 1, start - 1)
                )
            )
        }
    }

    fun handleMoveCursor(direction: Int) {
        val text = fieldValue.text
        val selection = fieldValue.selection
        val currentPos = selection.start.coerceIn(0, text.length)
        val newPos = (currentPos + direction).coerceIn(0, text.length)
        onValueChange(
            TextFieldValue(
                text = text,
                selection = TextRange(newPos, newPos)
            )
        )
    }

    fun handleAC() {
        onValueChange(TextFieldValue("", selection = TextRange(0, 0)))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 6.dp, horizontal = 6.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        val startX = down.position.x
                        var lastX = startX

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: break
                            lastX = change.position.x

                            if (!change.pressed) {
                                val deltaX = lastX - startX
                                val threshold = 50f // gesture swipe threshold (~20-25dp)

                                // 只有在包含浮动选单 (Popup) 激活时，滑动手势才失效
                                if (!isAnyPopupActive) {
                                    if (deltaX > threshold) {
                                        // 手指从左向右滑动 -> 切换到左侧模式 ([字母] <- [基础] <- [高级] <- [字母])
                                        currentPage = when (currentPage) {
                                            KeyboardPage.BASIC -> KeyboardPage.ALPHABET
                                            KeyboardPage.ADVANCED -> KeyboardPage.BASIC
                                            KeyboardPage.ALPHABET -> KeyboardPage.ADVANCED
                                        }
                                    } else if (deltaX < -threshold) {
                                        // 手指从右向左滑动 -> 切换到右侧模式 ([字母] -> [基础] -> [高级] -> [字母])
                                        currentPage = when (currentPage) {
                                            KeyboardPage.ALPHABET -> KeyboardPage.BASIC
                                            KeyboardPage.BASIC -> KeyboardPage.ADVANCED
                                            KeyboardPage.ADVANCED -> KeyboardPage.ALPHABET
                                        }
                                    }
                                }
                                break
                            }
                        }
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Keyboard Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KeyboardTabChip("字母", currentPage == KeyboardPage.ALPHABET) { currentPage = KeyboardPage.ALPHABET }
                KeyboardTabChip("基础", currentPage == KeyboardPage.BASIC) { currentPage = KeyboardPage.BASIC }
                KeyboardTabChip("高级", currentPage == KeyboardPage.ADVANCED) { currentPage = KeyboardPage.ADVANCED }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onHideKeyboard() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "收起",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Hide keyboard",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if ((targetState == KeyboardPage.ALPHABET && initialState == KeyboardPage.BASIC) ||
                    (targetState == KeyboardPage.BASIC && initialState == KeyboardPage.ADVANCED) ||
                    (targetState == KeyboardPage.ADVANCED && initialState == KeyboardPage.ALPHABET)
                ) {
                    // Swiping Left-to-Right (slide in from left)
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(
                        slideOutHorizontally { it } + fadeOut()
                    )
                } else {
                    // Swiping Right-to-Left (slide in from right)
                    (slideInHorizontally { it } + fadeIn()).togetherWith(
                        slideOutHorizontally { -it } + fadeOut()
                    )
                }
            },
            label = "keyboard_page_animated_content"
        ) { page ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (page) {
                    KeyboardPage.BASIC -> {
                        BasicKeyboardLayout(
                            onKeyInsert = { txt, shift -> handleInsert(txt, shift) },
                            onBackspace = { handleBackspace() },
                            onMoveCursor = { dir -> handleMoveCursor(dir) },
                            onAC = { handleAC() },
                            onSolve = onSolve,
                            onSwitchPage = { currentPage = KeyboardPage.ADVANCED },
                            onPopupStateChanged = { active -> isAnyPopupActive = active }
                        )
                    }
                    KeyboardPage.ADVANCED -> {
                        AdvancedKeyboardLayout(
                            onKeyInsert = { txt, shift -> handleInsert(txt, shift) },
                            onBackspace = { handleBackspace() },
                            onMoveCursor = { dir -> handleMoveCursor(dir) },
                            onAC = { handleAC() },
                            onSolve = onSolve,
                            onSwitchPage = { currentPage = KeyboardPage.ALPHABET }
                        )
                    }
                    KeyboardPage.ALPHABET -> {
                        AlphabetKeyboardLayout(
                            onKeyInsert = { txt, shift -> handleInsert(txt, shift) },
                            onBackspace = { handleBackspace() },
                            onMoveCursor = { dir -> handleMoveCursor(dir) },
                            onAC = { handleAC() },
                            onSolve = onSolve,
                            onSwitchPage = { currentPage = KeyboardPage.BASIC }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeyboardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isBold: Boolean = false,
    fontSize: Int = 18
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Reusable modular button component with an instant press-and-drag floating popup menu.
 * When touched, a floating menu with multiple options appears above the button immediately.
 * Sliding finger left/right selects options, and lifting finger confirms selection.
 */
@Composable
fun PopupOptionButton(
    defaultText: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isBold: Boolean = false,
    fontSize: Int = 18,
    popupYOffsetDp: Int = -60,
    onPopupStateChanged: ((Boolean) -> Unit)? = null
) {
    val currentOnOptionSelected by rememberUpdatedState(onOptionSelected)
    val currentOnPopupStateChanged by rememberUpdatedState(onPopupStateChanged)
    var showPopup by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val stepPx = with(density) { 44.dp.toPx() }

    Box(
        modifier = modifier
            .pointerInput(options) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val startX = down.position.x

                        // Show popup menu immediately on press down
                        showPopup = true
                        currentOnPopupStateChanged?.invoke(true)
                        selectedIndex = 0

                        try {
                            // Track touch drag and release while finger is held down
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                change.consume()

                                val deltaX = change.position.x - startX
                                val newIndex = ((deltaX + stepPx / 2f) / stepPx)
                                    .toInt()
                                    .coerceIn(0, options.lastIndex)
                                selectedIndex = newIndex

                                if (!change.pressed) {
                                    // Finger released: trigger selection
                                    currentOnOptionSelected(options[selectedIndex])
                                    break
                                }
                            }
                        } finally {
                            showPopup = false
                            currentOnPopupStateChanged?.invoke(false)
                        }
                    }
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(containerColor)
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = defaultText,
                color = textColor,
                fontSize = fontSize.sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )
        }

        if (showPopup) {
            val yOffsetPx = with(density) { popupYOffsetDp.dp.roundToPx() }

            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = 0, y = yOffsetPx),
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = false, dismissOnClickOutside = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    tonalElevation = 2.dp,
                    modifier = Modifier.padding(2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        options.forEachIndexed { index, option ->
                            val isSelected = index == selectedIndex
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 40.dp, height = 40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                            ) {
                                Text(
                                    text = option,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else Color.Black,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.BasicKeyboardLayout(
    onKeyInsert: (String, Int) -> Unit,
    onBackspace: () -> Unit,
    onMoveCursor: (Int) -> Unit,
    onAC: () -> Unit,
    onSolve: () -> Unit,
    onSwitchPage: () -> Unit,
    onPopupStateChanged: ((Boolean) -> Unit)? = null
) {
    val darkBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val actionBg = MaterialTheme.colorScheme.primary
    val actionText = MaterialTheme.colorScheme.onPrimary

    // Row 1: AC, %, 7, 8, 9, ÷
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KeyboardButton("AC", onAC, Modifier.weight(1f), containerColor = darkBg, isBold = true)
        KeyboardButton("%", { onKeyInsert("%", 0) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("7", { onKeyInsert("7", 0) }, Modifier.weight(1f))
        KeyboardButton("8", { onKeyInsert("8", 0) }, Modifier.weight(1f))
        KeyboardButton("9", { onKeyInsert("9", 0) }, Modifier.weight(1f))
        KeyboardButton("÷", { onKeyInsert(" ÷ ", 0) }, Modifier.weight(1f), containerColor = darkBg)
    }

    // Row 2: x, √, 4, 5, 6, ×
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PopupOptionButton(
            defaultText = "x",
            options = remember { listOf("x", "y", "z", "a", "b", "c") },
            onOptionSelected = { option -> onKeyInsert(option, 0) },
            onPopupStateChanged = onPopupStateChanged,
            modifier = Modifier.weight(1f),
            containerColor = darkBg,
            isBold = true
        )
        KeyboardButton("√", { onKeyInsert("\\sqrt{}", -1) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("4", { onKeyInsert("4", 0) }, Modifier.weight(1f))
        KeyboardButton("5", { onKeyInsert("5", 0) }, Modifier.weight(1f))
        KeyboardButton("6", { onKeyInsert("6", 0) }, Modifier.weight(1f))
        KeyboardButton("×", { onKeyInsert(" × ", 0) }, Modifier.weight(1f), containerColor = darkBg)
    }

    // Row 3: ^2, fraction, 1, 2, 3, -
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KeyboardButton("x²", { onKeyInsert("^{2}", 0) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("□/□", { onKeyInsert("\\frac{}{}", -3) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("1", { onKeyInsert("1", 0) }, Modifier.weight(1f))
        KeyboardButton("2", { onKeyInsert("2", 0) }, Modifier.weight(1f))
        KeyboardButton("3", { onKeyInsert("3", 0) }, Modifier.weight(1f))
        KeyboardButton("-", { onKeyInsert(" - ", 0) }, Modifier.weight(1f), containerColor = darkBg)
    }

    // Row 4: (, ), 0, ., =, +
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KeyboardButton("(", { onKeyInsert("(", 0) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton(")", { onKeyInsert(")", 0) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("0", { onKeyInsert("0", 0) }, Modifier.weight(1.5f))
        KeyboardButton(".", { onKeyInsert(".", 0) }, Modifier.weight(0.8f))
        KeyboardButton("=", { onKeyInsert(" = ", 0) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("+", { onKeyInsert(" + ", 0) }, Modifier.weight(1f), containerColor = darkBg)
    }

    // Row 5: f/Δ, ←, →, ↵, ⌫, Send
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeyboardButton("f/Δ...", onSwitchPage, Modifier.weight(1.2f), containerColor = darkBg, isBold = true, fontSize = 15)
        KeyboardButton("←", { onMoveCursor(-1) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("→", { onMoveCursor(1) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("↵", { onKeyInsert("\n", 0) }, Modifier.weight(1f), containerColor = darkBg)

        // Delete button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(darkBg)
                .clickable { onBackspace() }
                .padding(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Backspace",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        // Send button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1.5f)
                .clip(RoundedCornerShape(8.dp))
                .background(actionBg)
                .clickable { onSolve() }
                .padding(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Solve",
                tint = actionText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ColumnScope.AdvancedKeyboardLayout(
    onKeyInsert: (String, Int) -> Unit,
    onBackspace: () -> Unit,
    onMoveCursor: (Int) -> Unit,
    onAC: () -> Unit,
    onSolve: () -> Unit,
    onSwitchPage: () -> Unit
) {
    val darkBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val actionBg = MaterialTheme.colorScheme.primary
    val actionText = MaterialTheme.colorScheme.onPrimary

    // Row 1: <, >, f(x), log, e^x, e
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KeyboardButton("<", { onKeyInsert(" < ", 0) }, Modifier.weight(1f))
        KeyboardButton(">", { onKeyInsert(" > ", 0) }, Modifier.weight(1f))
        KeyboardButton("f(x)", { onKeyInsert("f(x)", 0) }, Modifier.weight(1f))
        KeyboardButton("log", { onKeyInsert("\\log()", -1) }, Modifier.weight(1.2f))
        KeyboardButton("e^x", { onKeyInsert("e^{}", -1) }, Modifier.weight(1f))
        KeyboardButton("e", { onKeyInsert("e", 0) }, Modifier.weight(0.8f))
    }

    // Row 2: sin, cos, tan, π, °, i
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KeyboardButton("sin", { onKeyInsert("\\sin()", -1) }, Modifier.weight(1f))
        KeyboardButton("cos", { onKeyInsert("\\cos()", -1) }, Modifier.weight(1f))
        KeyboardButton("tan", { onKeyInsert("\\tan()", -1) }, Modifier.weight(1f))
        KeyboardButton("π", { onKeyInsert("\\pi", 0) }, Modifier.weight(0.8f))
        KeyboardButton("°", { onKeyInsert("°", 0) }, Modifier.weight(0.8f))
        KeyboardButton("i", { onKeyInsert("i", 0) }, Modifier.weight(0.8f))
    }

    // Row 3: |x|, ⌊x⌋, sum, int, !, max
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KeyboardButton("|x|", { onKeyInsert("abs()", -1) }, Modifier.weight(1f))
        KeyboardButton("⌊x⌋", { onKeyInsert("floor()", -1) }, Modifier.weight(1f))
        KeyboardButton("∑", { onKeyInsert("\\sum_{}^{}", -4) }, Modifier.weight(1f))
        KeyboardButton("∫", { onKeyInsert("\\int_{}^{}", -4) }, Modifier.weight(1f))
        KeyboardButton("!", { onKeyInsert("!", 0) }, Modifier.weight(0.8f))
        KeyboardButton("max", { onKeyInsert("\\max()", -1) }, Modifier.weight(1f))
    }

    // Row 4: d/dx, Integral, lim, ∞, nPr, gcf
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KeyboardButton("d/dx", { onKeyInsert("\\frac{d}{dx}", 0) }, Modifier.weight(1.2f))
        KeyboardButton("∫", { onKeyInsert("\\int", 0) }, Modifier.weight(0.8f))
        KeyboardButton("lim", { onKeyInsert("\\lim_{x \\to 0}", 0) }, Modifier.weight(1f))
        KeyboardButton("∞", { onKeyInsert("\\infty", 0) }, Modifier.weight(0.8f))
        KeyboardButton("nPr", { onKeyInsert("P", 0) }, Modifier.weight(0.8f))
        KeyboardButton("gcf", { onKeyInsert("\\gcf()", -1) }, Modifier.weight(1f))
    }

    // Row 5: xyz, ←, →, ↵, ⌫, Send
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeyboardButton("xyz...", onSwitchPage, Modifier.weight(1.2f), containerColor = darkBg, isBold = true, fontSize = 15)
        KeyboardButton("←", { onMoveCursor(-1) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("→", { onMoveCursor(1) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("↵", { onKeyInsert("\n", 0) }, Modifier.weight(1f), containerColor = darkBg)

        // Delete button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(darkBg)
                .clickable { onBackspace() }
                .padding(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Backspace",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        // Send button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1.5f)
                .clip(RoundedCornerShape(8.dp))
                .background(actionBg)
                .clickable { onSolve() }
                .padding(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Solve",
                tint = actionText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ColumnScope.AlphabetKeyboardLayout(
    onKeyInsert: (String, Int) -> Unit,
    onBackspace: () -> Unit,
    onMoveCursor: (Int) -> Unit,
    onAC: () -> Unit,
    onSolve: () -> Unit,
    onSwitchPage: () -> Unit
) {
    val darkBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val actionBg = MaterialTheme.colorScheme.primary
    val actionText = MaterialTheme.colorScheme.onPrimary

    val row1 = listOf("a", "b", "c", "d", "e", "f", "g", "h")
    val row2 = listOf("i", "j", "k", "l", "m", "n", "o", "p")
    val row3 = listOf("q", "r", "s", "t", "u", "v", "w", "τ")
    val row4 = listOf("α", "β", "γ", "π", "θ", "x", "y", "z")

    // Row 1: a b c d e f g h
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        row1.forEach { letter ->
            KeyboardButton(letter, { onKeyInsert(letter, 0) }, Modifier.weight(1f), fontSize = 16)
        }
    }

    // Row 2: i j k l m n o p
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        row2.forEach { letter ->
            KeyboardButton(letter, { onKeyInsert(letter, 0) }, Modifier.weight(1f), fontSize = 16)
        }
    }

    // Row 3: q r s t u v w τ
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        row3.forEach { item ->
            val latexCmd = if (item == "τ") "\\tau" else item
            KeyboardButton(item, { onKeyInsert(latexCmd, 0) }, Modifier.weight(1f), fontSize = 16)
        }
    }

    // Row 4: α β γ π θ x y z
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        row4.forEach { item ->
            val latexCmd = when (item) {
                "α" -> "\\alpha"
                "β" -> "\\beta"
                "γ" -> "\\gamma"
                "π" -> "\\pi"
                "θ" -> "\\theta"
                else -> item
            }
            KeyboardButton(item, { onKeyInsert(latexCmd, 0) }, Modifier.weight(1f), fontSize = 16)
        }
    }

    // Row 5: 123..., ←, →, ↵, ⌫, Send
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeyboardButton("123...", onSwitchPage, Modifier.weight(1.2f), containerColor = darkBg, isBold = true, fontSize = 15)
        KeyboardButton("←", { onMoveCursor(-1) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("→", { onMoveCursor(1) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("↵", { onKeyInsert("\n", 0) }, Modifier.weight(1f), containerColor = darkBg)

        // Delete button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(darkBg)
                .clickable { onBackspace() }
                .padding(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Backspace",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        // Send button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1.5f)
                .clip(RoundedCornerShape(8.dp))
                .background(actionBg)
                .clickable { onSolve() }
                .padding(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Solve",
                tint = actionText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
