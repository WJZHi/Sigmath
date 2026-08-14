package com.example

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CharacterNodeMarker(
    val bounds: Rect,
    val startIndex: Int,
    val endIndex: Int
)

@Composable
fun BlinkingCursor(
    fontSize: TextUnit = 24.sp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "cursorAlpha"
    )

    val cursorHeight = with(LocalDensity.current) { fontSize.toDp() * 1.25f }

    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .width(2.5.dp)
            .height(cursorHeight)
            .background(color.copy(alpha = alpha), shape = RoundedCornerShape(1.dp))
    )
}

@Composable
fun SystemLeftBrace(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(14.dp)
            .fillMaxHeight()
            .drawBehind {
                val w = size.width
                val h = size.height
                val sw = 2.2.dp.toPx()
                val midY = h / 2f

                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w, 2f)
                    cubicTo(w * 0.3f, 2f, 0.25f * w, h * 0.08f, 0.25f * w, h * 0.18f)
                    lineTo(0.25f * w, midY - 8.dp.toPx())
                    cubicTo(0.25f * w, midY - 2.dp.toPx(), -0.1f * w, midY, -0.1f * w, midY)
                    cubicTo(-0.1f * w, midY, 0.25f * w, midY + 2.dp.toPx(), 0.25f * w, midY + 8.dp.toPx())
                    lineTo(0.25f * w, h - h * 0.18f)
                    cubicTo(0.25f * w, h - h * 0.08f, w * 0.3f, h - 2f, w, h - 2f)
                }
                drawPath(
                    path = path,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = sw,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
    )
}

@Composable
fun MathRenderer(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    textColor: Color = LocalContentColor.current,
    cursorPosition: Int? = null,
    isFocused: Boolean = false,
    onCursorPositionChange: ((Int) -> Unit)? = null,
    onLongPress: ((Offset) -> Unit)? = null
) {
    if (latex.contains("\n")) {
        val rawLines = latex.split("\n")
        if (rawLines.size > 1) {
            val lineStartIndices = remember(rawLines) {
                val indices = IntArray(rawLines.size)
                var curr = 0
                for (i in rawLines.indices) {
                    indices[i] = curr
                    curr += rawLines[i].length + 1
                }
                indices
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = modifier
                    .height(IntrinsicSize.Min)
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                SystemLeftBrace(color = textColor)
                Spacer(modifier = Modifier.width(6.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    for (i in rawLines.indices) {
                        val lineText = rawLines[i]
                        val lineStart = lineStartIndices[i]
                        val lineEnd = lineStart + lineText.length
                        val lineCursor = if (isFocused && cursorPosition != null && cursorPosition in lineStart..lineEnd) {
                            cursorPosition - lineStart
                        } else null

                        MathRenderer(
                            latex = lineText,
                            fontSize = fontSize,
                            textColor = textColor,
                            cursorPosition = lineCursor,
                            isFocused = isFocused && (lineCursor != null),
                            onCursorPositionChange = if (onCursorPositionChange != null) {
                                { localPos -> onCursorPositionChange(lineStart + localPos) }
                            } else null,
                            onLongPress = onLongPress
                        )
                    }
                }
            }
            return
        }
    }

    val node = remember(latex, cursorPosition, isFocused) {
        try {
            if (isFocused && cursorPosition != null) {
                MathParser.parse(latex, cursorPosition)
            } else {
                MathParser.parse(latex)
            }
        } catch (e: Throwable) {
            SafeLog.e("MathRenderer", "Failed to parse LaTeX: $latex", e)
            MathNode.Text(latex)
        }
    }

    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val nodeMarkers = remember { mutableStateListOf<CharacterNodeMarker>() }

    SideEffect {
        nodeMarkers.clear()
    }

    val boxModifier = modifier
        .onGloballyPositioned { rootCoordinates = it }
        .then(
            if (onCursorPositionChange != null || onLongPress != null) {
                Modifier.pointerInput(latex, nodeMarkers.size) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            if (onCursorPositionChange != null) {
                                val targetIndex = calculateCursorIndexFromTap(tapOffset, nodeMarkers, latex.length)
                                onCursorPositionChange(targetIndex)
                            }
                        },
                        onLongPress = { longPressOffset ->
                            if (onCursorPositionChange != null) {
                                val targetIndex = calculateCursorIndexFromTap(longPressOffset, nodeMarkers, latex.length)
                                onCursorPositionChange(targetIndex)
                            }
                            onLongPress?.invoke(longPressOffset)
                        }
                    )
                }
            } else Modifier
        )

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.CenterStart
    ) {
        RenderMathNode(
            node = node,
            fontSize = fontSize,
            textColor = textColor,
            rootCoordinates = rootCoordinates,
            onRegisterMarker = { marker -> nodeMarkers.add(marker) }
        )
    }
}

private fun calculateCursorIndexFromTap(
    tapOffset: Offset,
    markers: List<CharacterNodeMarker>,
    latexLength: Int
): Int {
    if (markers.isEmpty()) {
        return if (tapOffset.x <= 0) 0 else latexLength
    }

    val validMarkers = markers.filter { it.startIndex >= 0 && it.endIndex >= 0 }
    if (validMarkers.isEmpty()) return latexLength

    var closestMarker: CharacterNodeMarker = validMarkers.first()
    var minDistance = Float.MAX_VALUE

    for (marker in validMarkers) {
        val dx = when {
            tapOffset.x < marker.bounds.left -> marker.bounds.left - tapOffset.x
            tapOffset.x > marker.bounds.right -> tapOffset.x - marker.bounds.right
            else -> 0f
        }
        val dy = when {
            tapOffset.y < marker.bounds.top -> marker.bounds.top - tapOffset.y
            tapOffset.y > marker.bounds.bottom -> tapOffset.y - marker.bounds.bottom
            else -> 0f
        }
        val dist = dx * dx + dy * dy
        if (dist < minDistance) {
            minDistance = dist
            closestMarker = marker
        }
    }

    return if (tapOffset.x < closestMarker.bounds.center.x) {
        closestMarker.startIndex.coerceIn(0, latexLength)
    } else {
        closestMarker.endIndex.coerceIn(0, latexLength)
    }
}

fun isEmptySlot(node: MathNode?): Boolean {
    if (node == null) return true
    return when (node) {
        is MathNode.Cursor -> true
        is MathNode.Text -> node.text.isEmpty()
        is MathNode.Row -> node.children.isEmpty() || node.children.all { isEmptySlot(it) }
        else -> false
    }
}

@Composable
fun PlaceholderBox(
    fontSize: TextUnit,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val minW = with(density) { fontSize.toDp() * 0.85f }
    val minH = with(density) { fontSize.toDp() * 1.15f }

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = minW, minHeight = minH)
            .padding(horizontal = 1.5.dp, vertical = 1.dp)
            .drawBehind {
                val strokeWidth = 1.2.dp.toPx()
                val dashLength = 3.dp.toPx()
                val gapLength = 2.5.dp.toPx()
                val effect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(dashLength, gapLength), 0f
                )
                drawRoundRect(
                    color = borderColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokeWidth,
                        pathEffect = effect
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun RenderMathSlot(
    node: MathNode?,
    fontSize: TextUnit,
    textColor: Color,
    rootCoordinates: LayoutCoordinates? = null,
    onRegisterMarker: ((CharacterNodeMarker) -> Unit)? = null
) {
    if (isEmptySlot(node)) {
        PlaceholderBox(fontSize = fontSize) {
            if (node != null) {
                RenderMathNode(node, fontSize, textColor, rootCoordinates, onRegisterMarker)
            }
        }
    } else if (node != null) {
        RenderMathNode(node, fontSize, textColor, rootCoordinates, onRegisterMarker)
    }
}

@Composable
fun RenderMathNode(
    node: MathNode,
    fontSize: TextUnit,
    textColor: Color,
    rootCoordinates: LayoutCoordinates? = null,
    onRegisterMarker: ((CharacterNodeMarker) -> Unit)? = null
) {
    val sizeVal = fontSize.value

    when (node) {
        is MathNode.Cursor -> {
            BlinkingCursor(fontSize = fontSize, color = MaterialTheme.colorScheme.primary)
        }
        is MathNode.Text -> {
            var nodeModifier: Modifier = Modifier
            if (rootCoordinates != null && onRegisterMarker != null && node.startIndex >= 0 && node.endIndex >= 0) {
                nodeModifier = nodeModifier.onGloballyPositioned { coords ->
                    if (rootCoordinates.isAttached && coords.isAttached) {
                        val boundsInRoot = rootCoordinates.localBoundingBoxOf(coords)
                        onRegisterMarker(CharacterNodeMarker(boundsInRoot, node.startIndex, node.endIndex))
                    }
                }
            }
            Text(
                text = node.text,
                fontSize = fontSize,
                fontStyle = if (node.isItalic) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (node.isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                color = textColor,
                modifier = nodeModifier
            )
        }
        is MathNode.Operator -> {
            var nodeModifier: Modifier = Modifier.padding(horizontal = 4.dp)
            if (rootCoordinates != null && onRegisterMarker != null && node.startIndex >= 0 && node.endIndex >= 0) {
                nodeModifier = nodeModifier.onGloballyPositioned { coords ->
                    if (rootCoordinates.isAttached && coords.isAttached) {
                        val boundsInRoot = rootCoordinates.localBoundingBoxOf(coords)
                        onRegisterMarker(CharacterNodeMarker(boundsInRoot, node.startIndex, node.endIndex))
                    }
                }
            }
            Text(
                text = node.op,
                fontSize = fontSize,
                fontWeight = FontWeight.Normal,
                color = textColor,
                modifier = nodeModifier
            )
        }
        is MathNode.SpecialSymbol -> {
            var nodeModifier: Modifier = Modifier
            if (rootCoordinates != null && onRegisterMarker != null && node.startIndex >= 0 && node.endIndex >= 0) {
                nodeModifier = nodeModifier.onGloballyPositioned { coords ->
                    if (rootCoordinates.isAttached && coords.isAttached) {
                        val boundsInRoot = rootCoordinates.localBoundingBoxOf(coords)
                        onRegisterMarker(CharacterNodeMarker(boundsInRoot, node.startIndex, node.endIndex))
                    }
                }
            }
            Text(
                text = node.symbol,
                fontSize = fontSize,
                fontStyle = FontStyle.Normal,
                color = textColor,
                modifier = nodeModifier
            )
        }
        is MathNode.Fraction -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Box(modifier = Modifier.padding(bottom = 2.dp)) {
                    RenderMathSlot(node.numerator, (sizeVal * 0.85f).sp, textColor, rootCoordinates, onRegisterMarker)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(textColor)
                )
                Box(modifier = Modifier.padding(top = 2.dp)) {
                    RenderMathSlot(node.denominator, (sizeVal * 0.85f).sp, textColor, rootCoordinates, onRegisterMarker)
                }
            }
        }
        is MathNode.Sqrt -> {
            val radicalWidth = (sizeVal * 0.55f).coerceAtLeast(10f).dp
            val strokeWidth = (sizeVal * 0.08f).coerceIn(1.2f, 2.5f).dp

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                if (node.rootIndex != null) {
                    Box(
                        modifier = Modifier
                            .padding(end = 1.dp)
                            .offset(y = (-3).dp)
                    ) {
                        RenderMathSlot(node.rootIndex, (sizeVal * 0.65f).sp, textColor, rootCoordinates, onRegisterMarker)
                    }
                }
                Box(
                    modifier = Modifier
                        .drawBehind {
                            val strokeWidthPx = strokeWidth.toPx()
                            val radicalWidthPx = radicalWidth.toPx()
                            val topBarY = strokeWidthPx / 2f + 1.dp.toPx()
                            val bottomY = size.height - strokeWidthPx / 2f - 1.dp.toPx()
                            val tickStartY = topBarY + (bottomY - topBarY) * 0.52f
                            val tickStartX = 1.dp.toPx()
                            val bottomX = radicalWidthPx * 0.38f
                            val peakX = radicalWidthPx - strokeWidthPx / 2f
                            val endX = size.width - 1.dp.toPx()

                            val radicalPath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(tickStartX, tickStartY)
                                lineTo(bottomX, bottomY)
                                lineTo(peakX, topBarY)
                                lineTo(endX, topBarY)
                            }
                            drawPath(
                                path = radicalPath,
                                color = textColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = strokeWidthPx,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                        .padding(
                            start = radicalWidth + 1.dp,
                            top = strokeWidth + 4.dp,
                            end = 3.dp,
                            bottom = 2.dp
                        )
                ) {
                    RenderMathSlot(node.content, fontSize, textColor, rootCoordinates, onRegisterMarker)
                }
            }
        }
        is MathNode.Power -> {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RenderMathSlot(node.base, fontSize, textColor, rootCoordinates, onRegisterMarker)
                Box(
                    modifier = Modifier
                        .align(Alignment.Top)
                        .offset(y = (-4).dp)
                ) {
                    RenderMathSlot(node.exponent, (sizeVal * 0.65f).sp, textColor, rootCoordinates, onRegisterMarker)
                }
            }
        }
        is MathNode.Subscript -> {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RenderMathSlot(node.base, fontSize, textColor, rootCoordinates, onRegisterMarker)
                Box(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .offset(y = 2.dp)
                ) {
                    RenderMathSlot(node.subscript, (sizeVal * 0.65f).sp, textColor, rootCoordinates, onRegisterMarker)
                }
            }
        }
        is MathNode.Parentheses -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 1.dp)
            ) {
                Text(
                    text = "(",
                    fontSize = (sizeVal * 1.1f).sp,
                    fontWeight = FontWeight.Light,
                    color = textColor
                )
                Box(modifier = Modifier.padding(horizontal = 1.dp)) {
                    RenderMathSlot(node.content, fontSize, textColor, rootCoordinates, onRegisterMarker)
                }
                Text(
                    text = ")",
                    fontSize = (sizeVal * 1.1f).sp,
                    fontWeight = FontWeight.Light,
                    color = textColor
                )
            }
        }
        is MathNode.SquareBrackets -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 1.dp)
            ) {
                Text(
                    text = "[",
                    fontSize = (sizeVal * 1.1f).sp,
                    fontWeight = FontWeight.Light,
                    color = textColor
                )
                Box(modifier = Modifier.padding(horizontal = 1.dp)) {
                    RenderMathSlot(node.content, fontSize, textColor, rootCoordinates, onRegisterMarker)
                }
                Text(
                    text = "]",
                    fontSize = (sizeVal * 1.1f).sp,
                    fontWeight = FontWeight.Light,
                    color = textColor
                )
            }
        }
        is MathNode.Row -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                node.children.forEach { child ->
                    RenderMathNode(child, fontSize, textColor, rootCoordinates, onRegisterMarker)
                }
            }
        }
        is MathNode.Integral -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 2.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "∫",
                        fontSize = (sizeVal * 1.6f).sp,
                        fontWeight = FontWeight.Light,
                        color = textColor
                    )
                }
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .height(38.dp)
                        .padding(start = 1.dp)
                ) {
                    Box(modifier = Modifier.offset(x = 1.dp, y = (-2).dp)) {
                        node.to?.let { RenderMathSlot(it, (sizeVal * 0.55f).sp, textColor, rootCoordinates, onRegisterMarker) }
                    }
                    Box(modifier = Modifier.offset(x = (-2).dp, y = 2.dp)) {
                        node.from?.let { RenderMathSlot(it, (sizeVal * 0.55f).sp, textColor, rootCoordinates, onRegisterMarker) }
                    }
                }
                RenderMathSlot(node.body, fontSize, textColor, rootCoordinates, onRegisterMarker)
            }
        }
        is MathNode.Sum -> {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Box(modifier = Modifier.padding(bottom = 1.dp)) {
                        node.to?.let { RenderMathSlot(it, (sizeVal * 0.55f).sp, textColor, rootCoordinates, onRegisterMarker) }
                    }
                    Text(
                        text = "∑",
                        fontSize = (sizeVal * 1.5f).sp,
                        fontWeight = FontWeight.Normal,
                        color = textColor
                    )
                    Box(modifier = Modifier.padding(top = 1.dp)) {
                        node.from?.let { RenderMathSlot(it, (sizeVal * 0.55f).sp, textColor, rootCoordinates, onRegisterMarker) }
                    }
                }
                RenderMathSlot(node.body, fontSize, textColor, rootCoordinates, onRegisterMarker)
            }
        }
        is MathNode.Limit -> {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "lim",
                        fontSize = fontSize,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Italic,
                        color = textColor
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        node.variable?.let { RenderMathSlot(it, (sizeVal * 0.55f).sp, textColor, rootCoordinates, onRegisterMarker) }
                        Text(
                            text = "→",
                            fontSize = (sizeVal * 0.55f).sp,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 1.dp)
                        )
                        RenderMathSlot(node.approach ?: MathNode.Text("0"), (sizeVal * 0.55f).sp, textColor, rootCoordinates, onRegisterMarker)
                    }
                }
                RenderMathSlot(node.body, fontSize, textColor, rootCoordinates, onRegisterMarker)
            }
        }
        is MathNode.Matrix -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                val leftModifier = if (node.type == "pmatrix") {
                    Modifier
                        .width(10.dp)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                        .drawBehind {
                            val w = size.width
                            val h = size.height
                            val sw = 1.5.dp.toPx()
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(w, 0f)
                                quadraticTo(0f, h / 2f, w, h)
                            }
                            drawPath(
                                path = path,
                                color = textColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw)
                            )
                        }
                } else if (node.type == "bmatrix") {
                    Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                        .drawBehind {
                            val w = size.width
                            val h = size.height
                            val sw = 1.5.dp.toPx()
                            drawLine(color = textColor, start = Offset(w, 0f), end = Offset(0f, 0f), strokeWidth = sw)
                            drawLine(color = textColor, start = Offset(0f, 0f), end = Offset(0f, h), strokeWidth = sw)
                            drawLine(color = textColor, start = Offset(0f, h), end = Offset(w, h), strokeWidth = sw)
                        }
                } else {
                    Modifier
                }

                if (node.type in listOf("pmatrix", "bmatrix")) {
                    Box(modifier = leftModifier)
                }

                val numRows = node.rows.size
                val numCols = if (numRows > 0) node.rows[0].size else 0

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    for (colIdx in 0 until numCols) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (rowIdx in 0 until numRows) {
                                val cell = node.rows[rowIdx].getOrNull(colIdx) ?: MathNode.Text("")
                                RenderMathSlot(cell, fontSize, textColor, rootCoordinates, onRegisterMarker)
                            }
                        }
                    }
                }

                val rightModifier = if (node.type == "pmatrix") {
                    Modifier
                        .width(10.dp)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                        .drawBehind {
                            val w = size.width
                            val h = size.height
                            val sw = 1.5.dp.toPx()
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(0f, 0f)
                                quadraticTo(w, h / 2f, 0f, h)
                            }
                            drawPath(
                                path = path,
                                color = textColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw)
                            )
                        }
                } else if (node.type == "bmatrix") {
                    Modifier
                        .width(8.dp)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                        .drawBehind {
                            val w = size.width
                            val h = size.height
                            val sw = 1.5.dp.toPx()
                            drawLine(color = textColor, start = Offset(0f, 0f), end = Offset(w, 0f), strokeWidth = sw)
                            drawLine(color = textColor, start = Offset(w, 0f), end = Offset(w, h), strokeWidth = sw)
                            drawLine(color = textColor, start = Offset(w, h), end = Offset(0f, h), strokeWidth = sw)
                        }
                } else {
                    Modifier
                }

                if (node.type in listOf("pmatrix", "bmatrix")) {
                    Box(modifier = rightModifier)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MixedMathText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    textColor: Color = LocalContentColor.current,
    lineHeight: TextUnit = 22.sp
) {
    if (text.isBlank()) return

    val normalized = text.replace("\\$", "$")
    val lines = normalized.split("\n")

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (line in lines) {
            if (line.isBlank()) continue

            if (line.contains("$")) {
                val parts = line.split("$")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    parts.forEachIndexed { index, part ->
                        if (part.isNotEmpty()) {
                            if (index % 2 == 1) {
                                MathRenderer(
                                    latex = part.trim(),
                                    fontSize = fontSize,
                                    textColor = textColor
                                )
                            } else {
                                Text(
                                    text = part,
                                    fontSize = fontSize,
                                    lineHeight = lineHeight,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            } else if (line.contains("\\") || line.contains("^") || line.contains("_") || line.contains("=") || line.contains("frac")) {
                val colonIdx = line.indexOfAny(charArrayOf(':', '：'))
                if (colonIdx > 0 && colonIdx < line.length - 1 && line.substring(0, colonIdx).any { it in '\u4e00'..'\u9fa5' }) {
                    val label = line.substring(0, colonIdx + 1)
                    val mathPart = line.substring(colonIdx + 1).trim()
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            color = textColor
                        )
                        if (mathPart.isNotEmpty()) {
                            MathRenderer(
                                latex = mathPart,
                                fontSize = fontSize,
                                textColor = textColor
                            )
                        }
                    }
                } else if (!line.any { it in '\u4e00'..'\u9fa5' }) {
                    MathRenderer(
                        latex = line.trim(),
                        fontSize = fontSize,
                        textColor = textColor
                    )
                } else {
                    Text(
                        text = line,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        color = textColor
                    )
                }
            } else {
                Text(
                    text = line,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    color = textColor
                )
            }
        }
    }
}
