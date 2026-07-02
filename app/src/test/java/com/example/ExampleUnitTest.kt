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
}

