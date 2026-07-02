package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class KeyboardPage {
    BASIC, ADVANCED, ALPHABET
}

@Composable
fun MathKeyboard(
    fieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSolve: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableStateOf(KeyboardPage.BASIC) }

    fun handleInsert(textToInsert: String, cursorShift: Int = 0) {
        val text = fieldValue.text
        val selection = fieldValue.selection
        val start = selection.start
        val end = selection.end

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
        val start = selection.start
        val end = selection.end

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
        val currentPos = selection.start
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
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (currentPage) {
            KeyboardPage.BASIC -> {
                BasicKeyboardLayout(
                    onKeyInsert = { txt, shift -> handleInsert(txt, shift) },
                    onBackspace = { handleBackspace() },
                    onMoveCursor = { dir -> handleMoveCursor(dir) },
                    onAC = { handleAC() },
                    onSolve = onSolve,
                    onSwitchPage = { currentPage = KeyboardPage.ADVANCED }
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

@Composable
fun KeyboardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
            .clickable { onClick() }
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

@Composable
fun ColumnScope.BasicKeyboardLayout(
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
        KeyboardButton("x", { onKeyInsert("x", 0) }, Modifier.weight(1f), containerColor = darkBg, isBold = true)
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

    // Display basic alphabet rows in QWERTY format
    val row1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    val row2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "z")
    val row3 = listOf("x", "y", "z", "α", "β", "γ", "π", "θ", "τ", "μ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        row1.forEach { letter ->
            KeyboardButton(letter, { onKeyInsert(letter, 0) }, Modifier.weight(1f), fontSize = 16)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        row2.forEach { letter ->
            KeyboardButton(letter, { onKeyInsert(letter, 0) }, Modifier.weight(1f), fontSize = 16)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        row3.forEach { letter ->
            KeyboardButton(
                text = letter,
                onClick = {
                    if (letter in listOf("α", "β", "γ", "π", "θ", "τ", "μ")) {
                        val command = when (letter) {
                            "α" -> "\\alpha"
                            "β" -> "\\beta"
                            "γ" -> "\\gamma"
                            "π" -> "\\pi"
                            "θ" -> "\\theta"
                            "τ" -> "\\tau"
                            "μ" -> "\\mu"
                            else -> letter
                        }
                        onKeyInsert(command, 0)
                    } else {
                        onKeyInsert(letter, 0)
                    }
                },
                modifier = Modifier.weight(1f),
                fontSize = 16
            )
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
