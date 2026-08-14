package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testBentoEquations() {
    val examples = listOf(
        "x^2 - 5x + 6 = 0",
        "3x + 4 = 19",
        "\\sin(x)",
        "\\frac{3}{4} + \\frac{2}{5}",
        "\\sqrt{18} - \\sqrt{8}"
    )
    for (eq in examples) {
        println("--- START TESTING: $eq ---")
        try {
            val result = MathSolver.solve(eq)
            println("SUCCESS for $eq: type=${result.type}, exact=${result.exactResultLaTeX}, decimal=${result.decimalResult}")
        } catch (t: Throwable) {
            println("FAILURE for $eq:")
            t.printStackTrace(System.out)
            throw t
        }
    }
  }

  @Test
  fun testSmartBracketHelper() {
    // 1. 开头，插入左括号
    assertEquals(Pair("(", 1), SmartBracketHelper.onBracketButtonClick("", 0))

    // 2. 运算符后，插入左括号
    assertEquals(Pair("5+(", 3), SmartBracketHelper.onBracketButtonClick("5+", 2))
    assertEquals(Pair("5 + (", 5), SmartBracketHelper.onBracketButtonClick("5 + ", 4))

    // 3. 数字后，存在未闭合括号 -> 闭合右括号
    assertEquals(Pair("(1+2)", 5), SmartBracketHelper.onBracketButtonClick("(1+2", 4))
    assertEquals(Pair("3*(5+2)", 7), SmartBracketHelper.onBracketButtonClick("3*(5+2", 6))

    // 4. 数字后，无待闭合括号 -> 新建左括号
    assertEquals(Pair("123(", 4), SmartBracketHelper.onBracketButtonClick("123", 3))

    // 5. 括号平衡，新建左括号
    assertEquals(Pair("(3+4)(", 6), SmartBracketHelper.onBracketButtonClick("(3+4)", 5))

    // 6. 左括号后方，继续左括号
    assertEquals(Pair("((", 2), SmartBracketHelper.onBracketButtonClick("(", 1))
    assertEquals(Pair("sqrt((", 6), SmartBracketHelper.onBracketButtonClick("sqrt(", 5))
  }

  @Test
  fun testSmartMathClipboardClassification() {
    // 1. Pure Math
    assertEquals(MathTextClassification.PURE_MATH, SmartMathClipboard.classify("x^2 + 2x - 3 = 0"))
    assertEquals(MathTextClassification.PURE_MATH, SmartMathClipboard.classify("\\frac{1}{2} + \\sqrt{3}"))
    assertEquals(MathTextClassification.PURE_MATH, SmartMathClipboard.classify("3 * (4 + 5) - 6 / 2"))
    assertEquals(MathTextClassification.PURE_MATH, SmartMathClipboard.classify("\\sin(x) + \\cos(y) = 1"))
    assertEquals(MathTextClassification.PURE_MATH, SmartMathClipboard.classify("$$123 + 456$$"))
    assertEquals(MathTextClassification.PURE_MATH, SmartMathClipboard.classify("x"))

    // 2. Non-Math (Plain Text / Natural language)
    assertEquals(MathTextClassification.NON_MATH, SmartMathClipboard.classify("今天天气真好，去公园散步"))
    assertEquals(MathTextClassification.NON_MATH, SmartMathClipboard.classify("hello world this is a test"))
    assertEquals(MathTextClassification.NON_MATH, SmartMathClipboard.classify("点击查看详情内容"))
    assertEquals(MathTextClassification.NON_MATH, SmartMathClipboard.classify(""))

    // 3. Noisy Math / Needs Editing
    assertEquals(MathTextClassification.NOISY_OR_INVALID_MATH, SmartMathClipboard.classify("已知 3x - 5 = 10，求x的值"))
    assertEquals(MathTextClassification.NOISY_OR_INVALID_MATH, SmartMathClipboard.classify("求解方程: y = 2x^2 + 3"))
    assertEquals(MathTextClassification.NOISY_OR_INVALID_MATH, SmartMathClipboard.classify("(1 + 2 * (3 + 4)")) // unbalanced paren

    // Test extraction
    val extracted = SmartMathClipboard.extractMathCandidate("已知 3x - 5 = 10，求x的值")
    assertTrue(extracted.contains("3x - 5 = 10"))
  }
}

