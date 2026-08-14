package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

enum class MathTextClassification {
    PURE_MATH,               // 纯净合法的数学算式/方程
    NOISY_OR_INVALID_MATH,   // 含有算式但伴有干扰文字/乱码，或语法需调整
    NON_MATH                 // 纯文字或完全非数学内容
}

object SmartMathClipboard {

    private val LATEX_MATH_COMMANDS = setOf(
        "frac", "sqrt", "cbrt", "root", "sin", "cos", "tan", "asin", "acos", "atan",
        "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "log", "ln", "exp",
        "pi", "alpha", "beta", "gamma", "delta", "theta", "lambda", "mu", "sigma", "omega",
        "phi", "psi", "int", "sum", "prod", "lim", "infty", "pm", "times", "div", "cdot",
        "neq", "le", "leq", "ge", "geq", "approx", "mp", "deg", "gcd", "lcm", "binom"
    )

    private val MATH_OPERATOR_CHARS = setOf(
        '+', '-', '*', '/', '=', '<', '>', '≤', '≥', '≠', '≈', '÷', '×', '±', '^', '%', '!', '√', 'π', '·'
    )

    /**
     * 清洗常见 LaTeX 包装符
     */
    fun sanitize(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("$$") && text.endsWith("$$") && text.length >= 4) {
            text = text.substring(2, text.length - 2).trim()
        } else if (text.startsWith("$") && text.endsWith("$") && text.length >= 2) {
            text = text.substring(1, text.length - 1).trim()
        } else if (text.startsWith("\\[") && text.endsWith("\\]") && text.length >= 4) {
            text = text.substring(2, text.length - 2).trim()
        } else if (text.startsWith("\\(") && text.endsWith("\\)") && text.length >= 4) {
            text = text.substring(2, text.length - 2).trim()
        }
        return text
    }

    /**
     * 判断是否为中文字符或中文全角标点
     */
    fun isCjkChar(c: Char): Boolean {
        val ub = Character.UnicodeBlock.of(c)
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
               ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
               ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
               ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
               ub == Character.UnicodeBlock.GENERAL_PUNCTUATION ||
               ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
    }

    /**
     * 智能提取文本中的候选数学算式
     */
    fun extractMathCandidate(raw: String): String {
        val sanitized = sanitize(raw)
        // 规范化常见全角符号
        val normalized = sanitized
            .replace('（', '(').replace('）', ')')
            .replace('【', '[').replace('】', ']')
            .replace('＋', '+').replace('－', '-')
            .replace('＝', '=').replace('×', '*').replace('÷', '/')

        // 匹配可能的数学子串
        val mathPattern = Regex("""[a-zA-Z0-9+\\/*=<>≤≥≠≈÷×±^%!_()\[\]{}\s.,-]+""")
        val matches = mathPattern.findAll(normalized)
            .map { it.value.trim() }
            .filter { candidate ->
                val hasDigit = candidate.any { it.isDigit() }
                val hasOp = candidate.any { it in MATH_OPERATOR_CHARS }
                val hasLatex = candidate.contains("\\")
                val hasVar = candidate.any { it in "xyzabctn" }
                (hasDigit || hasOp || hasLatex || hasVar) && candidate.length >= 1
            }
            .toList()

        return if (matches.isNotEmpty()) {
            matches.maxByOrNull { it.length } ?: sanitized
        } else {
            sanitized
        }
    }

    /**
     * 对剪贴板或传入文本进行分类判定
     */
    fun classify(raw: String): MathTextClassification {
        val text = sanitize(raw)
        if (text.isBlank()) return MathTextClassification.NON_MATH

        val hasCjk = text.any { isCjkChar(it) }
        val digitsCount = text.count { it.isDigit() }
        val mathOpsCount = text.count { it in MATH_OPERATOR_CHARS }
        val hasLatexCommand = text.contains("\\") && LATEX_MATH_COMMANDS.any { text.contains("\\$it") }

        // 如果既没有数字，也没有数学运算符，也没有 LaTeX 数学命令
        if (digitsCount == 0 && mathOpsCount == 0 && !hasLatexCommand) {
            // 单独一个常用数学字母变量 (如 x, y, a) 可作为算式
            val trimmed = text.trim()
            if (trimmed.length == 1 && trimmed[0].isLetter() && !hasCjk) {
                return MathTextClassification.PURE_MATH
            }
            return MathTextClassification.NON_MATH
        }

        // 如果含有汉字/中文全角字符，必定含有非算式干扰元素
        if (hasCjk) {
            return MathTextClassification.NOISY_OR_INVALID_MATH
        }

        // 检查是否含有长英文字词（非数学关键词）
        val words = text.split(Regex("[^a-zA-Z]+")).filter { it.isNotEmpty() }
        val knownMathWords = setOf(
            "sin", "cos", "tan", "asin", "acos", "atan", "arcsin", "arccos", "arctan",
            "sinh", "cosh", "tanh", "log", "ln", "exp", "sqrt", "cbrt", "root",
            "pi", "frac", "int", "sum", "prod", "lim", "mod", "gcd", "lcm", "deg", "binom",
            "x", "y", "z", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l",
            "m", "n", "p", "q", "r", "s", "t", "u", "v", "w", "alpha", "beta", "theta"
        )

        var nonMathWordsCount = 0
        for (word in words) {
            val lower = word.lowercase()
            if (lower !in knownMathWords && lower.length > 2) {
                nonMathWordsCount++
            }
        }

        if (nonMathWordsCount > 0) {
            return MathTextClassification.NOISY_OR_INVALID_MATH
        }

        // 检查括号匹配
        if (!isBracketsBalanced(text)) {
            return MathTextClassification.NOISY_OR_INVALID_MATH
        }

        // 尝试用 MathParser 进行词法检测
        return try {
            val tokens = MathParser.tokenize(text)
            if (tokens.isEmpty()) {
                MathTextClassification.NON_MATH
            } else {
                MathTextClassification.PURE_MATH
            }
        } catch (e: Exception) {
            MathTextClassification.NOISY_OR_INVALID_MATH
        }
    }

    private fun isBracketsBalanced(text: String): Boolean {
        var paren = 0
        var brace = 0
        var bracket = 0
        for (c in text) {
            when (c) {
                '(' -> paren++
                ')' -> { paren--; if (paren < 0) return false }
                '{' -> brace++
                '}' -> { brace--; if (brace < 0) return false }
                '[' -> bracket++
                ']' -> { bracket--; if (bracket < 0) return false }
            }
        }
        return paren == 0 && brace == 0 && bracket == 0
    }
}
