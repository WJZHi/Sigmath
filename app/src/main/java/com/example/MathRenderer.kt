package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import android.util.Log

@Composable
fun MathRenderer(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    textColor: Color = LocalContentColor.current
) {
    val node = androidx.compose.runtime.remember(latex) {
        try {
            MathParser.parse(latex)
        } catch (e: Throwable) {
            SafeLog.e("MathRenderer", "Failed to parse LaTeX: $latex", e)
            MathNode.Text(latex)
        }
    }
    Box(modifier = modifier) {
        RenderMathNode(node, fontSize, textColor)
    }
}

@Composable
fun RenderMathNode(
    node: MathNode,
    fontSize: TextUnit,
    textColor: Color
) {
    val sizeVal = fontSize.value

    when (node) {
        is MathNode.Text -> {
            Text(
                text = node.text,
                fontSize = fontSize,
                fontStyle = if (node.isItalic) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (node.isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                color = textColor
            )
        }
        is MathNode.Operator -> {
            Text(
                text = node.op,
                fontSize = fontSize,
                fontWeight = FontWeight.Normal,
                color = textColor,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        is MathNode.SpecialSymbol -> {
            Text(
                text = node.symbol,
                fontSize = fontSize,
                fontStyle = FontStyle.Normal,
                color = textColor
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
                    RenderMathNode(node.numerator, (sizeVal * 0.85f).sp, textColor)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(textColor)
                )
                Box(modifier = Modifier.padding(top = 2.dp)) {
                    RenderMathNode(node.denominator, (sizeVal * 0.85f).sp, textColor)
                }
            }
        }
        is MathNode.Sqrt -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(
                    text = "√",
                    fontSize = (sizeVal * 1.2f).sp,
                    fontWeight = FontWeight.Light,
                    color = textColor
                )
                Box(
                    modifier = Modifier
                        .drawBehind {
                            drawLine(
                                color = textColor,
                                start = Offset(0f, 2f),
                                end = Offset(size.width, 2f),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                        .padding(top = 4.dp, start = 1.dp, end = 2.dp)
                ) {
                    RenderMathNode(node.content, fontSize, textColor)
                }
            }
        }
        is MathNode.Power -> {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RenderMathNode(node.base, fontSize, textColor)
                Box(
                    modifier = Modifier
                        .align(Alignment.Top)
                        .offset(y = (-4).dp)
                ) {
                    RenderMathNode(node.exponent, (sizeVal * 0.65f).sp, textColor)
                }
            }
        }
        is MathNode.Subscript -> {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RenderMathNode(node.base, fontSize, textColor)
                Box(
                    modifier = Modifier
                        .align(Alignment.Bottom)
                        .offset(y = 2.dp)
                ) {
                    RenderMathNode(node.subscript, (sizeVal * 0.65f).sp, textColor)
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
                    RenderMathNode(node.content, fontSize, textColor)
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
                    RenderMathNode(node.content, fontSize, textColor)
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
                    RenderMathNode(child, fontSize, textColor)
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
                        node.to?.let { RenderMathNode(it, (sizeVal * 0.55f).sp, textColor) }
                    }
                    Box(modifier = Modifier.offset(x = (-2).dp, y = 2.dp)) {
                        node.from?.let { RenderMathNode(it, (sizeVal * 0.55f).sp, textColor) }
                    }
                }
            }
        }
        is MathNode.Sum -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Box(modifier = Modifier.padding(bottom = 1.dp)) {
                    node.to?.let { RenderMathNode(it, (sizeVal * 0.55f).sp, textColor) }
                }
                Text(
                    text = "∑",
                    fontSize = (sizeVal * 1.5f).sp,
                    fontWeight = FontWeight.Normal,
                    color = textColor
                )
                Box(modifier = Modifier.padding(top = 1.dp)) {
                    node.from?.let { RenderMathNode(it, (sizeVal * 0.55f).sp, textColor) }
                }
            }
        }
        is MathNode.Limit -> {
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
                    node.variable?.let { RenderMathNode(it, (sizeVal * 0.55f).sp, textColor) }
                    Text(
                        text = "→",
                        fontSize = (sizeVal * 0.55f).sp,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 1.dp)
                    )
                    RenderMathNode(node.approach ?: MathNode.Text("0"), (sizeVal * 0.55f).sp, textColor)
                }
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
                                RenderMathNode(cell, fontSize, textColor)
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
