package com.example

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MathViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val database = MathDatabase.getDatabase(application)
    private val repository = HistoryRepository(database.historyDao())

    companion object {
        private const val KEY_INPUT_TEXT = "input_text"
        private const val KEY_INPUT_SELECTION_START = "input_selection_start"
        private const val KEY_INPUT_SELECTION_END = "input_selection_end"
        private const val KEY_IS_PLOT_ACTIVE = "is_plot_active"
        private const val KEY_PLOT_EXPRESSION = "plot_expression"

        private const val KEY_SOL_TYPE = "sol_type"
        private const val KEY_SOL_INPUT_LATEX = "sol_input_latex"
        private const val KEY_SOL_STEPS = "sol_steps"
        private const val KEY_SOL_EXACT_RESULT = "sol_exact_result"
        private const val KEY_SOL_DECIMAL_RESULT = "sol_decimal_result"
        private const val KEY_SOL_ROOT_X_VALUES = "sol_root_x_values"
        private const val KEY_SOL_GEOMETRIC = "sol_geometric"
    }

    var input by mutableStateOf(
        TextFieldValue(
            text = savedStateHandle.get<String>(KEY_INPUT_TEXT) ?: "",
            selection = TextRange(
                savedStateHandle.get<Int>(KEY_INPUT_SELECTION_START) ?: 0,
                savedStateHandle.get<Int>(KEY_INPUT_SELECTION_END) ?: 0
            )
        )
    )
        private set

    private val _solutionResult = MutableStateFlow<MathSolver.SolutionResult?>(
        run {
            val solType = savedStateHandle.get<String>(KEY_SOL_TYPE)
            if (solType != null) {
                MathSolver.SolutionResult(
                    type = solType,
                    inputLaTeX = savedStateHandle.get<String>(KEY_SOL_INPUT_LATEX) ?: "",
                    steps = savedStateHandle.get<Array<String>>(KEY_SOL_STEPS)?.toList() ?: emptyList(),
                    exactResultLaTeX = savedStateHandle.get<String>(KEY_SOL_EXACT_RESULT) ?: "",
                    decimalResult = savedStateHandle.get<String>(KEY_SOL_DECIMAL_RESULT) ?: "",
                    rootXValues = savedStateHandle.get<DoubleArray>(KEY_SOL_ROOT_X_VALUES)?.toList() ?: emptyList(),
                    geometricInterpretation = savedStateHandle.get<String>(KEY_SOL_GEOMETRIC)
                )
            } else {
                null
            }
        }
    )
    val solutionResult: StateFlow<MathSolver.SolutionResult?> = _solutionResult

    private fun saveSolutionResult(result: MathSolver.SolutionResult?) {
        _solutionResult.value = result
        if (result != null) {
            savedStateHandle[KEY_SOL_TYPE] = result.type
            savedStateHandle[KEY_SOL_INPUT_LATEX] = result.inputLaTeX
            savedStateHandle[KEY_SOL_STEPS] = result.steps.toTypedArray()
            savedStateHandle[KEY_SOL_EXACT_RESULT] = result.exactResultLaTeX
            savedStateHandle[KEY_SOL_DECIMAL_RESULT] = result.decimalResult
            savedStateHandle[KEY_SOL_ROOT_X_VALUES] = result.rootXValues.toDoubleArray()
            savedStateHandle[KEY_SOL_GEOMETRIC] = result.geometricInterpretation
        } else {
            savedStateHandle.remove<String>(KEY_SOL_TYPE)
            savedStateHandle.remove<String>(KEY_SOL_INPUT_LATEX)
            savedStateHandle.remove<Array<String>>(KEY_SOL_STEPS)
            savedStateHandle.remove<String>(KEY_SOL_EXACT_RESULT)
            savedStateHandle.remove<String>(KEY_SOL_DECIMAL_RESULT)
            savedStateHandle.remove<DoubleArray>(KEY_SOL_ROOT_X_VALUES)
            savedStateHandle.remove<String>(KEY_SOL_GEOMETRIC)
        }
    }

    var isPlotActive by mutableStateOf(savedStateHandle.get<Boolean>(KEY_IS_PLOT_ACTIVE) ?: false)
        private set

    var plotExpression by mutableStateOf(savedStateHandle.get<String>(KEY_PLOT_EXPRESSION) ?: "x^2")
        private set

    val historyList: StateFlow<List<HistoryItem>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateInput(newValue: TextFieldValue) {
        input = newValue
        savedStateHandle[KEY_INPUT_TEXT] = newValue.text
        savedStateHandle[KEY_INPUT_SELECTION_START] = newValue.selection.start
        savedStateHandle[KEY_INPUT_SELECTION_END] = newValue.selection.end
    }

    fun togglePlot() {
        isPlotActive = !isPlotActive
        savedStateHandle[KEY_IS_PLOT_ACTIVE] = isPlotActive
    }

    fun updatePlotExpression(expr: String) {
        plotExpression = expr
        savedStateHandle[KEY_PLOT_EXPRESSION] = expr
        isPlotActive = true
        savedStateHandle[KEY_IS_PLOT_ACTIVE] = true
    }

    fun solveCurrentInput() {
        val rawInput = input.text.trim()
        if (rawInput.isEmpty()) return

        viewModelScope.launch {
            try {
                val result = MathSolver.solve(rawInput)
                saveSolutionResult(result)

                // Save to history if we resolved a valid computation
                if (result.exactResultLaTeX != "\\text{Error}") {
                    val summaryResult = if (result.type == "equation") {
                        result.exactResultLaTeX
                    } else {
                        "= ${result.decimalResult}"
                    }
                    repository.insert(
                        HistoryItem(
                            expression = rawInput,
                            result = summaryResult,
                            type = result.type
                        )
                    )

                    // Auto-enable and prepare plotter if input has 'x' variable
                    if (rawInput.contains("x", ignoreCase = true)) {
                        val plotEq = if (rawInput.contains("=")) {
                            val parts = rawInput.split("=")
                            if (parts.size == 2) {
                                "(${parts[0]}) - (${parts[1]})"
                            } else {
                                rawInput
                            }
                        } else {
                            rawInput
                        }
                        plotExpression = plotEq
                        savedStateHandle[KEY_PLOT_EXPRESSION] = plotEq
                        isPlotActive = true
                        savedStateHandle[KEY_IS_PLOT_ACTIVE] = true
                    }
                }
            } catch (e: Throwable) {
                val errResult = MathSolver.SolutionResult(
                    type = "calculation",
                    inputLaTeX = rawInput,
                    steps = listOf("解析或求解出错: ${e.localizedMessage}"),
                    exactResultLaTeX = "\\text{Error}",
                    decimalResult = "Solver error"
                )
                saveSolutionResult(errResult)
            }
        }
    }

    fun selectHistoryItem(item: HistoryItem) {
        val newValue = TextFieldValue(
            text = item.expression,
            selection = TextRange(item.expression.length)
        )
        updateInput(newValue)
        solveCurrentInput()
    }

    fun deleteHistoryItem(item: HistoryItem) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}
