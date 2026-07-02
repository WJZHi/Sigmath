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

    // Base scale is 40 pixels per 1 math unit at zoomFactor = 1
    val baseScale = 50f
    val pixelsPerUnitX = baseScale * zoomFactor
    val pixelsPerUnitY = baseScale * zoomFactor

    // Try to parse expression
    val expr = remember(latexExpression) {
        try {
            val node = MathParser.parse(latexExpression)
            MathSolver.nodeToExpr(node)
        } catch (e: Exception) {
            null
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
                    drawText(
                        textMeasurer = textMeasurer,
                        text = labelText,
                        topLeft = Offset(pt.x + 4f, labelYPixel),
                        style = TextStyle(
                            color = axisColor.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
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
                    drawText(
                        textMeasurer = textMeasurer,
                        text = labelText,
                        topLeft = Offset(labelXPixel, pt.y - 18f),
                        style = TextStyle(
                            color = axisColor.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
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
            drawText(
                textMeasurer = textMeasurer,
                text = "0",
                topLeft = Offset(originPixel.x + 6f, originPixel.y + 4f),
                style = TextStyle(
                    color = axisColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )

            // Plot the function: y = f(x)
            if (expr != null) {
                // Pre-check to avoid throwing exceptions 500 times per frame if the expression contains undefined variables like 'y' or 'a'
                val canEvaluate = try {
                    val testEnv = mapOf("x" to 1.0)
                    expr.eval(testEnv)
                    true
                } catch (e: Exception) {
                    false
                }

                if (canEvaluate) {
                    val path = Path()
                    var first = true
    
                    // Calculate step size in pixels for a smooth curve
                    val stepPx = 2f
                    var px = 0f
                    while (px <= width) {
                        val mx = pixelToMath(px, 0f).x
                        val my = try {
                            val env = mapOf("x" to mx.toDouble())
                            expr.eval(env).toFloat()
                        } catch (e: Exception) {
                            Float.NaN
                        }
    
                        if (!my.isNaN() && !my.isInfinite() && abs(my) < 500f) {
                            val pt = mathToPixel(mx, my)
                            if (pt.x.isFinite() && pt.y.isFinite()) {
                                if (first) {
                                    path.moveTo(pt.x, pt.y)
                                    first = false
                                } else {
                                    path.lineTo(pt.x, pt.y)
                                }
                            } else {
                                first = true
                            }
                        } else {
                            // Discontinuity - split the path
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
                // Outlined minus equivalent
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
            Text(
                text = "y = $latexExpression",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
