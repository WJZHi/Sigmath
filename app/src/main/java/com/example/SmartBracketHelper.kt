package com.example

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 智能括号输入算法辅助工具 (Smart Bracket Helper)
 *
 * 核心目标：
 * 用户只需要一个【( )】按钮，程序自动判断上下文，智能插入左括号 '(' 或右括号 ')'；
 * 同时维护括号层级，防止非法表达式（右括号多于左括号、不合理位置插入括号）。
 */
object SmartBracketHelper {

    /**
     * 根据当前算式与光标位置，计算应插入 '(' 还是 ')'，并返回更新后的算式与光标索引。
     *
     * @param expr 当前算式字符串
     * @param cursor 光标所在索引
     * @return Pair<新算式字符串, 新光标位置>
     */
    fun onBracketButtonClick(expr: String, cursor: Int): Pair<String, Int> {
        val safeCursor = cursor.coerceIn(0, expr.length)

        // 1. 统计光标左侧未闭合左括号数量 (openCount)
        var openCount = 0
        for (i in 0 until safeCursor) {
            val c = expr[i]
            if (c == '(') {
                openCount++
            } else if (c == ')') {
                if (openCount > 0) openCount--
            }
        }

        // 2. 获取光标左侧有效子串与末尾字符 (忽略末尾空格)
        val leftSub = expr.substring(0, safeCursor).trimEnd()
        val leftChar = if (leftSub.isNotEmpty()) leftSub.last() else null

        // 字符与上下文分类判断
        val opChars = setOf(
            '+', '-', '×', '÷', '*', '/', '^', '=', '<', '>',
            '±', '∓', '≤', '≥', '≠', '≈', ',', ':', ';', '!', '∫', '∑'
        )
        val isStart = leftChar == null
        val isAfterOperator = leftChar != null && (leftChar in opChars)
        val isAfterLeftBracket = leftChar == '(' || leftChar == '[' || leftChar == '{'
        val isAfterRightBracket = leftChar == ')' || leftChar == ']' || leftChar == '}'

        // 数字、常数、变量、单位
        val isAfterNumberOrConstant = leftChar != null && (
            leftChar.isDigit() ||
            leftChar == 'π' ||
            leftChar == 'e' ||
            leftChar == 'i' ||
            leftChar == '%' ||
            leftChar == '°' ||
            leftChar.isLetter()
        )

        // 函数名末尾识别（如 \sin, \cos, \tan, \sqrt, sqrt, abs, log, ln, max, min, gcf 等）
        val endsWithFuncCommand = Regex("""(\\[a-zA-Z]+|sin|cos|tan|sqrt|abs|log|ln|max|min|gcf)$""").containsMatchIn(leftSub) &&
                !leftSub.endsWith("\\pi") && !leftSub.endsWith("\\alpha") && !leftSub.endsWith("\\beta") &&
                !leftSub.endsWith("\\gamma") && !leftSub.endsWith("\\theta") && !leftSub.endsWith("\\infty")

        val insertChar: Char = when {
            // 场景 A：适合开启新括号（算式开头、运算符后、左括号后、函数名后）
            isStart || isAfterOperator || isAfterLeftBracket || endsWithFuncCommand -> '('

            // 场景 B：数字/常数/变量/右括号后，且存在未闭合的左括号 -> 闭合右括号
            (isAfterNumberOrConstant || isAfterRightBracket) && openCount > 0 -> ')'

            // 场景 C：数字/常数/变量后，但无待闭合括号 -> 强制开启新左括号（防非法孤立右括号）
            else -> '('
        }

        // 插入字符，光标跟随移动到新插入字符之后
        val before = expr.substring(0, safeCursor)
        val after = expr.substring(safeCursor)
        val newExpr = before + insertChar + after
        val newCursor = safeCursor + 1

        return Pair(newExpr, newCursor)
    }

    /**
     * 处理 TextFieldValue 的括号输入，支持选中文本自动包裹括号 (selectedText)。
     */
    fun processBracketInput(fieldValue: TextFieldValue): TextFieldValue {
        val text = fieldValue.text
        val selection = fieldValue.selection
        val selStart = selection.start.coerceIn(0, text.length)
        val selEnd = selection.end.coerceIn(0, text.length)
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)

        // 若用户框选了部分文本，直接使用括号包裹选中文本：(selected)
        if (start != end) {
            val selectedText = text.substring(start, end)
            val wrappedText = "($selectedText)"
            val newText = text.substring(0, start) + wrappedText + text.substring(end)
            val newCursor = start + wrappedText.length
            return TextFieldValue(
                text = newText,
                selection = TextRange(newCursor, newCursor)
            )
        }

        val (newText, newCursor) = onBracketButtonClick(text, start)
        return TextFieldValue(
            text = newText,
            selection = TextRange(newCursor, newCursor)
        )
    }
}
