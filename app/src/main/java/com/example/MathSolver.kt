package com.example

import android.util.Log
import java.util.*
import kotlin.math.*

sealed class Expr {
    data class Num(val value: Double) : Expr()
    data class Var(val name: String) : Expr()
    data class Add(val left: Expr, val right: Expr) : Expr()
    data class Sub(val left: Expr, val right: Expr) : Expr()
    data class Mul(val left: Expr, val right: Expr) : Expr()
    data class Div(val left: Expr, val right: Expr) : Expr()
    data class Pow(val base: Expr, val exp: Expr) : Expr()
    data class Sqrt(val expr: Expr) : Expr()
    data class Fn(val name: String, val arg: Expr) : Expr()
    data class Neg(val expr: Expr) : Expr()

    fun eval(env: Map<String, Double>): Double {
        return when (this) {
            is Num -> value
            is Var -> env[name] ?: throw IllegalArgumentException("Undefined variable: $name")
            is Add -> left.eval(env) + right.eval(env)
            is Sub -> left.eval(env) - right.eval(env)
            is Mul -> left.eval(env) * right.eval(env)
            is Div -> {
                val den = right.eval(env)
                if (abs(den) < 1e-15) throw ArithmeticException("Division by zero")
                left.eval(env) / den
            }
            is Pow -> {
                val b = base.eval(env)
                val e = exp.eval(env)
                b.pow(e)
            }
            is Sqrt -> {
                val v = expr.eval(env)
                if (v < 0) throw ArithmeticException("Square root of negative number")
                sqrt(v)
            }
            is Fn -> {
                val v = arg.eval(env)
                when (name) {
                    "sin" -> sin(v)
                    "cos" -> cos(v)
                    "tan" -> tan(v)
                    "asin", "arcsin" -> asin(v)
                    "acos", "arccos" -> acos(v)
                    "atan", "arctan" -> atan(v)
                    "log" -> log10(v)
                    "ln" -> ln(v)
                    "exp" -> exp(v)
                    "sinh" -> sinh(v)
                    "cosh" -> cosh(v)
                    "tanh" -> tanh(v)
                    "cot" -> 1.0 / tan(v)
                    "sec" -> 1.0 / cos(v)
                    "csc" -> 1.0 / sin(v)
                    "abs" -> abs(v)
                    "floor" -> floor(v)
                    "ceil" -> ceil(v)
                    "cuberoot" -> Math.cbrt(v)
                    "factorial" -> factorial(v)
                    else -> throw IllegalArgumentException("Unknown function: $name")
                }
            }
            is Neg -> -expr.eval(env)
        }
    }

    fun getVariables(): Set<String> {
        return when (this) {
            is Num -> emptySet()
            is Var -> setOf(name)
            is Add -> left.getVariables() + right.getVariables()
            is Sub -> left.getVariables() + right.getVariables()
            is Mul -> left.getVariables() + right.getVariables()
            is Div -> left.getVariables() + right.getVariables()
            is Pow -> base.getVariables() + exp.getVariables()
            is Sqrt -> expr.getVariables()
            is Fn -> arg.getVariables()
            is Neg -> expr.getVariables()
        }
    }

    private fun factorial(n: Double): Double {
        val integerPart = n.roundToInt()
        if (abs(n - integerPart) > 1e-9 || integerPart < 0) {
            throw IllegalArgumentException("Factorial is only defined for non-negative integers")
        }
        var res = 1.0
        for (i in 2..integerPart) {
            res *= i
        }
        return res
    }
}

data class SolverFraction(val num: Long, val den: Long) {
    fun simplify(): SolverFraction {
        val g = gcd(abs(num), abs(den))
        val n = num / g
        val d = den / g
        return if (d < 0) SolverFraction(-n, -d) else SolverFraction(n, d)
    }

    fun toLaTeX(): String {
        val simplified = simplify()
        if (simplified.den == 1L) return "${simplified.num}"
        return "\\frac{${simplified.num}}{${simplified.den}}"
    }

    override fun toString(): String = toLaTeX()
}

fun gcd(a: Long, b: Long): Long {
    var x = a
    var y = b
    while (y != 0L) {
        val t = y
        y = x % y
        x = t
    }
    return x
}

object MathSolver {
    private const val TAG = "MathSolver"

    fun doubleToFraction(value: Double, tolerance: Double = 1e-9): SolverFraction? {
        if (value.isNaN() || value.isInfinite() || abs(value) > 1e7) return null
        var h1 = 1L; var h2 = 0L; var k1 = 0L; var k2 = 1L
        var b = value
        var iterations = 0
        while (iterations < 50) {
            val a = floor(b).toLong()
            val auxH = h1; h1 = a * h1 + h2; h2 = auxH
            val auxK = k1; k1 = a * k1 + k2; k2 = auxK
            val diff = b - a
            if (abs(diff) < 1e-12) break
            b = 1.0 / diff
            if (abs(value - h1.toDouble() / k1.toDouble()) <= tolerance) break
            iterations++
        }
        val frac = SolverFraction(h1, k1).simplify()
        return if (abs(value - frac.num.toDouble() / frac.den.toDouble()) <= tolerance) frac else null
    }

    fun simplifySqrt(n: Long): Pair<Long, Long> {
        if (n < 0) return Pair(1L, n)
        var outside = 1L
        var inside = n
        var i = 2L
        while (i * i <= inside) {
            val sq = i * i
            if (inside % sq == 0L) {
                outside *= i
                inside /= sq
                i = 2L
            } else {
                i++
            }
        }
        return Pair(outside, inside)
    }

    private fun parseTextToExpr(txtRaw: String): Expr? {
        val txt = txtRaw.trim()
        if (txt.isEmpty()) return null
        val d = txt.toDoubleOrNull()
        if (d != null) return Expr.Num(d)
        if (txt == "pi" || txt == "π") return Expr.Num(PI)
        if (txt == "e") return Expr.Num(E)
        if (txt.length == 1 && txt[0].isLetter()) return Expr.Var(txt)
        if (txt.startsWith("-") && txt.length > 1) {
            val sub = parseTextToExpr(txt.substring(1))
            if (sub != null) return Expr.Neg(sub)
        }
        val numLetterMatch = Regex("^([0-9.]+)([a-zA-Z]+)$").find(txt)
        if (numLetterMatch != null) {
            val numVal = numLetterMatch.groupValues[1].toDoubleOrNull()
            val varStr = numLetterMatch.groupValues[2]
            if (numVal != null && varStr.isNotEmpty()) {
                val varExpr = if (varStr.length == 1) Expr.Var(varStr) else Expr.Var(varStr)
                return Expr.Mul(Expr.Num(numVal), varExpr)
            }
        }
        return Expr.Var(txt)
    }

    // Convert MathNode to Expr
    fun nodeToExpr(node: MathNode): Expr? {
        return try {
            when (node) {
                is MathNode.Text -> parseTextToExpr(node.text)
                is MathNode.Operator -> null // Operators are handled at Row-level parsing
                is MathNode.SpecialSymbol -> {
                    when (node.symbol) {
                        "π" -> Expr.Num(PI)
                        "e" -> Expr.Num(E)
                        "∞" -> Expr.Num(Double.POSITIVE_INFINITY)
                        else -> Expr.Var(node.symbol)
                    }
                }
                is MathNode.Fraction -> {
                    val num = nodeToExpr(node.numerator) ?: return null
                    val den = nodeToExpr(node.denominator) ?: return null
                    Expr.Div(num, den)
                }
                is MathNode.Sqrt -> {
                    val content = nodeToExpr(node.content) ?: return null
                    Expr.Sqrt(content)
                }
                is MathNode.Power -> {
                    val base = nodeToExpr(node.base) ?: return null
                    val exp = nodeToExpr(node.exponent) ?: return null
                    Expr.Pow(base, exp)
                }
                is MathNode.Subscript -> {
                    // Treat subscript as variable or base_sub
                    val baseExpr = nodeToExpr(node.base) ?: return null
                    val subExpr = nodeToExpr(node.subscript) ?: return null
                    if (baseExpr is Expr.Var && subExpr is Expr.Num) {
                        Expr.Var("${baseExpr.name}_${subExpr.value.toInt()}")
                    } else if (baseExpr is Expr.Var && subExpr is Expr.Var) {
                        Expr.Var("${baseExpr.name}_${subExpr.name}")
                    } else {
                        baseExpr
                    }
                }
                is MathNode.Parentheses -> nodeToExpr(node.content)
                is MathNode.SquareBrackets -> nodeToExpr(node.content)
                is MathNode.Row -> parseRowToExpr(node.children)
                is MathNode.Integral, is MathNode.Sum, is MathNode.Limit, is MathNode.Matrix -> {
                    // Not directly evaluable analytically easily in standard calc, but we can represent as Var or null
                    null
                }
            }
        } catch (e: Exception) {
            SafeLog.e(TAG, "Error converting node to Expr", e)
            null
        }
    }

    // Helper parser to handle rows of MathNodes with priority, parentheses, and implicit multiplication
    private fun parseRowToExpr(nodes: List<MathNode>): Expr? {
        val exprTokens = mutableListOf<Any>() // Can be Expr or Operator string
        var i = 0
        while (i < nodes.size) {
            val node = nodes[i]
            when (node) {
                is MathNode.Operator -> {
                    exprTokens.add(node.op)
                    i++
                }
                is MathNode.Text -> {
                    val txt = node.text.trim()
                    if (txt in listOf("sin", "cos", "tan", "arcsin", "arccos", "arctan", "asin", "acos", "atan", "log", "ln", "exp", "sinh", "cosh", "tanh", "cot", "sec", "csc", "abs", "floor", "ceil", "cuberoot")) {
                        // Function. Parse its argument
                        if (i + 1 < nodes.size) {
                            val argNode = nodes[i + 1]
                            val argExpr = nodeToExpr(argNode)
                            if (argExpr != null) {
                                exprTokens.add(Expr.Fn(txt, argExpr))
                                i += 2
                            } else {
                                exprTokens.add(Expr.Var(txt))
                                i++
                            }
                        } else {
                            exprTokens.add(Expr.Var(txt))
                            i++
                        }
                    } else {
                        val expr = nodeToExpr(node)
                        if (expr != null) exprTokens.add(expr)
                        i++
                    }
                }
                else -> {
                    val expr = nodeToExpr(node)
                    if (expr != null) exprTokens.add(expr)
                    i++
                }
            }
        }

        // Insert implicit multiplication between adjacent Exprs
        val processedTokens = mutableListOf<Any>()
        var j = 0
        while (j < exprTokens.size) {
            val current = exprTokens[j]
            processedTokens.add(current)
            if (current is Expr || (current is String && current == ")")) {
                if (j + 1 < exprTokens.size) {
                    val next = exprTokens[j + 1]
                    if (next is Expr || (next is String && next in listOf("sin", "cos", "tan", "arcsin", "arccos", "arctan", "asin", "acos", "atan", "log", "ln", "abs", "floor", "ceil", "cuberoot", "("))) {
                        processedTokens.add("×") // implicit multiplication
                    }
                }
            }
            j++
        }

        return evaluateTokens(processedTokens)
    }

    private fun evaluateTokens(tokens: List<Any>): Expr? {
        if (tokens.isEmpty()) return null
        // Standard Shunting-yard algorithm to parse tokens with standard operators: +, -, ×, ÷, ^, !
        val values = Stack<Expr>()
        val ops = Stack<String>()

        fun precedence(op: String): Int {
            return when (op) {
                "=", "±" -> 1
                "+", "-" -> 2
                "×", "*", "÷", "/" -> 3
                "^" -> 4
                "!" -> 5
                else -> -1
            }
        }

        fun applyOp(op: String) {
            if (op == "!") {
                if (values.isNotEmpty()) {
                    val v = values.pop()
                    values.push(Expr.Fn("factorial", v))
                }
                return
            }
            if (values.size < 2) return
            val right = values.pop()
            val left = values.pop()
            val expr = when (op) {
                "+" -> Expr.Add(left, right)
                "-" -> Expr.Sub(left, right)
                "×", "*" -> Expr.Mul(left, right)
                "÷", "/" -> Expr.Div(left, right)
                "^" -> Expr.Pow(left, right)
                else -> Expr.Add(left, right)
            }
            values.push(expr)
        }

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (token is Expr) {
                values.push(token)
            } else if (token is String) {
                when (token) {
                    "(" -> ops.push(token)
                    ")" -> {
                        while (ops.isNotEmpty() && ops.peek() != "(") {
                            applyOp(ops.pop())
                        }
                        if (ops.isNotEmpty()) ops.pop() // remove '('
                    }
                    else -> {
                        // Operator
                        // Handle unary minus: if '-' is preceded by an operator or at start
                        if (token == "-" && (i == 0 || (tokens[i - 1] is String && tokens[i - 1] in listOf("+", "-", "×", "*", "÷", "/", "(", "=")))) {
                            // It's a unary operator. Let's look at the next element.
                            if (i + 1 < tokens.size && tokens[i + 1] is Expr) {
                                values.push(Expr.Neg(tokens[i + 1] as Expr))
                                i += 2
                                continue
                            }
                        }

                        while (ops.isNotEmpty() && precedence(ops.peek()) >= precedence(token)) {
                            applyOp(ops.pop())
                        }
                        ops.push(token)
                    }
                }
            }
            i++
        }

        while (ops.isNotEmpty()) {
            applyOp(ops.pop())
        }

        return if (values.isNotEmpty()) values.peek() else null
    }

    data class SolutionResult(
        val type: String, // "calculation" or "equation"
        val inputLaTeX: String,
        val steps: List<String>, // Step-by-step LaTeX lines
        val exactResultLaTeX: String, // Exact formatted result (fraction or radical)
        val decimalResult: String, // Decimal format
        val rootXValues: List<Double> = emptyList(), // List of raw x values if solving
        val geometricInterpretation: String? = null
    ) : java.io.Serializable

    fun solve(input: String): SolutionResult {
        val inputLaTeX = input.trim()
        val rootNode = MathParser.parse(inputLaTeX)

        // 1. Matrix operations check
        // Check for Matrix Addition or Subtraction: Matrix op Matrix
        if (rootNode is MathNode.Row && rootNode.children.size == 3 &&
            rootNode.children[0] is MathNode.Matrix &&
            rootNode.children[1] is MathNode.Operator &&
            rootNode.children[2] is MathNode.Matrix) {
            
            val m1 = rootNode.children[0] as MathNode.Matrix
            val op = (rootNode.children[1] as MathNode.Operator).op
            val m2 = rootNode.children[2] as MathNode.Matrix
            
            val d1 = extractMatrixDoubles(m1)
            val d2 = extractMatrixDoubles(m2)
            
            if (d1 != null && d2 != null) {
                val r1 = d1.size; val c1 = d1[0].size
                val r2 = d2.size; val c2 = d2[0].size
                if (r1 == r2 && c1 == c2) {
                    val res = List(r1) { r ->
                        List(c1) { c ->
                            if (op == "+") d1[r][c] + d2[r][c] else d1[r][c] - d2[r][c]
                        }
                    }
                    val steps = listOf(
                        "矩阵加减运算:",
                        "${matrixToLaTeX(d1, m1.type)} $op ${matrixToLaTeX(d2, m2.type)}",
                        "对应元素相${if (op == "+") "加" else "减"}:",
                        "得结果矩阵:"
                    )
                    return SolutionResult(
                        type = "calculation",
                        inputLaTeX = inputLaTeX,
                        steps = steps,
                        exactResultLaTeX = matrixToLaTeX(res, m1.type),
                        decimalResult = "Matrix operation completed successfully",
                        geometricInterpretation = "在几何上，矩阵代表线性变换。两个矩阵的加减法，相当于在其代表的空间变换中对应维度坐标位移进行直接线性叠加。"
                    )
                } else {
                    return SolutionResult(
                        type = "calculation",
                        inputLaTeX = inputLaTeX,
                        steps = listOf("矩阵维度不匹配，无法进行加减运算。", "矩阵 A 的维度为 ${r1}x${c1}，矩阵 B 的维度为 ${r2}x${c2}。"),
                        exactResultLaTeX = "\\text{Dimension Mismatch}",
                        decimalResult = "Dimension Mismatch"
                    )
                }
            }
        }

        // Check for single Matrix analysis
        val singleMatrixNode = if (rootNode is MathNode.Row && rootNode.children.size == 1 && rootNode.children[0] is MathNode.Matrix) {
            rootNode.children[0] as MathNode.Matrix
        } else if (rootNode is MathNode.Matrix) {
            rootNode
        } else {
            null
        }

        if (singleMatrixNode != null) {
            val d = extractMatrixDoubles(singleMatrixNode)
            if (d != null) {
                val r = d.size; val c = d[0].size
                val steps = mutableListOf<String>()
                steps.add("输入矩阵: ${matrixToLaTeX(d, singleMatrixNode.type)}，大小为 ${r}x${c}")
                
                val trans = transpose(d)
                steps.add("其转置矩阵 (Transpose \$A^T\$) 为:")
                steps.add(matrixToLaTeX(trans, singleMatrixNode.type))
                
                var exactResult = matrixToLaTeX(trans, singleMatrixNode.type)
                var decimalRes = "Transpose computed"
                var geoInterp = "在几何上，矩阵代表线性变换。转置矩阵 \$A^T\$ 将行向量线性变换与列向量对偶互换。对于描述正交基，转置对应着逆旋转变换。"
                
                if (r == c) {
                    if (r == 2) {
                        val det = det2x2(d)
                        steps.add("该矩阵为 2x2 方阵，其行列式 (Determinant) 计算为:")
                        steps.add("\\det(A) = a_{11}a_{22} - a_{12}a_{21} = ${formatVal(d[0][0])} \\times ${formatVal(d[1][1])} - ${formatVal(d[0][1])} \\times ${formatVal(d[1][0])}")
                        steps.add("\\det(A) = ${formatVal(det)}")
                        
                        exactResult = "\\det(A) = ${formatVal(det)}"
                        decimalRes = "det = ${formatVal(det)}"
                        geoInterp = "在几何上，行列式的值 \$\\det(A) = ${formatVal(det)}\$ 代表在二维平面上线性变换所缩放的单位面积倍数因子。若行列式为零值，代表变换后图形降维坍塌至直线或点。"
                        
                        val inv = inv2x2(d, det)
                        if (inv != null) {
                            steps.add("因为 \$\\det(A) \\neq 0\$，该矩阵可逆，其逆矩阵 (Inverse \$A^{-1}\$) 为:")
                            steps.add(matrixToLaTeX(inv, singleMatrixNode.type))
                        } else {
                            steps.add("因为 \$\\det(A) = 0\$，该矩阵为奇异矩阵，不可逆。")
                        }
                    } else if (r == 3) {
                        val det = det3x3(d)
                        steps.add("该矩阵为 3x3 方阵，其行列式 (Determinant) 计算为:")
                        steps.add("\\det(A) = ${formatVal(det)}")
                        exactResult = "\\det(A) = ${formatVal(det)}"
                        decimalRes = "det = ${formatVal(det)}"
                        geoInterp = "在几何上，行列式的值 \$\\det(A) = ${formatVal(det)}\$ 代表在线性三维空间变换中所缩放的单位平行六面体体积因子。正负号代表空间定向手性是否发生翻转。"
                    }
                }
                
                return SolutionResult(
                    type = "calculation",
                    inputLaTeX = inputLaTeX,
                    steps = steps,
                    exactResultLaTeX = exactResult,
                    decimalResult = decimalRes,
                    geometricInterpretation = geoInterp
                )
            }
        }

        // 2. Calculus operations check (Integral, Sum, Limit, Derivative)
        val firstNode = if (rootNode is MathNode.Row && rootNode.children.isNotEmpty()) rootNode.children[0] else rootNode
        
        // A. Integral
        if (firstNode is MathNode.Integral) {
            val fromNode = firstNode.from
            val toNode = firstNode.to
            val integrandNode = if (rootNode is MathNode.Row && rootNode.children.size > 1) {
                MathNode.Row(rootNode.children.subList(1, rootNode.children.size))
            } else {
                MathNode.Text("x")
            }
            val integrandExpr = nodeToExpr(integrandNode)
            if (integrandExpr != null && fromNode != null && toNode != null) {
                val fromVal = nodeToExpr(fromNode)?.eval(emptyMap())
                val toVal = nodeToExpr(toNode)?.eval(emptyMap())
                if (fromVal != null && toVal != null) {
                    val res = integrateNumerically(integrandExpr, fromVal, toVal)
                    val steps = listOf(
                        "定积分计算:",
                        "\\int_{${formatVal(fromVal)}}^{${formatVal(toVal)}} f(x) \\, dx",
                        "其中被积函数 \$f(x)\$ 经解析为: $integrandExpr",
                        "采用辛普森数值积分算法计算，得近似面积为:",
                        "\\int_{${formatVal(fromVal)}}^{${formatVal(toVal)}} f(x) \\, dx \\approx ${formatVal(res)}"
                    )
                    return SolutionResult(
                        type = "calculation",
                        inputLaTeX = inputLaTeX,
                        steps = steps,
                        exactResultLaTeX = formatVal(res),
                        decimalResult = String.format(Locale.US, "%.6f", res).replace(Regex("\\.?0+$"), ""),
                        geometricInterpretation = "在几何上，这代表曲线 \$y = f(x)\$ 在区间 [${formatVal(fromVal)}, ${formatVal(toVal)}] 内与 x 轴围成的有向面积。面积在 x 轴上方的部分为正数，下方的部分为负数。"
                    )
                }
            }
        }

        // B. Summation
        if (firstNode is MathNode.Sum) {
            val fromNode = firstNode.from
            val toNode = firstNode.to
            val termNode = if (rootNode is MathNode.Row && rootNode.children.size > 1) {
                MathNode.Row(rootNode.children.subList(1, rootNode.children.size))
            } else {
                MathNode.Text("i")
            }
            
            var varName = "i"
            var startVal = 1.0
            if (fromNode is MathNode.Subscript) {
                val base = nodeToExpr(fromNode.base)
                val sub = nodeToExpr(fromNode.subscript)
                if (base is Expr.Var) varName = base.name
                if (sub is Expr.Num) startVal = sub.value
            } else if (fromNode is MathNode.Row && fromNode.children.size >= 3) {
                val first = nodeToExpr(fromNode.children[0])
                val third = nodeToExpr(fromNode.children[2])
                if (first is Expr.Var) varName = first.name
                if (third is Expr.Num) startVal = third.value
            } else {
                val fe = nodeToExpr(fromNode ?: MathNode.Text("1"))
                if (fe is Expr.Num) startVal = fe.value
            }
            
            val endVal = nodeToExpr(toNode ?: MathNode.Text("10"))?.eval(emptyMap()) ?: 10.0
            val termExpr = nodeToExpr(termNode)
            
            if (termExpr != null) {
                var totalSum = 0.0
                val startInt = startVal.toInt()
                val endInt = endVal.toInt()
                for (v in startInt..endInt) {
                    totalSum += termExpr.eval(mapOf(varName to v.toDouble()))
                }
                val steps = listOf(
                    "求和 (Summation) 计算:",
                    "\\sum_{$varName = $startInt}^{$endInt} f($varName)",
                    "其中项 \$f($varName)\$ 经解析为: $termExpr",
                    "逐项累加计算得:",
                    "\\sum_{$varName = $startInt}^{$endInt} f($varName) = ${formatVal(totalSum)}"
                )
                return SolutionResult(
                    type = "calculation",
                    inputLaTeX = inputLaTeX,
                    steps = steps,
                    exactResultLaTeX = formatVal(totalSum),
                    decimalResult = String.format(Locale.US, "%.4f", totalSum).replace(Regex("\\.?0+$"), ""),
                    geometricInterpretation = "在几何上，求和运算代表将一组离散值按级数顺序进行合并累加。它是黎曼和以及离散型概率分布建模的核心手段。"
                )
            }
        }

        // C. Limit
        if (firstNode is MathNode.Limit) {
            val subNode = firstNode.variable
            var varName = "x"
            var approachVal = 0.0
            if (subNode != null) {
                val txt = subNode.toString()
                val match = Regex("(\\d+(\\.\\d+)?)").findAll(txt).lastOrNull()
                if (match != null) {
                    approachVal = match.value.toDoubleOrNull() ?: 0.0
                }
            }
            
            val functionNode = if (rootNode is MathNode.Row && rootNode.children.size > 1) {
                MathNode.Row(rootNode.children.subList(1, rootNode.children.size))
            } else {
                MathNode.Text("x")
            }
            
            val functionExpr = nodeToExpr(functionNode)
            if (functionExpr != null) {
                val eps = 1e-6
                val y1 = try { functionExpr.eval(mapOf(varName to approachVal + eps)) } catch(e: Exception) { Double.NaN }
                val y2 = try { functionExpr.eval(mapOf(varName to approachVal - eps)) } catch(e: Exception) { Double.NaN }
                
                val limitVal = if (!y1.isNaN() && !y2.isNaN() && abs(y1 - y2) < 1e-2) {
                    (y1 + y2) / 2.0
                } else if (!y1.isNaN()) {
                    y1
                } else if (!y2.isNaN()) {
                    y2
                } else {
                    Double.NaN
                }
                
                if (!limitVal.isNaN()) {
                    val steps = listOf(
                        "极限 (Limit) 计算:",
                        "\\lim_{$varName \\to ${formatVal(approachVal)}} f($varName)",
                        "其中函数 \$f($varName)\$ 经解析为: $functionExpr",
                        "自变量 \$varName\$ 无限接近 ${formatVal(approachVal)} 时，数值逼近计算得极限值为:",
                        "\\lim_{$varName \\to ${formatVal(approachVal)}} f($varName) \\approx ${formatVal(limitVal)}"
                    )
                    return SolutionResult(
                        type = "calculation",
                        inputLaTeX = inputLaTeX,
                        steps = steps,
                        exactResultLaTeX = formatVal(limitVal),
                        decimalResult = String.format(Locale.US, "%.4f", limitVal).replace(Regex("\\.?0+$"), ""),
                        geometricInterpretation = "在几何上，这描述了自变量 \$x\$ 无限逼近 \$approachVal\$ 时函数曲线 \$y\$ 值的逼近态势。这对于精确分析函数的奇点、不连续点以及水平/垂直渐近线至关重要。"
                    )
                }
            }
        }

        // D. Derivative
        val isDerivative = firstNode is MathNode.Fraction &&
                           (firstNode.numerator as? MathNode.Text)?.text?.trim() == "d" &&
                           (firstNode.denominator as? MathNode.Text)?.text?.trim() == "dx"
        
        if (isDerivative) {
            val functionNode = if (rootNode is MathNode.Row && rootNode.children.size > 1) {
                MathNode.Row(rootNode.children.subList(1, rootNode.children.size))
            } else {
                MathNode.Text("x")
            }
            
            val functionExpr = nodeToExpr(functionNode)
            if (functionExpr != null) {
                var xVal = 2.0
                val h = 1e-5
                val yPlus = try { functionExpr.eval(mapOf("x" to xVal + h)) } catch(e: Exception) { Double.NaN }
                val yMinus = try { functionExpr.eval(mapOf("x" to xVal - h)) } catch(e: Exception) { Double.NaN }
                
                if (!yPlus.isNaN() && !yMinus.isNaN()) {
                    val deriv = (yPlus - yMinus) / (2.0 * h)
                    val steps = listOf(
                        "导数 (Derivative) 计算:",
                        "\\frac{d}{dx} f(x) \\quad \\text{在 } x = ${formatVal(xVal)} \\text{ 处}",
                        "函数 \$f(x)\$ 经解析为: $functionExpr",
                        "采用中心差分数值导数公式计算得切线斜率:",
                        "\\frac{d}{dx} f(x) \\approx ${formatVal(deriv)}"
                    )
                    return SolutionResult(
                        type = "calculation",
                        inputLaTeX = inputLaTeX,
                        steps = steps,
                        exactResultLaTeX = formatVal(deriv),
                        decimalResult = String.format(Locale.US, "%.4f", deriv).replace(Regex("\\.?0+$"), ""),
                        geometricInterpretation = "在几何上，导数代表曲线 \$y = f(x)\$ 在指定点 \$x = ${formatVal(xVal)}\$ 处切线 (Tangent Line) 的几何斜率。正值说明函数在该点处处于递增状态，负值说明处于递减状态。"
                    )
                }
            }
        }

        val tokens = MathParser.tokenize(inputLaTeX)
        val equalsIndex = tokens.indexOfFirst { it.type == TokenType.OPERATOR && it.value == "=" }

        if (equalsIndex != -1) {
            // It's an equation!
            val leftLaTeX = tokens.subList(0, equalsIndex).joinToString("") { it.value }
            val rightLaTeX = tokens.subList(equalsIndex + 1, tokens.size).joinToString("") { it.value }

            val leftNode = MathParser.parse(leftLaTeX)
            val rightNode = MathParser.parse(rightLaTeX)

            val leftExpr = nodeToExpr(leftNode)
            val rightExpr = nodeToExpr(rightNode)

            if (leftExpr == null || rightExpr == null) {
                return SolutionResult(
                    type = "equation",
                    inputLaTeX = inputLaTeX,
                    steps = listOf("无法解析等式的左右两侧。"),
                    exactResultLaTeX = "\\text{Error}",
                    decimalResult = "Parsing error"
                )
            }

            val leftVars = leftExpr.getVariables()
            val rightVars = rightExpr.getVariables()
            val allVars = (leftVars + rightVars) - setOf("e", "pi", "π")

            // Check if equation involves 'y' (2D function equation)
            if ("y" in allVars) {
                val steps = mutableListOf<String>()
                steps.add("原方程: $leftLaTeX = $rightLaTeX")

                val exactStr: String
                val decStr: String

                if (leftExpr is Expr.Var && leftExpr.name == "y" && "y" !in rightVars) {
                    steps.add("该方程显式给出了因变量 \$y\$ 关于自变量 \$x\$ 的函数解析式:")
                    steps.add("y = $rightLaTeX")
                    exactStr = "y = $rightLaTeX"
                    decStr = "y = $rightLaTeX"
                } else if (rightExpr is Expr.Var && rightExpr.name == "y" && "y" !in leftVars) {
                    steps.add("交换左右两端，解出 \$y\$ 关于 \$x\$ 的函数关系:")
                    steps.add("y = $leftLaTeX")
                    exactStr = "y = $leftLaTeX"
                    decStr = "y = $leftLaTeX"
                } else if (leftExpr is Expr.Var && leftExpr.name == "x" && rightExpr is Expr.Var && rightExpr.name == "y") {
                    steps.add("交换左右两端，解出 \$y\$ 关于 \$x\$ 的函数关系:")
                    steps.add("y = x")
                    exactStr = "y = x"
                    decStr = "y = x"
                } else {
                    steps.add("此为包含自变量 \$x\$ 与因变量 \$y\$ 的函数图像方程。")
                    steps.add("已在下方笛卡尔坐标系中绘制出其对应曲线。")
                    exactStr = "y = f(x)"
                    decStr = "Function plot generated"
                }

                return SolutionResult(
                    type = "equation",
                    inputLaTeX = inputLaTeX,
                    steps = steps,
                    exactResultLaTeX = exactStr,
                    decimalResult = decStr,
                    geometricInterpretation = "在几何上，该方程在二维笛卡尔直角坐标系中对应一条平面曲线。已自动生成可视化函数图像。"
                )
            }

            // Let f(x) = left - right
            // We want to solve f(x) = 0
            val f = Expr.Sub(leftExpr, rightExpr)

            // Let's analyze the coefficients of f(x) to see if it's linear or quadratic
            // We evaluate f(x) for several values of x to find its coefficients
            try {
                val f0 = f.eval(mapOf("x" to 0.0))
                val f1 = f.eval(mapOf("x" to 1.0))
                val fn1 = f.eval(mapOf("x" to -1.0))

                // Check quadratic: f(x) = A*x^2 + B*x + C
                // C = f(0)
                // B = (f(1) - f(-1)) / 2
                // A = (f(1) + f(-1)) / 2 - f(0)
                val C = f0
                val B = (f1 - fn1) / 2.0
                val A = (f1 + fn1) / 2.0 - f0

                // Let's verify quadratic integrity at x = 2 and x = -2
                val f2 = f.eval(mapOf("x" to 2.0))
                val fn2 = f.eval(mapOf("x" to -2.0))

                val expectedF2 = A * 4.0 + B * 2.0 + C
                val expectedFn2 = A * 4.0 - B * 2.0 + C

                val isQuadratic = abs(f2 - expectedF2) < 1e-7 && abs(fn2 - expectedFn2) < 1e-7
                val isLinear = isQuadratic && abs(A) < 1e-7

                if (isLinear) {
                    // Linear: B*x + C = 0
                    if (abs(B) < 1e-9) {
                        return if (abs(C) < 1e-9) {
                            SolutionResult(
                                type = "equation",
                                inputLaTeX = inputLaTeX,
                                steps = listOf("方程化简为: $0 = 0$", "此方程有无穷多个解。"),
                                exactResultLaTeX = "x \\in \\mathbb{R}",
                                decimalResult = "Infinite solutions"
                            )
                        } else {
                            SolutionResult(
                                type = "equation",
                                inputLaTeX = inputLaTeX,
                                steps = listOf("方程化简为: ${String.format(Locale.US, "%.4f", C)} = 0$", "此方程无解。"),
                                exactResultLaTeX = "\\varnothing",
                                decimalResult = "No solution",
                                geometricInterpretation = "在几何上，这代表方程左右两侧的函数图像相互平行，在实数空间内没有任何交点。"
                            )
                        }
                    }

                    val root = -C / B
                    val steps = mutableListOf<String>()
                    steps.add("原方程: $leftLaTeX = $rightLaTeX")
                    steps.add("移项并合并同类项，化简为一次方程:")
                    steps.add("${formatCoef(B)}x + ${formatVal(C)} = 0")
                    steps.add("移项得:")
                    steps.add("${formatCoef(B)}x = ${formatVal(-C)}")
                    steps.add("两边同除以 ${formatVal(B)} 得:")

                    val frac = doubleToFraction(root)
                    val exactStr = if (frac != null) {
                        "x = $frac"
                    } else {
                        "x = ${String.format(Locale.US, "%.4f", root)}"
                    }
                    steps.add(exactStr)

                    return SolutionResult(
                        type = "equation",
                        inputLaTeX = inputLaTeX,
                        steps = steps,
                        exactResultLaTeX = exactStr,
                        decimalResult = "x = ${String.format(Locale.US, "%.4f", root)}",
                        rootXValues = listOf(root),
                        geometricInterpretation = "在几何上，一元一次方程代表一条直线 \$y = Ax + B\$ 与 x 轴的交点。此直线在实数轴上有且仅有一个交点。"
                    )
                } else if (isQuadratic) {
                    // Quadratic: A*x^2 + B*x + C = 0
                    val steps = mutableListOf<String>()
                    steps.add("原方程: $leftLaTeX = $rightLaTeX")
                    steps.add("移项并合并同类项，化简为二次方程:")
                    steps.add("${formatCoef(A)}x^2 + ${formatCoef(B)}x + ${formatVal(C)} = 0")

                    val disc = B * B - 4.0 * A * C
                    steps.add("计算判别式 $\\Delta = b^2 - 4ac$:")
                    steps.add("\\Delta = (${formatVal(B)})^2 - 4 \\times (${formatVal(A)}) \\times (${formatVal(C)})")
                    steps.add("\\Delta = ${formatVal(disc)}")

                    if (disc < 0) {
                        steps.add("因为 $\\Delta < 0$，该方程在实数范围内无解。")
                        // Calculate complex roots
                        val real = -B / (2.0 * A)
                        val imag = sqrt(-disc) / (2.0 * A)
                        val exactStr = "x = ${formatVal(real)} \\pm ${formatVal(imag)}i"
                        return SolutionResult(
                            type = "equation",
                            inputLaTeX = inputLaTeX,
                            steps = steps,
                            exactResultLaTeX = exactStr,
                            decimalResult = "No real roots",
                            geometricInterpretation = "在几何上，当判别式 \$\\Delta < 0\$ 时，对应的抛物线 \$y = Ax^2 + Bx + C\$ 整体处于 x 轴的上方（若开口向上）或下方，与 x 轴无任何实数交点。"
                        )
                    } else if (abs(disc) < 1e-9) {
                        val root = -B / (2.0 * A)
                        steps.add("因为 $\\Delta = 0$，方程有且只有一个重根:")
                        steps.add("x = \\frac{-b}{2a} = \\frac{-(${formatVal(B)})}{2 \\times (${formatVal(A)})}")
                        val frac = doubleToFraction(root)
                        val exactStr = if (frac != null) "x = $frac" else "x = ${formatVal(root)}"
                        steps.add(exactStr)

                        return SolutionResult(
                            type = "equation",
                            inputLaTeX = inputLaTeX,
                            steps = steps,
                            exactResultLaTeX = exactStr,
                            decimalResult = "x = ${String.format(Locale.US, "%.4f", root)}",
                            rootXValues = listOf(root),
                            geometricInterpretation = "在几何上，一元二次方程代表一条抛物线 \$y = Ax^2 + Bx + C\$ 与 x 轴的交点。由于判别式 \$\\Delta = 0\$，抛物线的顶点正好与 x 轴相切。"
                        )
                    } else {
                        // Two distinct roots
                        val root1 = (-B + sqrt(disc)) / (2.0 * A)
                        val root2 = (-B - sqrt(disc)) / (2.0 * A)

                        steps.add("因为 $\\Delta > 0$，方程有两个不同的实根:")
                        steps.add("x = \\frac{-b \\pm \\sqrt{\\Delta}}{2a}")
                        steps.add("x = \\frac{-(${formatVal(B)}) \\pm \\sqrt{${formatVal(disc)}}}{2 \\times (${formatVal(A)})}")

                        // Attempt to simplify the radical expression
                        // We check if A, B, C are integer-like to simplify analytically
                        val aL = A.roundToLong()
                        val bL = B.roundToLong()
                        val cL = C.roundToLong()
                        val isIntCoef = abs(A - aL) < 1e-5 && abs(B - bL) < 1e-5 && abs(C - cL) < 1e-5

                        var exactStr = ""
                        if (isIntCoef) {
                            val discL = bL * bL - 4 * aL * cL
                            val (outSq, inSq) = simplifySqrt(discL)
                            if (inSq == 1L) {
                                // Perfect square discriminant, roots are rational!
                                val root1Frac = doubleToFraction(root1)
                                val root2Frac = doubleToFraction(root2)
                                exactStr = "x_1 = ${root1Frac ?: formatVal(root1)}, \\quad x_2 = ${root2Frac ?: formatVal(root2)}"
                            } else {
                                // Simplified radical form: (-b \pm outSq * \sqrt{inSq}) / 2a
                                val numeratorStr = if (outSq == 1L) {
                                    "${-bL} \\pm \\sqrt{$inSq}"
                                } else {
                                    "${-bL} \\pm $outSq\\sqrt{$inSq}"
                                }
                                exactStr = "x = \\frac{$numeratorStr}{${2 * aL}}"
                            }
                        } else {
                            exactStr = "x_1 = ${formatVal(root1)}, \\quad x_2 = ${formatVal(root2)}"
                        }

                        steps.add("精确根形式:")
                        steps.add(exactStr)

                        return SolutionResult(
                            type = "equation",
                            inputLaTeX = inputLaTeX,
                            steps = steps,
                            exactResultLaTeX = exactStr,
                            decimalResult = "x_1 = ${String.format(Locale.US, "%.4f", root1)}\nx_2 = ${String.format(Locale.US, "%.4f", root2)}",
                            rootXValues = listOf(root1, root2),
                            geometricInterpretation = "在几何上，一元二次方程代表抛物线 \$y = Ax^2 + Bx + C\$ 与 x 轴的交点。由于判别式 \$\\Delta > 0\$，抛物线在实数轴上与 x 轴有两个不同的实数交点。"
                        )
                    }
                } else {
                    // Non-linear, non-quadratic: Solve numerically in range [-10, 10]
                    val steps = mutableListOf<String>()
                    steps.add("原方程: $leftLaTeX = $rightLaTeX")
                    steps.add("移项合并得 \$f(x) = 0\$，此方程为超越方程或高次方程。")
                    steps.add("我们将使用数值逼近算法（二分法）在区间 $[-10, 10]$ 内搜索实根...")

                    val roots = findRootsNumerically(f)
                    if (roots.isEmpty()) {
                        steps.add("在区间内未找到实数根。")
                        return SolutionResult(
                            type = "equation",
                            inputLaTeX = inputLaTeX,
                            steps = steps,
                            exactResultLaTeX = "\\text{无实根}",
                            decimalResult = "No real roots found"
                        )
                    } else {
                        steps.add("发现 ${roots.size} 个实数根。")
                        val rootsStr = roots.joinToString(", ") { String.format(Locale.US, "%.4f", it) }
                        val exactLaTeX = roots.mapIndexed { idx, r -> "x_${idx + 1} \\approx ${String.format(Locale.US, "%.4f", r)}" }.joinToString(", \\quad ")
                        steps.add("解为: $exactLaTeX")

                        return SolutionResult(
                            type = "equation",
                            inputLaTeX = inputLaTeX,
                            steps = steps,
                            exactResultLaTeX = exactLaTeX,
                            decimalResult = roots.mapIndexed { idx, r -> "x${idx + 1} = ${String.format(Locale.US, "%.4f", r)}" }.joinToString("\n"),
                            rootXValues = roots,
                            geometricInterpretation = "在几何上，此超越或高阶方程代表曲线 \$y = f(x)\$ 与 x 轴的交点。数值逼近算法精确算出了所有实数相交横坐标。"
                        )
                    }
                }
            } catch (e: Exception) {
                // E.g., Undefined variables other than x
                return SolutionResult(
                    type = "equation",
                    inputLaTeX = inputLaTeX,
                    steps = listOf("原等式: $inputLaTeX", "计算或化简出错: ${e.localizedMessage}"),
                    exactResultLaTeX = "\\text{Error}",
                    decimalResult = "Error"
                )
            }
        } else {
            // It's a standard arithmetic calculation!
            val rootNode = MathParser.parse(inputLaTeX)
            val expr = nodeToExpr(rootNode)

            if (expr == null) {
                return SolutionResult(
                    type = "calculation",
                    inputLaTeX = inputLaTeX,
                    steps = listOf("无法解析输入表达式。"),
                    exactResultLaTeX = "\\text{Error}",
                    decimalResult = "Parsing error"
                )
            }

            val hasX = inputLaTeX.contains("x", ignoreCase = true)

            return try {
                val resVal = expr.eval(emptyMap())
                val steps = mutableListOf<String>()
                steps.add("输入表达式: $inputLaTeX")
                steps.add("计算步骤:")

                // Check if it's a simple operation to show details
                steps.add("直接计算得:")
                steps.add("${formatVal(resVal)}")

                val frac = doubleToFraction(resVal)
                val exactStr = if (frac != null) {
                    if (frac.den == 1L) "${frac.num}" else "\\frac{${frac.num}}{${frac.den}}"
                } else {
                    formatVal(resVal)
                }

                SolutionResult(
                    type = "calculation",
                    inputLaTeX = inputLaTeX,
                    steps = steps,
                    exactResultLaTeX = exactStr,
                    decimalResult = String.format(Locale.US, "%.6f", resVal).replace(Regex("\\.?0+$"), "")
                )
            } catch (e: Exception) {
                if (hasX) {
                    SolutionResult(
                        type = "calculation",
                        inputLaTeX = inputLaTeX,
                        steps = listOf("输入函数: $inputLaTeX", "由于包含自变量，已在直角坐标系中为您绘制出连续图像。"),
                        exactResultLaTeX = "f(x) = $inputLaTeX",
                        decimalResult = "Function Plot"
                    )
                } else {
                    SolutionResult(
                        type = "calculation",
                        inputLaTeX = inputLaTeX,
                        steps = listOf("计算出错: ${e.localizedMessage}"),
                        exactResultLaTeX = "\\text{Error}",
                        decimalResult = "Calculation error"
                    )
                }
            }
        }
    }

    private fun findRootsNumerically(expr: Expr): List<Double> {
        val roots = mutableListOf<Double>()
        val start = -10.0
        val end = 10.0
        val steps = 200
        val stepSize = (end - start) / steps

        // Scan for sign changes and apply bisection
        var prevX = start
        var prevY = try { expr.eval(mapOf("x" to prevX)) } catch (e: Exception) { Double.NaN }

        for (k in 1..steps) {
            val currX = start + k * stepSize
            val currY = try { expr.eval(mapOf("x" to currX)) } catch (e: Exception) { Double.NaN }

            if (!prevY.isNaN() && !currY.isNaN()) {
                if (prevY * currY <= 0.0) {
                    // Sign change found, do bisection
                    val root = bisection(expr, prevX, currX)
                    if (!root.isNaN()) {
                        // Check if we already have this root
                        if (roots.none { abs(it - root) < 1e-4 }) {
                            roots.add(root)
                        }
                    }
                }
            }
            prevX = currX
            prevY = currY
        }
        return roots.sorted()
    }

    private fun bisection(expr: Expr, low: Double, high: Double): Double {
        var a = low
        var b = high
        var fa = expr.eval(mapOf("x" to a))
        var fb = expr.eval(mapOf("x" to b))

        if (abs(fa) < 1e-12) return a
        if (abs(fb) < 1e-12) return b

        for (i in 0..60) {
            val mid = (a + b) / 2.0
            val fmid = try { expr.eval(mapOf("x" to mid)) } catch (e: Exception) { return Double.NaN }

            if (abs(fmid) < 1e-9 || abs(b - a) < 1e-12) {
                return mid
            }

            if (fa * fmid < 0.0) {
                b = mid
                fb = fmid
            } else {
                a = mid
                fa = fmid
            }
        }
        return (a + b) / 2.0
    }

    private fun formatVal(v: Double): String {
        if (v.isNaN()) return "\\text{NaN}"
        if (v.isInfinite()) return if (v > 0) "\\infty" else "-\\infty"
        val r = v.roundToLong()
        if (abs(v - r) < 1e-9) return r.toString()
        return String.format(Locale.US, "%.4f", v).replace(Regex("\\.?0+$"), "")
    }

    private fun formatCoef(v: Double): String {
        if (abs(v - 1.0) < 1e-9) return ""
        if (abs(v + 1.0) < 1e-9) return "-"
        return formatVal(v)
    }

    private fun extractMatrixDoubles(matrix: MathNode.Matrix): List<List<Double>>? {
        return try {
            matrix.rows.map { row ->
                row.map { cell ->
                    val expr = nodeToExpr(cell) ?: return null
                    expr.eval(emptyMap())
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun transpose(matrix: List<List<Double>>): List<List<Double>> {
        val r = matrix.size
        val c = matrix[0].size
        return List(c) { col ->
            List(r) { row ->
                matrix[row][col]
            }
        }
    }

    private fun det2x2(m: List<List<Double>>): Double = m[0][0]*m[1][1] - m[0][1]*m[1][0]

    private fun det3x3(m: List<List<Double>>): Double {
        return m[0][0]*(m[1][1]*m[2][2] - m[1][2]*m[2][1]) -
               m[0][1]*(m[1][0]*m[2][2] - m[1][2]*m[2][0]) +
               m[0][2]*(m[1][0]*m[2][1] - m[1][1]*m[2][0])
    }

    private fun inv2x2(m: List<List<Double>>, det: Double): List<List<Double>>? {
        if (abs(det) < 1e-12) return null
        return listOf(
            listOf(m[1][1] / det, -m[0][1] / det),
            listOf(-m[1][0] / det, m[0][0] / det)
        )
    }

    private fun matrixToLaTeX(m: List<List<Double>>, type: String = "pmatrix"): String {
        val body = m.joinToString(" \\\\ ") { row ->
            row.joinToString(" & ") { v ->
                formatVal(v)
            }
        }
        return "\\begin{$type} $body \\end{$type}"
    }

    private fun integrateNumerically(expr: Expr, a: Double, b: Double): Double {
        val n = 1000 // must be even
        val h = (b - a) / n
        var sum = try { expr.eval(mapOf("x" to a)) + expr.eval(mapOf("x" to b)) } catch(e: Exception) { 0.0 }
        for (i in 1 until n) {
            val x = a + i * h
            val weight = if (i % 2 == 0) 2.0 else 4.0
            sum += weight * (try { expr.eval(mapOf("x" to x)) } catch(e: Exception) { 0.0 })
        }
        return (h / 3.0) * sum
    }
}
