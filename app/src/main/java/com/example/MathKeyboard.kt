package com.example

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

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
    canHideKeyboard: Boolean = true,
    onHideKeyboard: () -> Unit = {},
    onPasteRequest: (() -> Unit)? = null,
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
            val before = text.substring(0, start)
            val after = text.substring(start, text.length)

            val trailingSpacesInBefore = before.length - before.trimEnd().length
            val targetBefore = before.trimEnd()

            val leadingSpacesInAfter = after.length - after.trimStart().length
            val targetAfter = after.trimStart()

            var deleteBeforeCountInTarget = 1
            var deleteAfterCountInTarget = 0

            when {
                // 1. Fraction \frac{|}{} - cursor in empty numerator
                targetBefore.endsWith("\\frac{") && targetAfter.startsWith("}{}") -> {
                    deleteBeforeCountInTarget = 6
                    deleteAfterCountInTarget = 3
                }
                // 2. Fraction \frac{|}{...} - cursor in empty numerator, denominator has content or braces
                targetBefore.endsWith("\\frac{") && targetAfter.startsWith("}{") -> {
                    deleteBeforeCountInTarget = 6
                    deleteAfterCountInTarget = 2
                }
                // 3. Fraction cursor after \frac{
                targetBefore.endsWith("\\frac{") && targetAfter.startsWith("}") -> {
                    deleteBeforeCountInTarget = 6
                    deleteAfterCountInTarget = 1
                }
                // 4. Cursor right after empty fraction \frac{}{}`
                targetBefore.endsWith("\\frac{}{}") -> {
                    deleteBeforeCountInTarget = 10
                }
                // 5. Square root \sqrt{|}
                targetBefore.endsWith("\\sqrt{") && targetAfter.startsWith("}") -> {
                    deleteBeforeCountInTarget = 6
                    deleteAfterCountInTarget = 1
                }
                targetBefore.endsWith("\\sqrt{}") -> {
                    deleteBeforeCountInTarget = 7
                }
                // 6. Power ^{|}
                targetBefore.endsWith("^{") && targetAfter.startsWith("}") -> {
                    deleteBeforeCountInTarget = 2
                    deleteAfterCountInTarget = 1
                }
                targetBefore.endsWith("^{}") -> {
                    deleteBeforeCountInTarget = 3
                }
                // 7. Subscript _{|}
                targetBefore.endsWith("_{") && targetAfter.startsWith("}") -> {
                    deleteBeforeCountInTarget = 2
                    deleteAfterCountInTarget = 1
                }
                targetBefore.endsWith("_{}") -> {
                    deleteBeforeCountInTarget = 3
                }
                // 8. Vector \vec{|}
                targetBefore.endsWith("\\vec{") && targetAfter.startsWith("}") -> {
                    deleteBeforeCountInTarget = 5
                    deleteAfterCountInTarget = 1
                }
                // 9. Functions with parens e.g. \sin(), \cos(), \tan(), \log(), \ln()
                targetBefore.endsWith("\\sin(") && targetAfter.startsWith(")") -> { deleteBeforeCountInTarget = 5; deleteAfterCountInTarget = 1 }
                targetBefore.endsWith("\\cos(") && targetAfter.startsWith(")") -> { deleteBeforeCountInTarget = 5; deleteAfterCountInTarget = 1 }
                targetBefore.endsWith("\\tan(") && targetAfter.startsWith(")") -> { deleteBeforeCountInTarget = 5; deleteAfterCountInTarget = 1 }
                targetBefore.endsWith("\\log(") && targetAfter.startsWith(")") -> { deleteBeforeCountInTarget = 5; deleteAfterCountInTarget = 1 }
                targetBefore.endsWith("\\ln(") && targetAfter.startsWith(")") -> { deleteBeforeCountInTarget = 4; deleteAfterCountInTarget = 1 }
                targetBefore.endsWith("abs(") && targetAfter.startsWith(")") -> { deleteBeforeCountInTarget = 4; deleteAfterCountInTarget = 1 }
                targetBefore.endsWith("floor(") && targetAfter.startsWith(")") -> { deleteBeforeCountInTarget = 6; deleteAfterCountInTarget = 1 }
                targetBefore.endsWith("\\max(") && targetAfter.startsWith(")") -> { deleteBeforeCountInTarget = 5; deleteAfterCountInTarget = 1 }
                targetBefore.endsWith("\\gcf(") && targetAfter.startsWith(")") -> { deleteBeforeCountInTarget = 5; deleteAfterCountInTarget = 1 }
                // 10. Atomic LaTeX command words e.g. \alpha, \beta, \theta, \pi, \le, \ge, \neq, \approx
                Regex("""\\[a-zA-Z]+$""").containsMatchIn(targetBefore) -> {
                    val match = Regex("""\\[a-zA-Z]+$""").find(targetBefore)
                    if (match != null) {
                        deleteBeforeCountInTarget = match.value.length
                    }
                }
            }

            val remBeforeInTarget = targetBefore.substring(0, (targetBefore.length - deleteBeforeCountInTarget).coerceAtLeast(0))
            val leadingSpacesInTarget = remBeforeInTarget.length - remBeforeInTarget.trimEnd().length

            val opChars = setOf('+', '-', '=', '<', '>', '*', '/', ',', ':', ';', '!', '×', '÷', '±', '∓', '≤', '≥', '≠', '≈', '≪', '≫')
            val isOperatorOrCommand = targetBefore.endsWith("}") || targetBefore.endsWith(")") ||
                    Regex("""\\[a-zA-Z]+$""").containsMatchIn(targetBefore) ||
                    (targetBefore.isNotEmpty() && targetBefore.last() in opChars)

            val totalDeleteBefore = if (isOperatorOrCommand) {
                trailingSpacesInBefore + deleteBeforeCountInTarget + leadingSpacesInTarget
            } else {
                trailingSpacesInBefore + deleteBeforeCountInTarget
            }.coerceAtMost(before.length)

            val totalDeleteAfter = (leadingSpacesInAfter + deleteAfterCountInTarget).coerceAtMost(after.length)

            val newBefore = before.substring(0, before.length - totalDeleteBefore)
            val newAfter = after.substring(totalDeleteAfter)

            val newCursorPos = newBefore.length
            onValueChange(
                TextFieldValue(
                    text = newBefore + newAfter,
                    selection = TextRange(newCursorPos, newCursorPos)
                )
            )
        }
    }

    fun getValidCursorPositions(text: String): BooleanArray {
        val len = text.length
        val valid = BooleanArray(len + 1) { true }

        val cmdRegex = Regex("""\\[a-zA-Z]+""")
        for (match in cmdRegex.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            for (p in (start + 1) until end) {
                valid[p] = false
            }
        }

        val cmdBraceRegex = Regex("""\\[a-zA-Z]+[\{_]""")
        for (match in cmdBraceRegex.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            for (p in (start + 1) until end) {
                valid[p] = false
            }
        }

        val funcRegex = Regex("""(\\[a-zA-Z]+|abs|floor)\(""")
        for (match in funcRegex.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            for (p in (start + 1) until end) {
                valid[p] = false
            }
        }

        for (i in 0 until len - 1) {
            if (text[i] == '}' && text[i + 1] == '{') {
                valid[i + 1] = false
            }
            if (text[i] == ']' && text[i + 1] == '{') {
                valid[i + 1] = false
            }
            if (text[i] == '^' && text[i + 1] == '{') {
                valid[i + 1] = false
            }
            if (text[i] == '_' && text[i + 1] == '{') {
                valid[i + 1] = false
            }
        }

        for (i in 0 until len - 2) {
            if (text[i] == '}' && text[i + 1] == '^' && text[i + 2] == '{') {
                valid[i + 1] = false
                valid[i + 2] = false
            }
        }

        // Space skipping rules:
        // Ignore space positions between elements so moving cursor skips spaces.
        val opChars = setOf('+', '-', '=', '<', '>', '*', '/', ',', ':', ';', '!', '×', '÷', '±', '∓', '≤', '≥', '≠', '≈', '≪', '≫')
        for (p in 1 until len) {
            val prevChar = text[p - 1]
            val currChar = text[p]

            // 1. Position between two spaces -> invalid
            if (prevChar == ' ' && currChar == ' ') {
                valid[p] = false
            }
            // 2. Position between space and operator/command -> invalid (e.g. "x |+ 2" or "x |\le 2")
            if (prevChar == ' ' && (currChar in opChars || currChar == '\\')) {
                valid[p] = false
            }
            // 3. Position between operator/command and trailing space -> invalid (e.g. "x +| 2" or "x \le| 2")
            if ((prevChar in opChars || prevChar == '}' || prevChar == ')') && currChar == ' ') {
                valid[p] = false
            }
        }

        valid[0] = true
        valid[len] = true
        return valid
    }

    fun handleMoveCursor(direction: Int) {
        val text = fieldValue.text
        if (text.isEmpty()) return

        val validPositions = getValidCursorPositions(text)
        val selection = fieldValue.selection
        val currentPos = selection.start.coerceIn(0, text.length)

        var newPos = currentPos
        if (direction > 0) {
            for (p in (currentPos + 1)..text.length) {
                if (validPositions[p]) {
                    newPos = p
                    break
                }
            }
        } else if (direction < 0) {
            for (p in (currentPos - 1) downTo 0) {
                if (validPositions[p]) {
                    newPos = p
                    break
                }
            }
        }

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

    fun handleSmartBracket() {
        val updated = SmartBracketHelper.processBracketInput(fieldValue)
        onValueChange(updated)
    }

    val coroutineScope = rememberCoroutineScope()
    val dragOffsetAnimatable = remember { Animatable(0f) }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }

    var pageSwitchJob by remember { mutableStateOf<Job?>(null) }
    var pendingTargetPage by remember { mutableStateOf<KeyboardPage?>(null) }

    fun commitPendingPageIfAny() {
        val pending = pendingTargetPage
        if (pending != null && pending != currentPage) {
            pageSwitchJob?.cancel()
            currentPage = pending
            pendingTargetPage = null
            coroutineScope.launch {
                dragOffsetAnimatable.snapTo(0f)
            }
        }
    }

    fun KeyboardPage.previous(): KeyboardPage = when (this) {
        KeyboardPage.BASIC -> KeyboardPage.ALPHABET
        KeyboardPage.ADVANCED -> KeyboardPage.BASIC
        KeyboardPage.ALPHABET -> KeyboardPage.ADVANCED
    }

    fun KeyboardPage.next(): KeyboardPage = when (this) {
        KeyboardPage.ALPHABET -> KeyboardPage.BASIC
        KeyboardPage.BASIC -> KeyboardPage.ADVANCED
        KeyboardPage.ADVANCED -> KeyboardPage.ALPHABET
    }

    val switchPageTo: (KeyboardPage) -> Unit = { target ->
        commitPendingPageIfAny()
        if (target != currentPage) {
            val isLeftToRight = (target == KeyboardPage.ALPHABET && currentPage == KeyboardPage.BASIC) ||
                                (target == KeyboardPage.BASIC && currentPage == KeyboardPage.ADVANCED) ||
                                (target == KeyboardPage.ADVANCED && currentPage == KeyboardPage.ALPHABET)
            pageSwitchJob?.cancel()
            pendingTargetPage = target
            pageSwitchJob = coroutineScope.launch {
                val width = if (containerWidthPx > 0f) containerWidthPx else 1000f
                val targetOffset = if (isLeftToRight) width else -width
                try {
                    dragOffsetAnimatable.animateTo(
                        targetValue = targetOffset,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )
                    currentPage = target
                    dragOffsetAnimatable.snapTo(0f)
                } finally {
                    if (currentPage == target) {
                        pendingTargetPage = null
                    }
                }
            }
        }
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

                        // 核心修复：如果上一轮滑动的翻页动画正在执行中，瞬间按下时立即将页面状态落盘提交到目标页
                        if (pendingTargetPage != null && pendingTargetPage != currentPage) {
                            pageSwitchJob?.cancel()
                            currentPage = pendingTargetPage!!
                            pendingTargetPage = null
                            coroutineScope.launch {
                                dragOffsetAnimatable.snapTo(0f)
                            }
                        }

                        val startX = down.position.x
                        var lastX = startX
                        var isDragging = false

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: break
                            lastX = change.position.x

                            if (change.pressed) {
                                val deltaX = lastX - startX
                                if (!isAnyPopupActive) {
                                    if (abs(deltaX) > 12f || isDragging) {
                                        isDragging = true
                                        pageSwitchJob?.cancel()
                                        coroutineScope.launch {
                                            dragOffsetAnimatable.snapTo(deltaX)
                                        }
                                    }
                                } else {
                                    if (isDragging) {
                                        isDragging = false
                                        coroutineScope.launch {
                                            dragOffsetAnimatable.snapTo(0f)
                                        }
                                    }
                                }
                            } else {
                                if (isDragging && !isAnyPopupActive && containerWidthPx > 0f) {
                                    val finalDeltaX = lastX - startX
                                    val threshold = containerWidthPx * 0.16f
                                    val targetPage = when {
                                        finalDeltaX > threshold -> currentPage.previous()
                                        finalDeltaX < -threshold -> currentPage.next()
                                        else -> null
                                    }

                                    pageSwitchJob?.cancel()
                                    if (targetPage != null) {
                                        pendingTargetPage = targetPage
                                        pageSwitchJob = coroutineScope.launch {
                                            val targetOffset = if (finalDeltaX > 0) containerWidthPx else -containerWidthPx
                                            try {
                                                dragOffsetAnimatable.animateTo(
                                                    targetValue = targetOffset,
                                                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                                                )
                                                currentPage = targetPage
                                                dragOffsetAnimatable.snapTo(0f)
                                            } finally {
                                                if (currentPage == targetPage) {
                                                    pendingTargetPage = null
                                                }
                                            }
                                        }
                                    } else {
                                        pendingTargetPage = null
                                        pageSwitchJob = coroutineScope.launch {
                                            dragOffsetAnimatable.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                            )
                                        }
                                    }
                                } else if (isDragging) {
                                    pageSwitchJob?.cancel()
                                    pageSwitchJob = coroutineScope.launch {
                                        dragOffsetAnimatable.snapTo(0f)
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
        val context = LocalContext.current

        // Keyboard Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KeyboardTabChip("字母", currentPage == KeyboardPage.ALPHABET) { switchPageTo(KeyboardPage.ALPHABET) }
                KeyboardTabChip("基础", currentPage == KeyboardPage.BASIC) { switchPageTo(KeyboardPage.BASIC) }
                KeyboardTabChip("高级", currentPage == KeyboardPage.ADVANCED) { switchPageTo(KeyboardPage.ADVANCED) }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (fieldValue.text.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                ClipboardUtils.showCopySelector(context, fieldValue.text)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "复制",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            if (onPasteRequest != null) {
                                onPasteRequest()
                            } else {
                                ClipboardUtils.showPasteSelector(context)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "粘贴",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }



                if (canHideKeyboard) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { onHideKeyboard() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
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
            }
        }

        val currentOffset = dragOffsetAnimatable.value

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { containerWidthPx = it.width.toFloat() }
                .clipToBounds()
        ) {
            val width = if (containerWidthPx > 0f) containerWidthPx else 1000f

            if (currentOffset > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = currentOffset }
                ) {
                    RenderKeyboardPage(
                        page = currentPage,
                        onKeyInsert = { txt, shift -> handleInsert(txt, shift) },
                        onSmartBracket = { handleSmartBracket() },
                        onAC = { handleAC() },
                        onPopupStateChanged = { active -> isAnyPopupActive = active }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = currentOffset - width }
                ) {
                    RenderKeyboardPage(
                        page = currentPage.previous(),
                        onKeyInsert = { txt, shift -> handleInsert(txt, shift) },
                        onSmartBracket = { handleSmartBracket() },
                        onAC = { handleAC() },
                        onPopupStateChanged = { active -> isAnyPopupActive = active }
                    )
                }
            } else if (currentOffset < 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = currentOffset }
                ) {
                    RenderKeyboardPage(
                        page = currentPage,
                        onKeyInsert = { txt, shift -> handleInsert(txt, shift) },
                        onSmartBracket = { handleSmartBracket() },
                        onAC = { handleAC() },
                        onPopupStateChanged = { active -> isAnyPopupActive = active }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = currentOffset + width }
                ) {
                    RenderKeyboardPage(
                        page = currentPage.next(),
                        onKeyInsert = { txt, shift -> handleInsert(txt, shift) },
                        onSmartBracket = { handleSmartBracket() },
                        onAC = { handleAC() },
                        onPopupStateChanged = { active -> isAnyPopupActive = active }
                    )
                }
            } else {
                RenderKeyboardPage(
                    page = currentPage,
                    onKeyInsert = { txt, shift -> handleInsert(txt, shift) },
                    onSmartBracket = { handleSmartBracket() },
                    onAC = { handleAC() },
                    onPopupStateChanged = { active -> isAnyPopupActive = active }
                )
            }
        }

        // Fixed bottom row (←, →, ↵, ⌫, ➤) that stays static and does not slide with keyboard pages
        FixedBottomActionRow(
            onMoveCursor = { dir -> handleMoveCursor(dir) },
            onKeyInsert = { txt, shift -> handleInsert(txt, shift) },
            onBackspace = { handleBackspace() },
            onSolve = onSolve
        )
    }
}

@Composable
private fun FixedBottomActionRow(
    onMoveCursor: (Int) -> Unit,
    onKeyInsert: (String, Int) -> Unit,
    onBackspace: () -> Unit,
    onSolve: () -> Unit
) {
    val darkBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val actionBg = MaterialTheme.colorScheme.primary
    val actionText = MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        // Send / Solve button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1.2f)
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
private fun RenderKeyboardPage(
    page: KeyboardPage,
    onKeyInsert: (String, Int) -> Unit,
    onSmartBracket: () -> Unit,
    onAC: () -> Unit,
    onPopupStateChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (page) {
            KeyboardPage.BASIC -> {
                BasicKeyboardLayout(
                    onKeyInsert = onKeyInsert,
                    onSmartBracket = onSmartBracket,
                    onAC = onAC,
                    onPopupStateChanged = onPopupStateChanged
                )
            }
            KeyboardPage.ADVANCED -> {
                AdvancedKeyboardLayout(
                    onKeyInsert = onKeyInsert,
                    onPopupStateChanged = onPopupStateChanged
                )
            }
            KeyboardPage.ALPHABET -> {
                AlphabetKeyboardLayout(
                    onKeyInsert = onKeyInsert
                )
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
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 8.dp,
                    tonalElevation = 4.dp,
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
                                            else MaterialTheme.colorScheme.onSurface,
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
    onSmartBracket: () -> Unit,
    onAC: () -> Unit,
    onPopupStateChanged: ((Boolean) -> Unit)? = null
) {
    val darkBg = MaterialTheme.colorScheme.surfaceContainerHigh

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
        PopupOptionButton(
            defaultText = "√",
            options = remember { listOf("√", "ⁿ√", "∛", "∜") },
            onOptionSelected = { option ->
                when (option) {
                    "√" -> onKeyInsert("\\sqrt{}", -1)
                    "ⁿ√" -> onKeyInsert("\\sqrt[]{}", -3)
                    "∛" -> onKeyInsert("\\sqrt[3]{}", -1)
                    "∜" -> onKeyInsert("\\sqrt[4]{}", -1)
                    else -> onKeyInsert("\\sqrt{}", -1)
                }
            },
            onPopupStateChanged = onPopupStateChanged,
            modifier = Modifier.weight(1f),
            containerColor = darkBg,
            isBold = true
        )
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
        PopupOptionButton(
            defaultText = "x²",
            options = remember { listOf("x²", "x³", "xⁿ") },
            onOptionSelected = { option ->
                when (option) {
                    "x²" -> onKeyInsert("^{2}", 0)
                    "x³" -> onKeyInsert("^{3}", 0)
                    "xⁿ" -> onKeyInsert("^{}", -1)
                    else -> onKeyInsert("^{2}", 0)
                }
            },
            onPopupStateChanged = onPopupStateChanged,
            modifier = Modifier.weight(1f),
            containerColor = darkBg,
            isBold = true
        )
        KeyboardButton("□/□", { onKeyInsert("\\frac{}{}", -3) }, Modifier.weight(1f), containerColor = darkBg)
        KeyboardButton("1", { onKeyInsert("1", 0) }, Modifier.weight(1f))
        KeyboardButton("2", { onKeyInsert("2", 0) }, Modifier.weight(1f))
        KeyboardButton("3", { onKeyInsert("3", 0) }, Modifier.weight(1f))
        KeyboardButton("-", { onKeyInsert(" - ", 0) }, Modifier.weight(1f), containerColor = darkBg)
    }

    // Row 4: ( ), 0, ., =, +
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        KeyboardButton(
            text = "( )",
            onClick = onSmartBracket,
            modifier = Modifier.weight(1f),
            containerColor = darkBg,
            isBold = true
        )
        KeyboardButton("0", { onKeyInsert("0", 0) }, Modifier.weight(2f))
        PopupOptionButton(
            defaultText = ".",
            options = remember { listOf(".", ",", ";") },
            onOptionSelected = { option ->
                when (option) {
                    "." -> onKeyInsert(".", 0)
                    "," -> onKeyInsert(",", 0)
                    ";" -> onKeyInsert(";", 0)
                    else -> onKeyInsert(".", 0)
                }
            },
            onPopupStateChanged = onPopupStateChanged,
            modifier = Modifier.weight(1f),
            isBold = true
        )
        PopupOptionButton(
            defaultText = "=",
            options = remember { listOf("=", "≠") },
            onOptionSelected = { option ->
                when (option) {
                    "=" -> onKeyInsert(" = ", 0)
                    "≠" -> onKeyInsert(" \\neq ", 0)
                    else -> onKeyInsert(" = ", 0)
                }
            },
            onPopupStateChanged = onPopupStateChanged,
            modifier = Modifier.weight(1f),
            containerColor = darkBg,
            isBold = true
        )
        KeyboardButton("+", { onKeyInsert(" + ", 0) }, Modifier.weight(1f), containerColor = darkBg)
    }
}

@Composable
fun ColumnScope.AdvancedKeyboardLayout(
    onKeyInsert: (String, Int) -> Unit,
    onPopupStateChanged: ((Boolean) -> Unit)? = null
) {
    val darkBg = MaterialTheme.colorScheme.surfaceContainerHigh

    // Row 1: <, >, f(x), log, e^x, e
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PopupOptionButton(
            defaultText = "<",
            options = remember { listOf("<", "≤", "≠") },
            onOptionSelected = { option ->
                when (option) {
                    "<" -> onKeyInsert(" < ", 0)
                    "≤" -> onKeyInsert(" \\le ", 0)
                    "≠" -> onKeyInsert(" \\neq ", 0)
                    else -> onKeyInsert(" < ", 0)
                }
            },
            onPopupStateChanged = onPopupStateChanged,
            modifier = Modifier.weight(1f),
            containerColor = darkBg,
            isBold = true
        )
        PopupOptionButton(
            defaultText = ">",
            options = remember { listOf(">", "≥", "≠") },
            onOptionSelected = { option ->
                when (option) {
                    ">" -> onKeyInsert(" > ", 0)
                    "≥" -> onKeyInsert(" \\ge ", 0)
                    "≠" -> onKeyInsert(" \\neq ", 0)
                    else -> onKeyInsert(" > ", 0)
                }
            },
            onPopupStateChanged = onPopupStateChanged,
            modifier = Modifier.weight(1f),
            containerColor = darkBg,
            isBold = true
        )
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
}

@Composable
fun ColumnScope.AlphabetKeyboardLayout(
    onKeyInsert: (String, Int) -> Unit
) {
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
}
