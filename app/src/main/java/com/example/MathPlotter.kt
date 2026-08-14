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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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

sealed class PlotMode {
    data class FunctionY(val expr: Expr) : PlotMode() // y = f(x)
    data class FunctionX(val expr: Expr) : PlotMode() // x = f(y)
    data class MultipleVerticalLines(val xValues: List<Double>) : PlotMode() // x = x_1, x = x_2
    data class MultipleHorizontalLines(val yValues: List<Double>) : PlotMode() // y = y_1, y = y_2
    data class ImplicitCurve(val diffExpr: Expr) : PlotMode() // g(x, y) = 0
    data class EquationTwoFunctions(
        val leftExpr: Expr,
        val rightExpr: Expr,
        val leftLabel: String,
        val rightLabel: String,
        val variableName: String = "x",
        val singleLineExpr: Expr? = null
    ) : PlotMode()
}

private fun replaceVarInExpr(expr: Expr, oldVar: String, newVar: String): Expr {
    return when (expr) {
        is Expr.Num -> expr
        is Expr.Const -> expr
        is Expr.Var -> if (expr.name == oldVar) Expr.Var(newVar) else expr
        is Expr.Add -> Expr.Add(replaceVarInExpr(expr.left, oldVar, newVar), replaceVarInExpr(expr.right, oldVar, newVar))
        is Expr.Sub -> Expr.Sub(replaceVarInExpr(expr.left, oldVar, newVar), replaceVarInExpr(expr.right, oldVar, newVar))
        is Expr.Mul -> Expr.Mul(replaceVarInExpr(expr.left, oldVar, newVar), replaceVarInExpr(expr.right, oldVar, newVar))
        is Expr.Div -> Expr.Div(replaceVarInExpr(expr.left, oldVar, newVar), replaceVarInExpr(expr.right, oldVar, newVar))
        is Expr.Neg -> Expr.Neg(replaceVarInExpr(expr.expr, oldVar, newVar))
        is Expr.Pow -> Expr.Pow(replaceVarInExpr(expr.base, oldVar, newVar), replaceVarInExpr(expr.exp, oldVar, newVar))
        is Expr.Sqrt -> Expr.Sqrt(replaceVarInExpr(expr.expr, oldVar, newVar))
        is Expr.Root -> Expr.Root(replaceVarInExpr(expr.index, oldVar, newVar), replaceVarInExpr(expr.expr, oldVar, newVar))
        is Expr.Fn -> Expr.Fn(expr.name, replaceVarInExpr(expr.arg, oldVar, newVar))
        is Expr.Mod -> Expr.Mod(replaceVarInExpr(expr.left, oldVar, newVar), replaceVarInExpr(expr.right, oldVar, newVar))
    }
}

private fun parsePlotMode(latexStr: String): PlotMode? {
    var trimmed = latexStr.trim()
    while (trimmed.endsWith("=")) {
        trimmed = trimmed.substring(0, trimmed.length - 1).trim()
    }
    if (trimmed.isEmpty()) return null

    try {
        if (trimmed.contains("=")) {
            val parts = trimmed.split("=")
            if (parts.size == 2) {
                val leftLaTeX = parts[0].trim()
                val rightLaTeX = parts[1].trim()

                val leftLower = leftLaTeX.lowercase(Locale.US)
                val rightLower = rightLaTeX.lowercase(Locale.US)

                val leftNode = try { MathParser.parse(leftLaTeX) } catch(e: Exception) { null }
                val rightNode = try { MathParser.parse(rightLaTeX) } catch(e: Exception) { null }

                val leftExpr = leftNode?.let { MathSolver.nodeToExpr(it) }
                val rightExpr = rightNode?.let { MathSolver.nodeToExpr(it) }

                if (leftExpr != null && rightExpr != null) {
                    // Check if y = f(x)
                    if ((leftLower in listOf("y", "f(x)", "g(x)", "y(x)", "f", "g")) &&
                        !rightExpr.getVariables().contains("y")) {
                        return PlotMode.FunctionY(rightExpr)
                    }
                    if ((rightLower in listOf("y", "f(x)", "g(x)", "y(x)", "f", "g")) &&
                        !leftExpr.getVariables().contains("y")) {
                        return PlotMode.FunctionY(leftExpr)
                    }

                    // Check if x = f(y)
                    if (leftLower == "x" && !rightExpr.getVariables().contains("x")) {
                        return PlotMode.FunctionX(rightExpr)
                    }
                    if (rightLower == "x" && !leftExpr.getVariables().contains("x")) {
                        return PlotMode.FunctionX(leftExpr)
                    }

                    val leftVars = leftExpr.getVariables().filter { it == "x" || it == "y" }.toSet()
                    val rightVars = rightExpr.getVariables().filter { it == "x" || it == "y" }.toSet()
                    val allVars = leftVars + rightVars

                    if (allVars == setOf("x")) {
                        val singleExpr = Expr.Sub(leftExpr, rightExpr)
                        return PlotMode.EquationTwoFunctions(
                            leftExpr = leftExpr,
                            rightExpr = rightExpr,
                            leftLabel = "y₁ = $leftLaTeX",
                            rightLabel = "y₂ = $rightLaTeX",
                            variableName = "x",
                            singleLineExpr = singleExpr
                        )
                    } else if (allVars == setOf("y")) {
                        val leftInX = replaceVarInExpr(leftExpr, "y", "x")
                        val rightInX = replaceVarInExpr(rightExpr, "y", "x")
                        val singleExpr = Expr.Sub(leftInX, rightInX)
                        return PlotMode.EquationTwoFunctions(
                            leftExpr = leftInX,
                            rightExpr = rightInX,
                            leftLabel = "y₁ = $leftLaTeX",
                            rightLabel = "y₂ = $rightLaTeX",
                            variableName = "y",
                            singleLineExpr = singleExpr
                        )
                    }

                    // General diff expression for multivariable or standard equations
                    val diffExpr = Expr.Sub(leftExpr, rightExpr)
                    val vars = diffExpr.getVariables().filter { it == "x" || it == "y" }.toSet()

                    if (vars.isEmpty()) return null

                    // Linear simplification check
                    val c = diffExpr.eval(mapOf("x" to 0.0, "y" to 0.0))
                    val f10 = diffExpr.eval(mapOf("x" to 1.0, "y" to 0.0))
                    val a = f10 - c
                    val f01 = diffExpr.eval(mapOf("x" to 0.0, "y" to 1.0))
                    val b = f01 - c

                    val f11 = diffExpr.eval(mapOf("x" to 1.0, "y" to 1.0))
                    val fn12 = diffExpr.eval(mapOf("x" to -1.0, "y" to 2.0))

                    val isLinear = abs(f11 - (a + b + c)) < 1e-6 && abs(fn12 - (-a + 2.0 * b + c)) < 1e-6

                    if (isLinear) {
                        if (abs(b) > 1e-9) {
                            val yExpr = Expr.Div(
                                Expr.Sub(Expr.Neg(Expr.Num(c)), Expr.Mul(Expr.Num(a), Expr.Var("x"))),
                                Expr.Num(b)
                            )
                            return PlotMode.FunctionY(yExpr)
                        } else if (abs(a) > 1e-9) {
                            val rootX = -c / a
                            return PlotMode.MultipleVerticalLines(listOf(rootX))
                        }
                    }

                    return PlotMode.ImplicitCurve(diffExpr)
                }
            }
        }

        // Expression without '='
        val node = MathParser.parse(trimmed)
        val expr = MathSolver.nodeToExpr(node) ?: return null
        val vars = expr.getVariables().filter { it == "x" || it == "y" }.toSet()

        return if (vars == setOf("y")) {
            PlotMode.FunctionX(expr)
        } else if (vars.contains("x") && vars.contains("y")) {
            PlotMode.ImplicitCurve(expr)
        } else {
            PlotMode.FunctionY(expr)
        }
    } catch (e: Exception) {
        return null
    }
}

private fun findIntersections(
    f: Expr,
    g: Expr,
    minX: Double,
    maxX: Double
): List<Offset> {
    val results = mutableListOf<Offset>()
    val steps = 500
    val dx = (maxX - minX) / steps

    fun diff(x: Double): Double {
        return try {
            f.eval(mapOf("x" to x)) - g.eval(mapOf("x" to x))
        } catch (e: Exception) {
            Double.NaN
        }
    }

    var prevX = minX
    var prevD = diff(prevX)

    for (i in 1..steps) {
        val currX = minX + i * dx
        val currD = diff(currX)

        if (!prevD.isNaN() && !currD.isNaN()) {
            if (prevD * currD <= 0) {
                var a = prevX
                var b = currX
                var fa = prevD
                var fb = currD
                var root = (a + b) / 2.0

                for (iter in 0..25) {
                    val mid = (a + b) / 2.0
                    val fmid = diff(mid)
                    if (fmid.isNaN() || abs(fmid) < 1e-8) {
                        root = mid
                        break
                    }
                    if (fa * fmid < 0) {
                        b = mid
                        fb = fmid
                    } else {
                        a = mid
                        fa = fmid
                    }
                    root = mid
                }

                val rootY = try { f.eval(mapOf("x" to root)) } catch (e: Exception) { Double.NaN }
                if (!rootY.isNaN() && !rootY.isInfinite()) {
                    val tol = (maxX - minX) / 150.0
                    if (results.none { abs(it.x - root.toFloat()) < tol }) {
                        results.add(Offset(root.toFloat(), rootY.toFloat()))
                    }
                }
            }
        }
        prevX = currX
        prevD = currD
    }
    return results
}

@Composable
fun MathPlotter(
    latexExpression: String,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outlineVariant,
    axisColor: Color = MaterialTheme.colorScheme.onSurface
) {
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var zoomFactor by remember { mutableStateOf(1f) }
    var showTwoFunctionsMode by remember { mutableStateOf(true) }

    val textMeasurer = rememberTextMeasurer()

    val baseScale = 50f
    val pixelsPerUnitX = baseScale * zoomFactor
    val pixelsPerUnitY = baseScale * zoomFactor

    val plotMode = remember(latexExpression) {
        parsePlotMode(latexExpression)
    }

    val colorLeft = MaterialTheme.colorScheme.primary // Blue/Indigo
    val colorRight = Color(0xFFE91E63) // Pink/Coral
    val colorIntersect = Color(0xFFFF9800) // Orange Gold

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
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

            fun mathToPixel(mx: Float, my: Float): Offset {
                val px = halfW + (mx - panX) * pixelsPerUnitX
                val py = halfH - (my - panY) * pixelsPerUnitY
                return Offset(px, py)
            }

            fun pixelToMath(px: Float, py: Float): Offset {
                val mx = panX + (px - halfW) / pixelsPerUnitX
                val my = panY - (py - halfH) / pixelsPerUnitY
                return Offset(mx, my)
            }

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
                drawLine(
                    color = gridColor,
                    start = Offset(pt.x, 0f),
                    end = Offset(pt.x, height),
                    strokeWidth = 0.8.dp.toPx()
                )

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
                drawLine(
                    color = gridColor,
                    start = Offset(0f, pt.y),
                    end = Offset(width, pt.y),
                    strokeWidth = 0.8.dp.toPx()
                )

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
            if (originPixel.y in 0f..height) {
                drawLine(
                    color = axisColor,
                    start = Offset(0f, originPixel.y),
                    end = Offset(width, originPixel.y),
                    strokeWidth = 1.8.dp.toPx()
                )
            }
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

            // Helper to draw single function y = expr(x)
            fun drawSingleFunction(expr: Expr, drawColor: Color, strokeWidthDp: Float = 3f) {
                val path = Path()
                var first = true
                var lastPtY = 0f
                val stepPx = 1.5f
                var px = 0f
                while (px <= width) {
                    val mx = pixelToMath(px, 0f).x
                    val my = try {
                        expr.eval(mapOf("x" to mx.toDouble())).toFloat()
                    } catch (e: Exception) {
                        Float.NaN
                    }

                    if (!my.isNaN() && !my.isInfinite() && abs(my) < 2000f) {
                        val pt = mathToPixel(mx, my)
                        if (pt.x.isFinite() && pt.y.isFinite()) {
                            val isAsymptote = !first && abs(pt.y - lastPtY) > height * 1.5f && (pt.y * lastPtY < 0 || abs(pt.y) > height * 2f)
                            if (isAsymptote) first = true

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
                drawPath(path = path, color = drawColor, style = Stroke(width = strokeWidthDp.dp.toPx()))
            }

            when (plotMode) {
                is PlotMode.EquationTwoFunctions -> {
                    if (showTwoFunctionsMode) {
                        // 1. Draw Left Function
                        drawSingleFunction(plotMode.leftExpr, colorLeft, strokeWidthDp = 3f)

                        // 2. Draw Right Function
                        drawSingleFunction(plotMode.rightExpr, colorRight, strokeWidthDp = 3f)

                        // 3. Find Intersections & Draw Guideline Projections
                        val intersections = findIntersections(
                            plotMode.leftExpr,
                            plotMode.rightExpr,
                            leftMath.toDouble(),
                            rightMath.toDouble()
                        )

                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                        for (ptMath in intersections) {
                            val ptPixel = mathToPixel(ptMath.x, ptMath.y)

                            if (ptPixel.x in -20f..(width + 20f) && ptPixel.y in -20f..(height + 20f)) {
                                // Vertical projection to X axis
                                drawLine(
                                    color = colorIntersect,
                                    start = ptPixel,
                                    end = Offset(ptPixel.x, originPixel.y.coerceIn(0f, height)),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = dashEffect
                                )

                                // Horizontal projection to Y axis
                                drawLine(
                                    color = colorIntersect,
                                    start = ptPixel,
                                    end = Offset(originPixel.x.coerceIn(0f, width), ptPixel.y),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = dashEffect
                                )

                                // Draw outer ring
                                drawCircle(
                                    color = colorIntersect.copy(alpha = 0.3f),
                                    radius = 10.dp.toPx(),
                                    center = ptPixel
                                )

                                // Draw solid intersection dot
                                drawCircle(
                                    color = colorIntersect,
                                    radius = 5.dp.toPx(),
                                    center = ptPixel
                                )

                                // Text callout for intersection
                                val varName = plotMode.variableName
                                val formatX = if (abs(ptMath.x - ptMath.x.toInt()) < 1e-4) ptMath.x.toInt().toString() else String.format(Locale.US, "%.2f", ptMath.x)
                                val formatY = if (abs(ptMath.y - ptMath.y.toInt()) < 1e-4) ptMath.y.toInt().toString() else String.format(Locale.US, "%.2f", ptMath.y)

                                val labelStr = "交点 ($formatX, $formatY) ⇒ $varName = $formatX"
                                val textOffset = Offset(
                                    (ptPixel.x + 12f).coerceAtMost(width - 160f),
                                    (ptPixel.y - 24f).coerceAtLeast(10f)
                                )

                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = labelStr,
                                    topLeft = textOffset,
                                    style = TextStyle(
                                        color = colorIntersect,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    } else {
                        // Plot combined single curve
                        plotMode.singleLineExpr?.let { singleExpr ->
                            drawSingleFunction(singleExpr, lineColor, strokeWidthDp = 3f)
                        }
                    }
                }

                is PlotMode.FunctionY -> {
                    drawSingleFunction(plotMode.expr, lineColor, strokeWidthDp = 3f)
                }

                is PlotMode.FunctionX -> {
                    val expr = plotMode.expr
                    val path = Path()
                    var first = true
                    var lastPtX = 0f
                    val stepPx = 1.5f
                    var py = 0f
                    while (py <= height) {
                        val my = pixelToMath(0f, py).y
                        val mx = try {
                            expr.eval(mapOf("y" to my.toDouble())).toFloat()
                        } catch (e: Exception) {
                            Float.NaN
                        }

                        if (!mx.isNaN() && !mx.isInfinite() && abs(mx) < 2000f) {
                            val pt = mathToPixel(mx, my)
                            if (pt.x.isFinite() && pt.y.isFinite()) {
                                val isAsymptote = !first && abs(pt.x - lastPtX) > width * 1.5f && (pt.x * lastPtX < 0 || abs(pt.x) > width * 2f)
                                if (isAsymptote) first = true

                                if (first) {
                                    path.moveTo(pt.x, pt.y)
                                    first = false
                                } else {
                                    path.lineTo(pt.x, pt.y)
                                }
                                lastPtX = pt.x
                            } else {
                                first = true
                            }
                        } else {
                            first = true
                        }
                        py += stepPx
                    }
                    drawPath(path = path, color = lineColor, style = Stroke(width = 3.dp.toPx()))
                }

                is PlotMode.MultipleVerticalLines -> {
                    for (xVal in plotMode.xValues) {
                        val pt = mathToPixel(xVal.toFloat(), 0f)
                        if (pt.x in -10f..(width + 10f)) {
                            drawLine(
                                color = lineColor,
                                start = Offset(pt.x, 0f),
                                end = Offset(pt.x, height),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }
                }

                is PlotMode.MultipleHorizontalLines -> {
                    for (yVal in plotMode.yValues) {
                        val pt = mathToPixel(0f, yVal.toFloat())
                        if (pt.y in -10f..(height + 10f)) {
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, pt.y),
                                end = Offset(width, pt.y),
                                strokeWidth = 3.dp.toPx()
                            )
                        }
                    }
                }

                is PlotMode.ImplicitCurve -> {
                    val diffExpr = plotMode.diffExpr
                    val step = 4f
                    val cols = (width / step).toInt() + 1
                    val rows = (height / step).toInt() + 1

                    val grid = Array(cols + 1) { FloatArray(rows + 1) }
                    for (c in 0..cols) {
                        val px = c * step
                        for (r in 0..rows) {
                            val py = r * step
                            val mathPt = pixelToMath(px, py)
                            val v = try {
                                diffExpr.eval(mapOf("x" to mathPt.x.toDouble(), "y" to mathPt.y.toDouble())).toFloat()
                            } catch (e: Exception) {
                                Float.NaN
                            }
                            grid[c][r] = v
                        }
                    }

                    val path = Path()
                    for (c in 0 until cols) {
                        val x0 = c * step
                        val x1 = (c + 1) * step
                        for (r in 0 until rows) {
                            val y0 = r * step
                            val y1 = (r + 1) * step

                            val v0 = grid[c][r]
                            val v1 = grid[c + 1][r]
                            val v2 = grid[c + 1][r + 1]
                            val v3 = grid[c][r + 1]

                            if (v0.isNaN() || v1.isNaN() || v2.isNaN() || v3.isNaN()) continue

                            val mask = (if (v0 > 0) 1 else 0) or
                                       (if (v1 > 0) 2 else 0) or
                                       (if (v2 > 0) 4 else 0) or
                                       (if (v3 > 0) 8 else 0)

                            if (mask == 0 || mask == 15) continue

                            fun edgePt(xA: Float, yA: Float, vA: Float, xB: Float, yB: Float, vB: Float): Offset {
                                val t = if (abs(vB - vA) < 1e-6f) 0.5f else (0f - vA) / (vB - vA)
                                val safeT = t.coerceIn(0f, 1f)
                                return Offset(xA + safeT * (xB - xA), yA + safeT * (yB - yA))
                            }

                            val top = edgePt(x0, y0, v0, x1, y0, v1)
                            val right = edgePt(x1, y0, v1, x1, y1, v2)
                            val bottom = edgePt(x0, y1, v3, x1, y1, v2)
                            val left = edgePt(x0, y0, v0, x0, y1, v3)

                            val segments = when (mask) {
                                1, 14 -> listOf(left, top)
                                2, 13 -> listOf(top, right)
                                3, 12 -> listOf(left, right)
                                4, 11 -> listOf(right, bottom)
                                5 -> listOf(left, top, right, bottom)
                                6, 9 -> listOf(top, bottom)
                                7, 8 -> listOf(left, bottom)
                                10 -> listOf(left, bottom, top, right)
                                else -> emptyList()
                            }

                            var idx = 0
                            while (idx + 1 < segments.size) {
                                path.moveTo(segments[idx].x, segments[idx].y)
                                path.lineTo(segments[idx + 1].x, segments[idx + 1].y)
                                idx += 2
                            }
                        }
                    }

                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
                null -> { /* Cannot plot */ }
            }
        }

        // Overlay control buttons (Zoom & Reset)
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

        // Header Legend & Toggle
        if (plotMode is PlotMode.EquationTwoFunctions) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { showTwoFunctionsMode = !showTwoFunctionsMode },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = if (showTwoFunctionsMode) "视角: 数形结合 (交点)" else "视角: 单曲线",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (showTwoFunctionsMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorLeft.copy(alpha = 0.15f),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = plotMode.leftLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorLeft,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colorRight.copy(alpha = 0.15f),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = plotMode.rightLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorRight,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Display standard function label
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
                val labelText = if (latexExpression.contains("=") || latexExpression.startsWith("y") || latexExpression.startsWith("x") || latexExpression.startsWith("f(")) {
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
}
