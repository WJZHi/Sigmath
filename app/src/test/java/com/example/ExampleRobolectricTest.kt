package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.test.runTest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `test MathViewModel crash with letters`() = runTest {
      val context = ApplicationProvider.getApplicationContext<Application>()
      val savedStateHandle = SavedStateHandle()
      val viewModel = MathViewModel(context, savedStateHandle)
      
      viewModel.updateInput(TextFieldValue("x"))
      viewModel.solveCurrentInput()
      
      // Wait for coroutine
      kotlinx.coroutines.delay(500)
      
      println("Result: ${viewModel.solutionResult.value?.exactResultLaTeX}")
      
      // Simulate save state
      val provider = savedStateHandle.savedStateProvider()
      val bundle = provider.saveState()
      
      // Create new bundle via parcel to see if it crashes
      val parcel = android.os.Parcel.obtain()
      bundle.writeToParcel(parcel, 0)
      parcel.setDataPosition(0)
      val newBundle = android.os.Bundle.CREATOR.createFromParcel(parcel)
      parcel.recycle()
      
      val restoredHandle = SavedStateHandle.createHandle(newBundle, null)
      val newViewModel = MathViewModel(context, restoredHandle)
      println("Restored Result: ${newViewModel.solutionResult.value?.exactResultLaTeX}")
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Math Solver", appName)
  }

  @Test
  fun testPlotExpressions() {
    val plotExamples = listOf(
        "(x^2 - 5x + 6) - (0)",
        "(3x + 4) - (19)",
        "\\sin(x)"
    )
    for (exprStr in plotExamples) {
        println("--- Testing Plot Expression: $exprStr ---")
        try {
            val node = MathParser.parse(exprStr)
            val expr = MathSolver.nodeToExpr(node)
            // It is okay if some don't evaluate, but they shouldn't crash
            if (expr != null) {
                println("Parsed Expr: $expr")
                for (x in -10..10) {
                    val env = mapOf("x" to x.toDouble())
                    val y = expr.eval(env)
                    println("x = $x -> y = $y")
                }
            } else {
                println("Expr was null")
            }
        } catch (t: Throwable) {
            println("FAILURE for plot expression $exprStr:")
            t.printStackTrace(System.out)
            throw t
        }
    }
  }

  @Test
  fun testBentoEquations() {
    val examples = listOf(
        "x^2 - 5x + 6 = 0",
        "3x + 4 = 19",
        "\\sin(x)",
        "\\frac{3}{4} + \\frac{2}{5}",
        "\\sqrt{18} - \\sqrt{8}",
        "a",
        "y = x^2",
        "sin(30)",
        "sin(x)",
        "2a = 4",
        "2x + y = 4",
        "y",
        "x",
        "sin",
        "cos",
        "tan"
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




