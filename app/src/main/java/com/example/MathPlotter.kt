package com.example

import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

private fun cleanExpressionForPlotting(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    if (trimmed.contains("=")) {
        val parts = trimmed.split("=")
        if (parts.size == 2) {
            val left = parts[0].trim()
            val right = parts[1].trim()
            val leftLower = left.lowercase(Locale.US)
            val rightLower = right.lowercase(Locale.US)
            if (leftLower in listOf("y", "f(x)", "g(x)", "y(x)", "f", "g")) {
                return right
            }
            if (rightLower in listOf("y", "f(x)", "g(x)", "y(x)", "f", "g")) {
                return left
            }
            return "($left) - ($right)"
        }
    }
    return trimmed
}

@Composable
fun MathPlotter(
    latexExpression: String,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    axisColor: Color = MaterialTheme.colorScheme.onSurface
) {
    var panX by remember { mutableStateOf(0f) } // Center X in math units
    var panY by remember { mutableStateOf(0f) } // Center Y in math units
    var zoomFactor by remember { mutableStateOf(1f) } // Scale multiplier

    val textMeasurer = rememberTextMeasurer()

    // Base scale is 50 pixels per 1 math unit at zoomFactor = 1
    val baseScale = 50f
    val pixelsPerUnitX = baseScale * zoomFactor
    val pixelsPerUnitY = baseScale * zoomFactor

    // Clean expression for plotting
    val cleanLaTeX = remember(latexExpression) {
        cleanExpressionForPlotting(latexExpression)
    }

    // Try to parse expression
    val expr = remember(cleanLaTeX) {
        try {
            val node = MathParser.parse(cleanLaTeX)
            MathSolver.nodeToExpr(node)
        } catch (e: Exception) {
            null
        }
    }

    val canPlot = remember(expr) {
        if (expr == null) false
        else {
            val vars = expr.getVariables()
            vars.all { it in setOf("x", "e", "pi", "π") }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomFactor = (zoomFactor * zoom).coerceIn(0.15f, 25f)
                        panX -= pan.x / pixelsPerUnitX
                        panY += pan.y / pixelsPerUnitY
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val halfW = width / 2f
            val halfH = height / 2f

            // Math to pixel conversion
            fun mathToPixel(mx: Float, my: Float): Offset {
                val px = halfW + (mx - panX) * pixelsPerUnitX
                val py = halfH - (my - panY) * pixelsPerUnitY
                return Offset(px, py)
            }

            // Pixel to math conversion
            fun pixelToMath(px: Float, py: Float): Offset {
                val mx = panX + (px - halfW) / pixelsPerUnitX
                val my = panY - (py - halfH) / pixelsPerUnitY
                return Offset(mx, my)
            }

            // Draw grid lines
            // Dynamic grid spacing depending on zoom
            val gridSpacingMath = when {
                zoomFactor > 10f -> 0.1f
                zoomFactor > 4f -> 0.2f
                zoomFactor > 1.5f -> 0.5f
                zoomFactor > 0.6f -> 1.0f
                zoomFactor > 0.25f -> 2.0f
                zoomFactor > 0.1f -> 5.0f
                else -> 10.0f
            }

            val leftMath = pixelToMath(0f, 0f).x
            val rightMath = pixelToMath(width, 0f).x
            val topMath = pixelToMath(0f, 0f).y
            val bottomMath = pixelToMath(0f, height).y

            val startGridX = (floor(leftMath / gridSpacingMath) * gridSpacingMath).toFloat()
            val endGridX = (ceil(rightMath / gridSpacingMath) * gridSpacingMath).toFloat()
            val startGridY = (floor(bottomMath / gridSpacingMath) * gridSpacingMath).toFloat()
            val endGridY = (ceil(topMath / gridSpacingMath) * gridSpacingMath).toFloat()

            // Draw vertical grid lines & labels
            var xGrid = startGridX
            while (xGrid <= endGridX) {
                val pt = mathToPixel(xGrid, 0f)
                // Grid line
                drawLine(
                    color = gridColor,
                    start = Offset(pt.x, 0f),
                    end = Offset(pt.x, height),
                    strokeWidth = 0.8.dp.toPx()
                )

                // Label on X-axis (if axes are visible, else on bottom)
                val labelYPixel = if (mathToPixel(0f, 0f).y in 20f..(height - 30f)) {
                    mathToPixel(0f, 0f).y + 6f
                } else {
                    height - 30f
                }

                if (abs(xGrid) > 1e-5f) {
                    val labelText = if (gridSpacingMath < 1.0) String.format(Locale.US, "%.1f", xGrid) else xGrid.toInt().toString()
                    val textX = pt.x + 4f
                    if (textX in 0f..(width - 10f) && labelYPixel in 0f..(height - 10f)) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = labelText,
                            topLeft = Offset(textX, labelYPixel),
                            style = TextStyle(
                                color = axisColor.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
                xGrid += gridSpacingMath.toFloat()
            }

            // Draw horizontal grid lines & labels
            var yGrid = startGridY
            while (yGrid <= endGridY) {
                val pt = mathToPixel(0f, yGrid)
                // Grid line
                drawLine(
                    color = gridColor,
                    start = Offset(0f, pt.y),
                    end = Offset(width, pt.y),
                    strokeWidth = 0.8.dp.toPx()
                )

                // Label on Y-axis
                val labelXPixel = if (mathToPixel(0f, 0f).x in 10f..(width - 50f)) {
                    mathToPixel(0f, 0f).x + 6f
                } else {
                    10f
                }

                if (abs(yGrid) > 1e-5f) {
                    val labelText = if (gridSpacingMath < 1.0) String.format(Locale.US, "%.1f", yGrid) else yGrid.toInt().toString()
                    val textY = pt.y - 18f
                    if (labelXPixel in 0f..(width - 10f) && textY in 0f..(height - 10f)) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = labelText,
                            topLeft = Offset(labelXPixel, textY),
                            style = TextStyle(
                                color = axisColor.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
                yGrid += gridSpacingMath.toFloat()
            }

            // Draw main axes
            val originPixel = mathToPixel(0f, 0f)
            // X-axis
            if (originPixel.y in 0f..height) {
                drawLine(
                    color = axisColor,
                    start = Offset(0f, originPixel.y),
                    end = Offset(width, originPixel.y),
                    strokeWidth = 1.8.dp.toPx()
                )
            }
            // Y-axis
            if (originPixel.x in 0f..width) {
                drawLine(
                    color = axisColor,
                    start = Offset(originPixel.x, 0f),
                    end = Offset(originPixel.x, height),
                    strokeWidth = 1.8.dp.toPx()
                )
            }

            // Label origin
            val originTextX = originPixel.x + 6f
            val originTextY = originPixel.y + 4f
            if (originTextX in 0f..(width - 10f) && originTextY in 0f..(height - 10f)) {
                drawText(
                    textMeasurer = textMeasurer,
                    text = "0",
                    topLeft = Offset(originTextX, originTextY),
                    style = TextStyle(
                        color = axisColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            // Plot the function: y = f(x)
            if (canPlot && expr != null) {
                val path = Path()
                var first = true
                var lastPtY = 0f

                val stepPx = 1.5f
                var px = 0f
                while (px <= width) {
                    val mx = pixelToMath(px, 0f).x
                    val my = try {
                        val env = mapOf("x" to mx.toDouble())
                        expr.eval(env).toFloat()
                    } catch (e: Exception) {
                        Float.NaN
                    }

                    if (!my.isNaN() && !my.isInfinite() && abs(my) < 2000f) {
                        val pt = mathToPixel(mx, my)
                        if (pt.x.isFinite() && pt.y.isFinite()) {
                            // Check for asymptote gap (sudden vertical jump across screen height)
                            val isAsymptote = !first && abs(pt.y - lastPtY) > height * 1.5f && (pt.y * lastPtY < 0 || abs(pt.y) > height * 2f)
                            if (isAsymptote) {
                                first = true
                            }

                            if (first) {
                                path.moveTo(pt.x, pt.y)
                                first = false
                            } else {
                                path.lineTo(pt.x, pt.y)
                            }
                            lastPtY = pt.y
                        } else {
                            first = true
                        }
                    } else {
                        first = true
                    }
                    px += stepPx
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Overlay control buttons for quick actions (zoom in, zoom out, reset center)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledIconButton(
                onClick = {
                    panX = 0f
                    panY = 0f
                    zoomFactor = 1.0f
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset plot",
                    modifier = Modifier.size(18.dp)
                )
            }

            FilledIconButton(
                onClick = { zoomFactor = (zoomFactor * 1.5f).coerceAtMost(25f) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom in",
                    modifier = Modifier.size(18.dp)
                )
            }

            FilledIconButton(
                onClick = { zoomFactor = (zoomFactor / 1.5f).coerceAtLeast(0.15f) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        // Display function name
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val labelText = if (latexExpression.contains("=") || latexExpression.startsWith("y") || latexExpression.startsWith("f(")) {
                latexExpression
            } else {
                "y = $latexExpression"
            }
            Text(
                text = labelText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
