package com.example

import android.util.Log
import java.util.*
import kotlin.math.*

sealed class Expr {
    data class Num(val value: Double) : Expr()
    data class Const(val symbol: String) : Expr()
    data class Var(val name: String) : Expr()
    data class Add(val left: Expr, val right: Expr) : Expr()
    data class Sub(val left: Expr, val right: Expr) : Expr()
    data class Mul(val left: Expr, val right: Expr) : Expr()
    data class Div(val left: Expr, val right: Expr) : Expr()
    data class Pow(val base: Expr, val exp: Expr) : Expr()
    data class Sqrt(val expr: Expr) : Expr()
    data class Root(val index: Expr, val expr: Expr) : Expr()
    data class Fn(val name: String, val arg: Expr) : Expr()
    data class Neg(val expr: Expr) : Expr()
    data class Mod(val left: Expr, val right: Expr) : Expr()

    fun eval(env: Map<String, Double>): Double {
        return when (this) {
            is Num -> value
            is Const -> {
                when (symbol) {
                    "\\pi", "π", "pi" -> PI
                    "e" -> E
                    "i" -> env["i"] ?: throw ArithmeticException("Complex number i cannot be evaluated as real Double")
                    else -> env[symbol] ?: throw IllegalArgumentException("Undefined constant: $symbol")
                }
            }
            is Var -> env[name] ?: throw IllegalArgumentException("Undefined variable: $name")
            is Add -> left.eval(env) + right.eval(env)
            is Sub -> left.eval(env) - right.eval(env)
            is Mul -> left.eval(env) * right.eval(env)
            is Div -> {
                val den = right.eval(env)
                if (abs(den) < 1e-15) throw ArithmeticException("Division by zero")
                left.eval(env) / den
            }
            is Mod -> {
                val den = right.eval(env)
                if (abs(den) < 1e-15) throw ArithmeticException("Division by zero in modulo")
                left.eval(env) % den
            }
            is Pow -> {
                val b = base.eval(env)
                val e = exp.eval(env)
                if (b < 0) {
                    val invE = 1.0 / e
                    val invEInt = invE.roundToInt()
                    if (abs(invE - invEInt) < 1e-9 && invEInt % 2 != 0) {
                        -(-b).pow(e)
                    } else {
                        b.pow(e)
                    }
                } else {
                    b.pow(e)
                }
            }
            is Sqrt -> {
                val v = expr.eval(env)
                if (v < 0) throw ArithmeticException("Square root of negative number")
                sqrt(v)
            }
            is Root -> {
                val idx = index.eval(env)
                val v = expr.eval(env)
                val idxInt = idx.roundToInt()
                if (abs(idx - idxInt) < 1e-9 && idxInt > 0) {
                    if (idxInt % 2 != 0) {
                        if (v < 0) -(-v).pow(1.0 / idxInt) else v.pow(1.0 / idxInt)
                    } else {
                        if (v < 0) throw ArithmeticException("Even root of negative number")
                        v.pow(1.0 / idxInt)
                    }
                } else {
                    if (v < 0) throw ArithmeticException("Negative base with non-integer root index")
                    v.pow(1.0 / idx)
                }
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
            is Const -> emptySet()
            is Var -> setOf(name)
            is Add -> left.getVariables() + right.getVariables()
            is Sub -> left.getVariables() + right.getVariables()
            is Mul -> left.getVariables() + right.getVariables()
            is Div -> left.getVariables() + right.getVariables()
            is Pow -> base.getVariables() + exp.getVariables()
            is Sqrt -> expr.getVariables()
            is Root -> index.getVariables() + expr.getVariables()
            is Fn -> arg.getVariables()
            is Neg -> expr.getVariables()
            is Mod -> left.getVariables() + right.getVariables()
        }
    }

    fun toLaTeX(): String {
        return when (this) {
            is Num -> {
                val r = value.roundToLong()
                if (abs(value - r) < 1e-9) "$r"
                else {
                    val frac = MathSolver.doubleToFraction(value)
                    if (frac != null) frac.toLaTeX() else String.format(Locale.US, "%.6f", value).replace(Regex("\\.?0+$"), "")
                }
            }
            is Const -> {
                when (symbol) {
                    "\\pi", "π", "pi" -> "\\pi"
                    "e" -> "e"
                    "i" -> "i"
                    else -> symbol
                }
            }
            is Var -> name
            is Add -> "${left.toLaTeX()} + ${right.toLaTeX()}"
            is Sub -> "${left.toLaTeX()} - ${right.toLaTeX()}"
            is Mul -> {
                val lStr = if (left is Add || left is Sub) "(${left.toLaTeX()})" else left.toLaTeX()
                val rStr = if (right is Add || right is Sub) "(${right.toLaTeX()})" else right.toLaTeX()
                if (left is Num && (right is Var || right is Const || right is Sqrt || right is Root)) {
                    if (left.value == 1.0) right.toLaTeX()
                    else if (left.value == -1.0) "-${right.toLaTeX()}"
                    else "$lStr$rStr"
                } else if (left is Const && (right is Var || right is Const || right is Sqrt || right is Root)) {
                    "$lStr$rStr"
                } else "$lStr \\cdot $rStr"
            }
            is Div -> "\\frac{${left.toLaTeX()}}{${right.toLaTeX()}}"
            is Mod -> {
                val lStr = if (left is Add || left is Sub) "(${left.toLaTeX()})" else left.toLaTeX()
                val rStr = if (right is Add || right is Sub) "(${right.toLaTeX()})" else right.toLaTeX()
                "$lStr \\% $rStr"
            }
            is Pow -> {
                val bStr = if (base is Add || base is Sub || base is Mul || base is Div || base is Neg) "(${base.toLaTeX()})" else base.toLaTeX()
                "{$bStr}^{${exp.toLaTeX()}}"
            }
            is Sqrt -> "\\sqrt{${expr.toLaTeX()}}"
            is Root -> {
                val idxStr = index.toLaTeX()
                if (idxStr == "2") "\\sqrt{${expr.toLaTeX()}}"
                else "\\sqrt[$idxStr]{${expr.toLaTeX()}}"
            }
            is Fn -> "\\$name(${arg.toLaTeX()})"
            is Neg -> {
                val eStr = if (expr is Add || expr is Sub) "(${expr.toLaTeX()})" else expr.toLaTeX()
                "-$eStr"
            }
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

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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

fun lcm(a: Long, b: Long): Long {
    if (a == 0L || b == 0L) return 0L
    return abs(a * b) / gcd(a, b)
}

data class RadicalExpr(
    val terms: Map<Long, Long>, // Key: square-free positive integer r >= 1. Value: non-zero coefficient a_r
    val den: Long = 1L          // Denominator > 0
) {
    init {
        require(den > 0) { "Denominator must be positive" }
    }

    companion object {
        val ZERO = RadicalExpr(emptyMap(), 1L)
        val ONE = RadicalExpr(mapOf(1L to 1L), 1L)

        fun fromLong(v: Long): RadicalExpr {
            if (v == 0L) return ZERO
            return RadicalExpr(mapOf(1L to v), 1L)
        }

        fun fromFraction(num: Long, den: Long): RadicalExpr {
            if (num == 0L) return ZERO
            val g = gcd(abs(num), abs(den))
            val n = num / g
            val d = den / g
            return if (d < 0) RadicalExpr(mapOf(1L to -n), -d) else RadicalExpr(mapOf(1L to n), d)
        }
    }

    fun isZero(): Boolean = terms.isEmpty() || terms.values.all { it == 0L }

    fun simplify(): RadicalExpr {
        val cleaned = terms.filter { it.value != 0L }
        if (cleaned.isEmpty()) return ZERO

        var g = abs(den)
        for (v in cleaned.values) {
            g = gcd(g, abs(v))
        }

        val d = den / g
        val newTerms = cleaned.mapValues { it.value / g }
        return RadicalExpr(newTerms, d)
    }

    operator fun unaryMinus(): RadicalExpr {
        return RadicalExpr(terms.mapValues { -it.value }, den)
    }

    operator fun plus(other: RadicalExpr): RadicalExpr {
        if (this.isZero()) return other
        if (other.isZero()) return this

        val commonDen = lcm(this.den, other.den)
        val m1 = commonDen / this.den
        val m2 = commonDen / other.den

        val newTerms = mutableMapOf<Long, Long>()
        for ((r, coeff) in this.terms) {
            newTerms[r] = (newTerms[r] ?: 0L) + coeff * m1
        }
        for ((r, coeff) in other.terms) {
            newTerms[r] = (newTerms[r] ?: 0L) + coeff * m2
        }

        return RadicalExpr(newTerms, commonDen).simplify()
    }

    operator fun minus(other: RadicalExpr): RadicalExpr {
        return this + (-other)
    }

    operator fun times(other: RadicalExpr): RadicalExpr {
        if (this.isZero() || other.isZero()) return ZERO

        val newDen = this.den * other.den
        val newTerms = mutableMapOf<Long, Long>()

        for ((r1, c1) in this.terms) {
            for ((r2, c2) in other.terms) {
                val g = gcd(r1, r2)
                val multCoeff = c1 * c2 * g
                val remR = (r1 / g) * (r2 / g)
                newTerms[remR] = (newTerms[remR] ?: 0L) + multCoeff
            }
        }

        return RadicalExpr(newTerms, newDen).simplify()
    }

    operator fun div(other: RadicalExpr): RadicalExpr? {
        if (other.isZero()) return null
        if (this.isZero()) return ZERO

        val cleanedOther = other.simplify()
        val otherTerms = cleanedOther.terms.filter { it.value != 0L }

        if (otherTerms.size == 1) {
            val (r, c) = otherTerms.entries.first()
            val d = cleanedOther.den
            val conjugate = RadicalExpr(mapOf(r to d), c * r)
            return (this * conjugate)
        } else if (otherTerms.size == 2) {
            val entries = otherTerms.entries.toList()
            val (r1, c1) = entries[0]
            val (r2, c2) = entries[1]
            val d = cleanedOther.den

            val conjugateTerms = mapOf(r1 to c1, r2 to -c2)
            val conjugate = RadicalExpr(conjugateTerms, 1L)

            val denRational = (c1 * c1 * r1 - c2 * c2 * r2)
            if (denRational == 0L) return null

            val factor = RadicalExpr(mapOf(1L to d), denRational)
            return (this * conjugate * factor)
        }

        return null
    }

    fun pow(n: Long): RadicalExpr? {
        if (n == 0L) return ONE
        if (n > 0L) {
            var res = ONE
            var base = this
            var p = n
            while (p > 0L) {
                if (p % 2L == 1L) res = (res * base)
                base = (base * base)
                p /= 2L
            }
            return res
        } else {
            val posPow = pow(-n) ?: return null
            return ONE / posPow
        }
    }

    fun sqrt(): RadicalExpr? {
        if (this.isZero()) return ZERO
        val s = this.simplify()
        val cleaned = s.terms.filter { it.value != 0L }

        if (cleaned.size == 1 && cleaned.containsKey(1L)) {
            val num = cleaned[1L]!!
            val den = s.den
            if (num < 0) return null

            val prod = num * den
            val (outside, inside) = MathSolver.simplifySqrt(prod)
            return RadicalExpr(mapOf(inside to outside), den).simplify()
        }

        return null
    }

    fun toDouble(): Double {
        var sum = 0.0
        for ((r, c) in terms) {
            sum += c * kotlin.math.sqrt(r.toDouble())
        }
        return sum / den.toDouble()
    }

    fun toLaTeX(): String {
        val s = simplify()
        if (s.isZero()) return "0"

        val cleaned = s.terms.filter { it.value != 0L }
        if (cleaned.isEmpty()) return "0"

        val sortedKeys = cleaned.keys.sortedWith(Comparator { k1, k2 ->
            if (k1 == 1L) -1 else if (k2 == 1L) 1 else k1.compareTo(k2)
        })

        val sb = StringBuilder()
        var first = true

        for (r in sortedKeys) {
            val c = cleaned[r]!!
            val absC = abs(c)

            if (first) {
                if (c < 0) sb.append("-")
            } else {
                if (c < 0) sb.append(" - ") else sb.append(" + ")
            }

            if (r == 1L) {
                sb.append(absC)
            } else {
                if (absC != 1L) {
                    sb.append(absC)
                }
                sb.append("\\sqrt{").append(r).append("}")
            }
            first = false
        }

        val numStr = sb.toString()
        return if (s.den == 1L) {
            numStr
        } else {
            "\\frac{$numStr}{${s.den}}"
        }
    }
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

    fun hasVar(expr: Expr, varName: String = "x"): Boolean {
        return varName in expr.getVariables()
    }

    fun extractLinearCoeffs(expr: Expr, varName: String = "x"): Pair<Expr, Expr>? {
        if (!hasVar(expr, varName)) {
            return Pair(Expr.Num(0.0), expr)
        }
        return when (expr) {
            is Expr.Var -> {
                if (expr.name == varName) Pair(Expr.Num(1.0), Expr.Num(0.0))
                else Pair(Expr.Num(0.0), expr)
            }
            is Expr.Add -> {
                val (lC, lK) = extractLinearCoeffs(expr.left, varName) ?: return null
                val (rC, rK) = extractLinearCoeffs(expr.right, varName) ?: return null
                Pair(Expr.Add(lC, rC), Expr.Add(lK, rK))
            }
            is Expr.Sub -> {
                val (lC, lK) = extractLinearCoeffs(expr.left, varName) ?: return null
                val (rC, rK) = extractLinearCoeffs(expr.right, varName) ?: return null
                Pair(Expr.Sub(lC, rC), Expr.Sub(lK, rK))
            }
            is Expr.Neg -> {
                val (c, k) = extractLinearCoeffs(expr.expr, varName) ?: return null
                Pair(Expr.Neg(c), Expr.Neg(k))
            }
            is Expr.Mul -> {
                val leftHas = hasVar(expr.left, varName)
                val rightHas = hasVar(expr.right, varName)
                if (leftHas && rightHas) {
                    null
                } else if (!leftHas) {
                    val (rC, rK) = extractLinearCoeffs(expr.right, varName) ?: return null
                    Pair(Expr.Mul(expr.left, rC), Expr.Mul(expr.left, rK))
                } else {
                    val (lC, lK) = extractLinearCoeffs(expr.left, varName) ?: return null
                    Pair(Expr.Mul(lC, expr.right), Expr.Mul(lK, expr.right))
                }
            }
            is Expr.Div -> {
                if (hasVar(expr.right, varName)) null
                else {
                    val (lC, lK) = extractLinearCoeffs(expr.left, varName) ?: return null
                    Pair(Expr.Div(lC, expr.right), Expr.Div(lK, expr.right))
                }
            }
            else -> null
        }
    }

    fun extractQuadraticCoeffs(expr: Expr, varName: String = "x"): Triple<Expr, Expr, Expr>? {
        if (!hasVar(expr, varName)) {
            return Triple(Expr.Num(0.0), Expr.Num(0.0), expr)
        }
        return when (expr) {
            is Expr.Var -> {
                if (expr.name == varName) Triple(Expr.Num(0.0), Expr.Num(1.0), Expr.Num(0.0))
                else Triple(Expr.Num(0.0), Expr.Num(0.0), expr)
            }
            is Expr.Pow -> {
                val expVal = (expr.exp as? Expr.Num)?.value
                if (expVal != null && abs(expVal - 2.0) < 1e-9) {
                    val lin = extractLinearCoeffs(expr.base, varName)
                    if (lin != null) {
                        val (l1, l0) = lin
                        Triple(
                            Expr.Mul(l1, l1),
                            Expr.Mul(Expr.Num(2.0), Expr.Mul(l1, l0)),
                            Expr.Mul(l0, l0)
                        )
                    } else if (!hasVar(expr, varName)) {
                        Triple(Expr.Num(0.0), Expr.Num(0.0), expr)
                    } else null
                } else if (!hasVar(expr, varName)) {
                    Triple(Expr.Num(0.0), Expr.Num(0.0), expr)
                } else null
            }
            is Expr.Add -> {
                val (a1, b1, c1) = extractQuadraticCoeffs(expr.left, varName) ?: return null
                val (a2, b2, c2) = extractQuadraticCoeffs(expr.right, varName) ?: return null
                Triple(Expr.Add(a1, a2), Expr.Add(b1, b2), Expr.Add(c1, c2))
            }
            is Expr.Sub -> {
                val (a1, b1, c1) = extractQuadraticCoeffs(expr.left, varName) ?: return null
                val (a2, b2, c2) = extractQuadraticCoeffs(expr.right, varName) ?: return null
                Triple(Expr.Sub(a1, a2), Expr.Sub(b1, b2), Expr.Sub(c1, c2))
            }
            is Expr.Neg -> {
                val (a, b, c) = extractQuadraticCoeffs(expr.expr, varName) ?: return null
                Triple(Expr.Neg(a), Expr.Neg(b), Expr.Neg(c))
            }
            is Expr.Mul -> {
                val leftHas = hasVar(expr.left, varName)
                val rightHas = hasVar(expr.right, varName)
                if (!leftHas) {
                    val (a, b, c) = extractQuadraticCoeffs(expr.right, varName) ?: return null
                    Triple(Expr.Mul(expr.left, a), Expr.Mul(expr.left, b), Expr.Mul(expr.left, c))
                } else if (!rightHas) {
                    val (a, b, c) = extractQuadraticCoeffs(expr.left, varName) ?: return null
                    Triple(Expr.Mul(a, expr.right), Expr.Mul(b, expr.right), Expr.Mul(c, expr.right))
                } else {
                    val (l1, l0) = extractLinearCoeffs(expr.left, varName) ?: return null
                    val (r1, r0) = extractLinearCoeffs(expr.right, varName) ?: return null
                    Triple(
                        Expr.Mul(l1, r1),
                        Expr.Add(Expr.Mul(l1, r0), Expr.Mul(l0, r1)),
                        Expr.Mul(l0, r0)
                    )
                }
            }
            is Expr.Div -> {
                if (hasVar(expr.right, varName)) null
                else {
                    val (a, b, c) = extractQuadraticCoeffs(expr.left, varName) ?: return null
                    Triple(Expr.Div(a, expr.right), Expr.Div(b, expr.right), Expr.Div(c, expr.right))
                }
            }
            else -> null
        }
    }

    private fun parseTextToExpr(txtRaw: String): Expr? {
        val txt = txtRaw.trim()
        if (txt.isEmpty()) return null
        if (txt == "pi" || txt == "π" || txt == "\\pi") return Expr.Const("\\pi")
        if (txt == "e") return Expr.Const("e")
        if (txt == "i") return Expr.Const("i")
        val d = txt.toDoubleOrNull()
        if (d != null) return Expr.Num(d)
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
                val varExpr = when (varStr) {
                    "pi", "π" -> Expr.Const("\\pi")
                    "e" -> Expr.Const("e")
                    "i" -> Expr.Const("i")
                    else -> Expr.Var(varStr)
                }
                return Expr.Mul(Expr.Num(numVal), varExpr)
            }
        }
        return Expr.Var(txt)
    }

    data class RationalPower(val baseNum: Long, val baseDen: Long, val powNum: Long, val powDen: Long)
    data class FactorComponent(val base: Expr, val exp: Long)

    private fun primeFactorize(n: Long): Map<Long, Long> {
        var temp = abs(n)
        val factors = mutableMapOf<Long, Long>()
        var d = 2L
        while (d * d <= temp) {
            while (temp % d == 0L) {
                factors[d] = factors.getOrDefault(d, 0L) + 1L
                temp /= d
            }
            d++
        }
        if (temp > 1L) {
            factors[temp] = factors.getOrDefault(temp, 0L) + 1L
        }
        return factors
    }

    private fun algebraicFactorization(expr: Expr): List<FactorComponent> {
        return when (expr) {
            is Expr.Mul -> algebraicFactorization(expr.left) + algebraicFactorization(expr.right)
            is Expr.Div -> algebraicFactorization(expr.left) + algebraicFactorization(expr.right).map { FactorComponent(it.base, -it.exp) }
            is Expr.Pow -> {
                val baseFactors = algebraicFactorization(expr.base)
                val expConst = evalConstFraction(expr.exp)
                if (expConst != null && expConst.den == 1L) {
                    baseFactors.map { FactorComponent(it.base, it.exp * expConst.num) }
                } else {
                    listOf(FactorComponent(expr, 1L))
                }
            }
            is Expr.Sqrt -> listOf(FactorComponent(expr, 1L))
            is Expr.Root -> listOf(FactorComponent(expr, 1L))
            is Expr.Num -> {
                val v = expr.value
                val r = v.roundToLong()
                if (abs(v - r) < 1e-9) {
                    if (r == 0L) listOf(FactorComponent(Expr.Num(0.0), 1L))
                    else if (r == 1L) emptyList()
                    else if (r == -1L) listOf(FactorComponent(Expr.Num(-1.0), 1L))
                    else {
                        val isNeg = r < 0
                        val pf = primeFactorize(abs(r))
                        val res = pf.map { (p, e) -> FactorComponent(Expr.Num(p.toDouble()), e) }.toMutableList()
                        if (isNeg) res.add(0, FactorComponent(Expr.Num(-1.0), 1L))
                        res
                    }
                } else {
                    val frac = doubleToFraction(v)
                    if (frac != null) {
                        algebraicFactorization(Expr.Div(Expr.Num(frac.num.toDouble()), Expr.Num(frac.den.toDouble())))
                    } else {
                        listOf(FactorComponent(expr, 1L))
                    }
                }
            }
            is Expr.Neg -> listOf(FactorComponent(Expr.Num(-1.0), 1L)) + algebraicFactorization(expr.expr)
            else -> listOf(FactorComponent(expr, 1L))
        }
    }

    /**
     * Pseudocode implementation of simplify_radical(n, m)
     */
    fun simplifyRadical(n: Expr, m: Long): Expr {
        // Step 0: Unnest nested roots (\sqrt[m]{\sqrt[k]{x}} -> \sqrt[m*k]{x})
        var currN = n
        var currM = m
        while (true) {
            if (currN is Expr.Sqrt) {
                currM *= 2L
                currN = currN.expr
            } else if (currN is Expr.Root) {
                val idxFrac = evalConstFraction(currN.index)
                if (idxFrac != null && idxFrac.den == 1L && idxFrac.num > 0) {
                    currM *= idxFrac.num
                    currN = currN.expr
                } else {
                    break
                }
            } else {
                break
            }
        }
        val realN = currN
        val realM = currM

        // Step 1: Boundary & Normalization
        if (realM == 1L) return realN
        if (realM <= 0L) return Expr.Root(Expr.Num(realM.toDouble()), realN)
        val evalN = try { realN.eval(emptyMap()) } catch (e: Exception) { Double.NaN }
        if (evalN == 0.0) return Expr.Num(0.0)
        if (evalN == 1.0) return Expr.Num(1.0)

        // Step 2: Algebraic Factorization
        val factors = algebraicFactorization(realN)

        // Step 3: Extract complete m-th powers & handle exponents
        var outsideNum = 1L
        var outsideDen = 1L
        val outsideExprs = mutableListOf<Expr>()
        val insideComponents = mutableListOf<FactorComponent>()

        for ((base, exp) in factors) {
            if (base is Expr.Num) {
                val v = base.value
                val r = v.roundToLong()
                if (abs(v - r) < 1e-9 && r > 0) {
                    val pf = primeFactorize(r)
                    for ((p, pExp) in pf) {
                        val totalExp = pExp * exp
                        val q = kotlin.math.floor(totalExp.toDouble() / realM.toDouble()).toLong()
                        val rem = totalExp - q * realM
                        if (q > 0) {
                            outsideNum *= p.toDouble().pow(q.toDouble()).toLong()
                        } else if (q < 0) {
                            outsideDen *= p.toDouble().pow((-q).toDouble()).toLong()
                        }
                        if (rem != 0L) {
                            insideComponents.add(FactorComponent(Expr.Num(p.toDouble()), rem))
                        }
                    }
                } else if (abs(v - (-1.0)) < 1e-9) {
                    if (exp % 2L != 0L) {
                        if (realM % 2L != 0L) {
                            outsideNum = -outsideNum
                        } else {
                            insideComponents.add(FactorComponent(Expr.Num(-1.0), 1L))
                        }
                    }
                } else {
                    insideComponents.add(FactorComponent(base, exp))
                }
            } else {
                val q = kotlin.math.floor(exp.toDouble() / realM.toDouble()).toLong()
                val rem = exp - q * realM
                if (q != 0L) {
                    val outPow = if (q == 1L) base else Expr.Pow(base, Expr.Num(q.toDouble()))
                    outsideExprs.add(outPow)
                }
                if (rem != 0L) {
                    insideComponents.add(FactorComponent(base, rem))
                }
            }
        }

        var outsideConst: Expr = if (outsideDen == 1L) Expr.Num(outsideNum.toDouble()) else Expr.Div(Expr.Num(outsideNum.toDouble()), Expr.Num(outsideDen.toDouble()))
        if (outsideExprs.isNotEmpty()) {
            val outMul = outsideExprs.reduce { acc, expr -> Expr.Mul(acc, expr) }
            outsideConst = if (outsideNum == 1L && outsideDen == 1L) outMul
            else if (outsideNum == -1L && outsideDen == 1L) Expr.Neg(outMul)
            else Expr.Mul(outsideConst, outMul)
        }

        // Step 4: Root Index Reduction via GCD
        if (insideComponents.isEmpty()) {
            return outsideConst
        }

        var g = realM
        for ((_, exp) in insideComponents) {
            g = gcd(g, abs(exp))
            if (g == 1L) break
        }

        val newM = realM / g
        val newInsideComponents = insideComponents.map { FactorComponent(it.base, it.exp / g) }

        // Step 6: Reconstruct standard form
        var insideNumExpr: Expr = Expr.Num(1.0)
        var insideDenExpr: Expr = Expr.Num(1.0)

        for ((base, exp) in newInsideComponents) {
            val term = if (exp == 1L) base else Expr.Pow(base, Expr.Num(exp.toDouble()))
            if (exp > 0) {
                insideNumExpr = if (insideNumExpr == Expr.Num(1.0)) term else Expr.Mul(insideNumExpr, term)
            } else {
                val posTerm = if (-exp == 1L) base else Expr.Pow(base, Expr.Num((-exp).toDouble()))
                insideDenExpr = if (insideDenExpr == Expr.Num(1.0)) posTerm else Expr.Mul(insideDenExpr, posTerm)
            }
        }

        val insideExpr = if (insideDenExpr == Expr.Num(1.0)) insideNumExpr else Expr.Div(insideNumExpr, insideDenExpr)
        val rootExpr = if (newM == 2L) Expr.Sqrt(insideExpr) else Expr.Root(Expr.Num(newM.toDouble()), insideExpr)

        return if (outsideConst == Expr.Num(1.0)) rootExpr
        else if (outsideConst == Expr.Num(-1.0)) Expr.Neg(rootExpr)
        else Expr.Mul(outsideConst, rootExpr)
    }

    private fun evalConstFraction(expr: Expr): SolverFraction? {
        return when (expr) {
            is Expr.Num -> {
                val v = expr.value
                val r = v.roundToLong()
                if (abs(v - r) < 1e-9) SolverFraction(r, 1L)
                else doubleToFraction(v)?.let { SolverFraction(it.num, it.den) }
            }
            is Expr.Div -> {
                val n = evalConstFraction(expr.left) ?: return null
                val d = evalConstFraction(expr.right) ?: return null
                if (d.num == 0L) null
                else SolverFraction(n.num * d.den, n.den * d.num).simplify()
            }
            is Expr.Neg -> {
                val inner = evalConstFraction(expr.expr) ?: return null
                SolverFraction(-inner.num, inner.den)
            }
            else -> null
        }
    }

    fun extractRationalPower(expr: Expr): RationalPower? {
        return when (expr) {
            is Expr.Num -> {
                val v = expr.value
                val r = v.roundToLong()
                if (abs(v - r) < 1e-9) RationalPower(r, 1L, 1L, 1L)
                else {
                    val frac = doubleToFraction(v) ?: return null
                    RationalPower(frac.num, frac.den, 1L, 1L)
                }
            }
            is Expr.Sqrt -> {
                val innerRP = extractRationalPower(expr.expr) ?: return null
                RationalPower(innerRP.baseNum, innerRP.baseDen, innerRP.powNum, innerRP.powDen * 2L)
            }
            is Expr.Root -> {
                val idxFrac = evalConstFraction(expr.index) ?: return null
                if (idxFrac.den != 1L || idxFrac.num <= 0) return null
                val n = idxFrac.num
                val innerRP = extractRationalPower(expr.expr) ?: return null
                RationalPower(innerRP.baseNum, innerRP.baseDen, innerRP.powNum, innerRP.powDen * n)
            }
            is Expr.Pow -> {
                val baseRP = extractRationalPower(expr.base) ?: return null
                val expFrac = evalConstFraction(expr.exp) ?: return null
                val newPowNum = baseRP.powNum * expFrac.num
                val newPowDen = baseRP.powDen * expFrac.den
                val g = gcd(abs(newPowNum), abs(newPowDen))
                RationalPower(baseRP.baseNum, baseRP.baseDen, newPowNum / g, newPowDen / g)
            }
            is Expr.Neg -> {
                val innerRP = extractRationalPower(expr.expr) ?: return null
                RationalPower(-innerRP.baseNum, innerRP.baseDen, innerRP.powNum, innerRP.powDen)
            }
            else -> null
        }
    }

    fun simplifyRadicalPower(baseNum: Long, baseDen: Long, powNum: Long, powDen: Long): String? {
        if (powDen <= 0L || baseNum == 0L) return null
        val isNegativeBase = baseNum < 0
        if (isNegativeBase && powDen % 2L == 0L) return null

        val absBaseNum = abs(baseNum)
        val absBaseDen = abs(baseDen)

        val totalPowNum = abs(powNum)
        val Q = powDen

        val numFactors = primeFactorize(absBaseNum).mapValues { it.value * totalPowNum }
        val denFactors = primeFactorize(absBaseDen).mapValues { it.value * totalPowNum }

        var outNum = 1L
        var inNum = 1L
        for ((p, exp) in numFactors) {
            val q = exp / Q
            val r = exp % Q
            outNum *= p.toDouble().pow(q.toDouble()).toLong()
            inNum *= p.toDouble().pow(r.toDouble()).toLong()
        }

        var outDen = 1L
        var inDen = 1L
        for ((p, exp) in denFactors) {
            val q = exp / Q
            val r = exp % Q
            outDen *= p.toDouble().pow(q.toDouble()).toLong()
            inDen *= p.toDouble().pow(r.toDouble()).toLong()
        }

        if (inDen > 1L) {
            val multiplyFactor = inDen.toDouble().pow((Q - 1).toDouble()).toLong()
            inNum *= multiplyFactor
            outDen *= inDen
            inDen = 1L
        }

        if (isNegativeBase && powNum % 2L != 0L) {
            outNum = -outNum
        }
        if (powNum < 0) {
            val tmpN = outNum
            outNum = outDen
            outDen = tmpN
        }

        val g = gcd(abs(outNum), abs(outDen))
        outNum /= g
        outDen /= g

        if (inNum == 1L) {
            return if (outDen == 1L) "$outNum" else "\\frac{$outNum}{$outDen}"
        }

        val coefStr = when {
            outNum == 1L && outDen == 1L -> ""
            outNum == -1L && outDen == 1L -> "-"
            outDen == 1L -> "$outNum"
            else -> "\\frac{$outNum}{$outDen}"
        }

        val radStr = if (Q == 2L) "\\sqrt{$inNum}" else "\\sqrt[$Q]{$inNum}"
        return if (coefStr.isEmpty()) radStr else "$coefStr $radStr"
    }

    fun trySimplifyExprToRadical(expr: Expr): String? {
        return try {
            when (expr) {
                is Expr.Sqrt -> simplifyRadical(expr.expr, 2L).toLaTeX()
                is Expr.Root -> {
                    val idxFrac = evalConstFraction(expr.index)
                    if (idxFrac != null && idxFrac.den == 1L && idxFrac.num >= 2L) {
                        simplifyRadical(expr.expr, idxFrac.num).toLaTeX()
                    } else {
                        expr.toLaTeX()
                    }
                }
                else -> {
                    val rp = extractRationalPower(expr)
                    if (rp != null) {
                        simplifyRadicalPower(rp.baseNum, rp.baseDen, rp.powNum, rp.powDen)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    data class SymbolicTerm(val coeffNum: Long, val coeffDen: Long, val factors: Map<Expr, Long>) {
        fun simplifyCoeff(): SymbolicTerm {
            val g = gcd(abs(coeffNum), abs(coeffDen))
            if (g == 0L) return SymbolicTerm(0L, 1L, emptyMap())
            var n = coeffNum / g
            var d = coeffDen / g
            if (d < 0) { n = -n; d = -d }
            return SymbolicTerm(n, d, factors)
        }
    }

    private fun simplifySin(a: Expr): Expr {
        if (a is Expr.Num) {
            val v = a.value
            if (abs(v) < 1e-9) return Expr.Num(0.0)
        }
        if (a is Expr.Const && (a.symbol == "\\pi" || a.symbol == "π" || a.symbol == "pi")) return Expr.Num(0.0)
        if (a is Expr.Mul) {
            val l = a.left
            val r = a.right
            val k = if (l is Expr.Num && r is Expr.Const && (r.symbol == "\\pi" || r.symbol == "π")) l.value
            else if (r is Expr.Num && l is Expr.Const && (l.symbol == "\\pi" || l.symbol == "π")) r.value
            else null

            if (k != null) {
                val kInt = k.roundToInt()
                if (abs(k - kInt) < 1e-9) return Expr.Num(0.0)
                if (abs(k - 0.5) < 1e-9) return Expr.Num(1.0)
                if (abs(k - 1.5) < 1e-9) return Expr.Num(-1.0)
                if (abs(k - 1.0/6.0) < 1e-9) return Expr.Div(Expr.Num(1.0), Expr.Num(2.0))
                if (abs(k - 0.25) < 1e-9) return Expr.Div(Expr.Sqrt(Expr.Num(2.0)), Expr.Num(2.0))
                if (abs(k - 1.0/3.0) < 1e-9) return Expr.Div(Expr.Sqrt(Expr.Num(3.0)), Expr.Num(2.0))
            }
        }
        if (a is Expr.Div) {
            val l = a.left
            val r = a.right
            if (l is Expr.Const && (l.symbol == "\\pi" || l.symbol == "π") && r is Expr.Num) {
                val d = r.value
                if (abs(d - 2.0) < 1e-9) return Expr.Num(1.0)
                if (abs(d - 6.0) < 1e-9) return Expr.Div(Expr.Num(1.0), Expr.Num(2.0))
                if (abs(d - 4.0) < 1e-9) return Expr.Div(Expr.Sqrt(Expr.Num(2.0)), Expr.Num(2.0))
                if (abs(d - 3.0) < 1e-9) return Expr.Div(Expr.Sqrt(Expr.Num(3.0)), Expr.Num(2.0))
            }
        }
        return Expr.Fn("sin", a)
    }

    private fun simplifyCos(a: Expr): Expr {
        if (a is Expr.Num && abs(a.value) < 1e-9) return Expr.Num(1.0)
        if (a is Expr.Const && (a.symbol == "\\pi" || a.symbol == "π" || a.symbol == "pi")) return Expr.Num(-1.0)
        if (a is Expr.Mul) {
            val l = a.left
            val r = a.right
            val k = if (l is Expr.Num && r is Expr.Const && (r.symbol == "\\pi" || r.symbol == "π")) l.value
            else if (r is Expr.Num && l is Expr.Const && (l.symbol == "\\pi" || l.symbol == "π")) r.value
            else null

            if (k != null) {
                if (abs(k - 0.5) < 1e-9) return Expr.Num(0.0)
                if (abs(k - 1.0/6.0) < 1e-9) return Expr.Div(Expr.Sqrt(Expr.Num(3.0)), Expr.Num(2.0))
                if (abs(k - 0.25) < 1e-9) return Expr.Div(Expr.Sqrt(Expr.Num(2.0)), Expr.Num(2.0))
                if (abs(k - 1.0/3.0) < 1e-9) return Expr.Div(Expr.Num(1.0), Expr.Num(2.0))
            }
        }
        if (a is Expr.Div) {
            val l = a.left
            val r = a.right
            if (l is Expr.Const && (l.symbol == "\\pi" || l.symbol == "π") && r is Expr.Num) {
                val d = r.value
                if (abs(d - 2.0) < 1e-9) return Expr.Num(0.0)
                if (abs(d - 6.0) < 1e-9) return Expr.Div(Expr.Sqrt(Expr.Num(3.0)), Expr.Num(2.0))
                if (abs(d - 4.0) < 1e-9) return Expr.Div(Expr.Sqrt(Expr.Num(2.0)), Expr.Num(2.0))
                if (abs(d - 3.0) < 1e-9) return Expr.Div(Expr.Num(1.0), Expr.Num(2.0))
            }
        }
        return Expr.Fn("cos", a)
    }

    private fun simplifyTan(a: Expr): Expr {
        if (a is Expr.Num && abs(a.value) < 1e-9) return Expr.Num(0.0)
        if (a is Expr.Const && (a.symbol == "\\pi" || a.symbol == "π" || a.symbol == "pi")) return Expr.Num(0.0)
        if (a is Expr.Div && a.left is Expr.Const && (a.left.symbol == "\\pi" || a.left.symbol == "π") && a.right is Expr.Num) {
            if (abs(a.right.value - 4.0) < 1e-9) return Expr.Num(1.0)
        }
        return Expr.Fn("tan", a)
    }

    private fun simplifyLn(a: Expr): Expr {
        if (a is Expr.Num && abs(a.value - 1.0) < 1e-9) return Expr.Num(0.0)
        if (a is Expr.Const && a.symbol == "e") return Expr.Num(1.0)
        if (a is Expr.Pow && a.base is Expr.Const && a.base.symbol == "e") return reduceBasic(a.exp)
        return Expr.Fn("ln", a)
    }

    private fun simplifyLog(a: Expr): Expr {
        if (a is Expr.Num) {
            if (abs(a.value - 1.0) < 1e-9) return Expr.Num(0.0)
            if (abs(a.value - 10.0) < 1e-9) return Expr.Num(1.0)
        }
        if (a is Expr.Pow && a.base is Expr.Num && abs(a.base.value - 10.0) < 1e-9) return reduceBasic(a.exp)
        return Expr.Fn("log", a)
    }

    private fun reduceBasic(expr: Expr): Expr {
        return when (expr) {
            is Expr.Num, is Expr.Const, is Expr.Var -> expr
            is Expr.Neg -> {
                val inner = reduceBasic(expr.expr)
                when (inner) {
                    is Expr.Neg -> inner.expr
                    is Expr.Num -> Expr.Num(-inner.value)
                    else -> Expr.Neg(inner)
                }
            }
            is Expr.Add -> {
                val l = reduceBasic(expr.left)
                val r = reduceBasic(expr.right)
                if (l is Expr.Num && l.value == 0.0) r
                else if (r is Expr.Num && r.value == 0.0) l
                else if (l is Expr.Num && r is Expr.Num) Expr.Num(l.value + r.value)
                else Expr.Add(l, r)
            }
            is Expr.Sub -> {
                val l = reduceBasic(expr.left)
                val r = reduceBasic(expr.right)
                if (r is Expr.Num && r.value == 0.0) l
                else if (l is Expr.Num && l.value == 0.0) reduceBasic(Expr.Neg(r))
                else if (l is Expr.Num && r is Expr.Num) Expr.Num(l.value - r.value)
                else if (l == r) Expr.Num(0.0)
                else Expr.Sub(l, r)
            }
            is Expr.Mul -> {
                val l = reduceBasic(expr.left)
                val r = reduceBasic(expr.right)
                if ((l is Expr.Num && l.value == 0.0) || (r is Expr.Num && r.value == 0.0)) Expr.Num(0.0)
                else if (l is Expr.Num && l.value == 1.0) r
                else if (r is Expr.Num && r.value == 1.0) l
                else if (l is Expr.Num && l.value == -1.0) reduceBasic(Expr.Neg(r))
                else if (r is Expr.Num && r.value == -1.0) reduceBasic(Expr.Neg(l))
                else if (l is Expr.Num && r is Expr.Num) Expr.Num(l.value * r.value)
                else Expr.Mul(l, r)
            }
            is Expr.Div -> {
                val l = reduceBasic(expr.left)
                val r = reduceBasic(expr.right)
                if (l is Expr.Num && l.value == 0.0) Expr.Num(0.0)
                else if (r is Expr.Num && r.value == 1.0) l
                else if (l == r) Expr.Num(1.0)
                else if (l is Expr.Num && r is Expr.Num && r.value != 0.0) {
                    val valDiv = l.value / r.value
                    val lLong = l.value.roundToLong()
                    val rLong = r.value.roundToLong()
                    if (abs(l.value - lLong) < 1e-9 && abs(r.value - rLong) < 1e-9) {
                        val g = gcd(abs(lLong), abs(rLong))
                        if (g > 0) {
                            val num = lLong / g
                            val den = rLong / g
                            if (den == 1L) Expr.Num(num.toDouble())
                            else if (den < 0) Expr.Div(Expr.Num((-num).toDouble()), Expr.Num((-den).toDouble()))
                            else Expr.Div(Expr.Num(num.toDouble()), Expr.Num(den.toDouble()))
                        } else Expr.Num(valDiv)
                    } else Expr.Num(valDiv)
                } else Expr.Div(l, r)
            }
            is Expr.Pow -> {
                val b = reduceBasic(expr.base)
                val p = reduceBasic(expr.exp)
                if (p is Expr.Num && p.value == 0.0) Expr.Num(1.0)
                else if (p is Expr.Num && p.value == 1.0) b
                else if (b is Expr.Num && b.value == 0.0) Expr.Num(0.0)
                else if (b is Expr.Num && b.value == 1.0) Expr.Num(1.0)
                else if (b is Expr.Const && b.symbol == "i" && p is Expr.Num) {
                    val n = p.value.roundToLong()
                    if (abs(p.value - n) < 1e-9) {
                        val mod = ((n % 4) + 4) % 4
                        when (mod) {
                            0L -> Expr.Num(1.0)
                            1L -> Expr.Const("i")
                            2L -> Expr.Num(-1.0)
                            3L -> Expr.Neg(Expr.Const("i"))
                            else -> Expr.Pow(b, p)
                        }
                    } else Expr.Pow(b, p)
                } else if (b is Expr.Const && b.symbol == "e" && p is Expr.Fn && p.name == "ln") {
                    reduceBasic(p.arg)
                } else if (b is Expr.Num && p is Expr.Num) {
                    Expr.Num(b.value.pow(p.value))
                } else Expr.Pow(b, p)
            }
            is Expr.Sqrt -> {
                val arg = reduceBasic(expr.expr)
                if (arg is Expr.Num) {
                    if (arg.value >= 0.0) {
                        val s = sqrt(arg.value)
                        val sLong = s.roundToLong()
                        if (abs(s - sLong) < 1e-9) Expr.Num(sLong.toDouble())
                        else {
                            val argLong = arg.value.roundToLong()
                            if (abs(arg.value - argLong) < 1e-9 && argLong > 0) {
                                val (outSq, inSq) = simplifySqrt(argLong)
                                if (inSq == 1L) Expr.Num(outSq.toDouble())
                                else if (outSq > 1L) Expr.Mul(Expr.Num(outSq.toDouble()), Expr.Sqrt(Expr.Num(inSq.toDouble())))
                                else Expr.Sqrt(Expr.Num(argLong.toDouble()))
                            } else Expr.Sqrt(arg)
                        }
                    } else {
                        val posV = -arg.value
                        val posExpr = reduceBasic(Expr.Sqrt(Expr.Num(posV)))
                        Expr.Mul(posExpr, Expr.Const("i"))
                    }
                } else Expr.Sqrt(arg)
            }
            is Expr.Fn -> {
                val a = reduceBasic(expr.arg)
                when (expr.name) {
                    "sin" -> simplifySin(a)
                    "cos" -> simplifyCos(a)
                    "tan" -> simplifyTan(a)
                    "ln" -> simplifyLn(a)
                    "log" -> simplifyLog(a)
                    "exp" -> {
                        if (a is Expr.Num && a.value == 0.0) Expr.Num(1.0)
                        else if (a is Expr.Fn && a.name == "ln") reduceBasic(a.arg)
                        else Expr.Fn("exp", a)
                    }
                    "abs" -> {
                        if (a is Expr.Num) Expr.Num(abs(a.value))
                        else Expr.Fn("abs", a)
                    }
                    else -> Expr.Fn(expr.name, a)
                }
            }
            is Expr.Root -> {
                val idx = reduceBasic(expr.index)
                val arg = reduceBasic(expr.expr)
                Expr.Root(idx, arg)
            }
            is Expr.Mod -> {
                val l = reduceBasic(expr.left)
                val r = reduceBasic(expr.right)
                if (l is Expr.Num && r is Expr.Num && abs(r.value) > 1e-15) {
                    Expr.Num(l.value % r.value)
                } else {
                    Expr.Mod(l, r)
                }
            }
        }
    }

    fun termToExpr(term: SymbolicTerm): Expr {
        val sim = term.simplifyCoeff()
        if (sim.coeffNum == 0L) return Expr.Num(0.0)

        val factorExprs = mutableListOf<Expr>()
        for ((f, exp) in sim.factors) {
            if (exp == 1L) factorExprs.add(f)
            else if (exp != 0L) factorExprs.add(Expr.Pow(f, Expr.Num(exp.toDouble())))
        }

        if (factorExprs.isEmpty()) {
            return if (sim.coeffDen == 1L) Expr.Num(sim.coeffNum.toDouble())
            else Expr.Div(Expr.Num(sim.coeffNum.toDouble()), Expr.Num(sim.coeffDen.toDouble()))
        }

        var prod: Expr = factorExprs[0]
        for (k in 1 until factorExprs.size) {
            prod = Expr.Mul(prod, factorExprs[k])
        }

        if (sim.coeffNum == 1L && sim.coeffDen == 1L) return prod
        if (sim.coeffNum == -1L && sim.coeffDen == 1L) return Expr.Neg(prod)

        val coeffExpr = if (sim.coeffDen == 1L) Expr.Num(sim.coeffNum.toDouble())
        else Expr.Div(Expr.Num(sim.coeffNum.toDouble()), Expr.Num(sim.coeffDen.toDouble()))

        return Expr.Mul(coeffExpr, prod)
    }

    fun collectTerms(expr: Expr): List<SymbolicTerm> {
        return when (expr) {
            is Expr.Add -> collectTerms(expr.left) + collectTerms(expr.right)
            is Expr.Sub -> collectTerms(expr.left) + collectTerms(expr.right).map { SymbolicTerm(-it.coeffNum, it.coeffDen, it.factors) }
            is Expr.Neg -> collectTerms(expr.expr).map { SymbolicTerm(-it.coeffNum, it.coeffDen, it.factors) }
            is Expr.Mul -> {
                val termsA = collectTerms(expr.left)
                val termsB = collectTerms(expr.right)
                val res = mutableListOf<SymbolicTerm>()
                for (tA in termsA) {
                    for (tB in termsB) {
                        val num = tA.coeffNum * tB.coeffNum
                        val den = tA.coeffDen * tB.coeffDen
                        val combinedFactors = mutableMapOf<Expr, Long>()
                        combinedFactors.putAll(tA.factors)
                        for ((fB, eB) in tB.factors) {
                            combinedFactors[fB] = combinedFactors.getOrDefault(fB, 0L) + eB
                        }

                        var finalNum = num
                        var finalDen = den
                        if (Expr.Const("i") in combinedFactors) {
                            val iExp = combinedFactors[Expr.Const("i")]!!
                            val mod = ((iExp % 4) + 4) % 4
                            combinedFactors.remove(Expr.Const("i"))
                            when (mod) {
                                0L -> {}
                                1L -> combinedFactors[Expr.Const("i")] = 1L
                                2L -> finalNum = -finalNum
                                3L -> {
                                    finalNum = -finalNum
                                    combinedFactors[Expr.Const("i")] = 1L
                                }
                            }
                        }
                        res.add(SymbolicTerm(finalNum, finalDen, combinedFactors).simplifyCoeff())
                    }
                }
                res
            }
            is Expr.Num -> {
                val v = expr.value
                val r = v.roundToLong()
                if (abs(v - r) < 1e-9) {
                    listOf(SymbolicTerm(r, 1L, emptyMap()))
                } else {
                    val frac = doubleToFraction(v)
                    if (frac != null) listOf(SymbolicTerm(frac.num, frac.den, emptyMap()))
                    else listOf(SymbolicTerm(1L, 1L, mapOf(expr to 1L)))
                }
            }
            is Expr.Const -> {
                if (expr.symbol == "i") {
                    listOf(SymbolicTerm(1L, 1L, mapOf(Expr.Const("i") to 1L)))
                } else {
                    listOf(SymbolicTerm(1L, 1L, mapOf(expr to 1L)))
                }
            }
            is Expr.Div -> {
                val numTerms = collectTerms(expr.left)
                val denFrac = evalConstFraction(expr.right)
                if (denFrac != null && denFrac.num != 0L) {
                    numTerms.map { SymbolicTerm(it.coeffNum * denFrac.den, it.coeffDen * denFrac.num, it.factors).simplifyCoeff() }
                } else {
                    listOf(SymbolicTerm(1L, 1L, mapOf(expr to 1L)))
                }
            }
            else -> listOf(SymbolicTerm(1L, 1L, mapOf(expr to 1L)))
        }
    }

    fun simplifyCanonical(expr: Expr): Expr {
        val rawTerms = collectTerms(expr)
        val grouped = mutableMapOf<Map<Expr, Long>, Pair<Long, Long>>()

        for (term in rawTerms) {
            val sim = term.simplifyCoeff()
            if (sim.coeffNum == 0L) continue
            val key = sim.factors
            val existing = grouped[key]
            if (existing == null) {
                grouped[key] = Pair(sim.coeffNum, sim.coeffDen)
            } else {
                val n = existing.first * sim.coeffDen + sim.coeffNum * existing.second
                val d = existing.second * sim.coeffDen
                val g = gcd(abs(n), abs(d))
                if (g > 0L) {
                    grouped[key] = Pair(n / g, d / g)
                } else {
                    grouped[key] = Pair(n, d)
                }
            }
        }

        val termExprs = mutableListOf<Expr>()
        for ((factors, fraction) in grouped) {
            var (n, d) = fraction
            if (d < 0) { n = -n; d = -d }
            if (n == 0L) continue
            val term = SymbolicTerm(n, d, factors)
            termExprs.add(termToExpr(term))
        }

        if (termExprs.isEmpty()) return Expr.Num(0.0)

        var res = termExprs[0]
        for (k in 1 until termExprs.size) {
            val next = termExprs[k]
            if (next is Expr.Neg) {
                res = Expr.Sub(res, next.expr)
            } else {
                res = Expr.Add(res, next)
            }
        }
        return res
    }

    fun simplifySymbolic(expr: Expr): Expr {
        val reduced = reduceBasic(expr)
        return simplifyCanonical(reduced)
    }

    private fun formatComplexDecimal(expr: Expr): String {
        return try {
            val terms = collectTerms(expr)
            var realSum = 0.0
            var imagSum = 0.0
            var hasImag = false

            for (term in terms) {
                val iExp = term.factors[Expr.Const("i")] ?: 0L
                if (iExp > 0) {
                    hasImag = true
                    val termWithoutI = SymbolicTerm(term.coeffNum, term.coeffDen, term.factors.filterKeys { it != Expr.Const("i") })
                    val exprWithoutI = termToExpr(termWithoutI)
                    val v = exprWithoutI.eval(emptyMap())
                    imagSum += v
                } else {
                    val exprReal = termToExpr(term)
                    val v = exprReal.eval(emptyMap())
                    realSum += v
                }
            }

            if (hasImag) {
                val rStr = formatVal(realSum)
                val absI = abs(imagSum)
                val iStr = formatVal(absI)
                val sign = if (imagSum >= 0) " + " else " - "
                if (abs(realSum) < 1e-9) {
                    if (imagSum < 0) "-${iStr}i" else "${iStr}i"
                } else {
                    "$rStr$sign${iStr}i"
                }
            } else {
                val v = expr.eval(emptyMap())
                formatVal(v)
            }
        } catch (e: Exception) {
            expr.toLaTeX()
        }
    }

    // Convert MathNode to Expr
    fun exprToRadical(expr: Expr): RadicalExpr? {
        return try {
            when (expr) {
                is Expr.Num -> {
                    val v = expr.value
                    val r = v.roundToLong()
                    if (abs(v - r) < 1e-9) {
                        RadicalExpr.fromLong(r)
                    } else {
                        val frac = doubleToFraction(v)
                        if (frac != null) {
                            RadicalExpr.fromFraction(frac.num, frac.den)
                        } else {
                            null
                        }
                    }
                }
                is Expr.Const -> null
                is Expr.Var -> null
                is Expr.Add -> {
                    val l = exprToRadical(expr.left) ?: return null
                    val r = exprToRadical(expr.right) ?: return null
                    l + r
                }
                is Expr.Sub -> {
                    val l = exprToRadical(expr.left) ?: return null
                    val r = exprToRadical(expr.right) ?: return null
                    l - r
                }
                is Expr.Mul -> {
                    val l = exprToRadical(expr.left) ?: return null
                    val r = exprToRadical(expr.right) ?: return null
                    l * r
                }
                is Expr.Div -> {
                    val l = exprToRadical(expr.left) ?: return null
                    val r = exprToRadical(expr.right) ?: return null
                    l / r
                }
                is Expr.Mod -> {
                    val l = exprToRadical(expr.left) ?: return null
                    val r = exprToRadical(expr.right) ?: return null
                    val lVal = l.toDouble()
                    val rVal = r.toDouble()
                    if (abs(rVal) < 1e-9) null
                    else {
                        val res = lVal % rVal
                        val resLong = res.roundToLong()
                        if (abs(res - resLong) < 1e-9) RadicalExpr.fromLong(resLong) else null
                    }
                }
                is Expr.Neg -> {
                    val s = exprToRadical(expr.expr) ?: return null
                    -s
                }
                is Expr.Sqrt -> {
                    val s = exprToRadical(expr.expr) ?: return null
                    s.sqrt()
                }
                is Expr.Pow -> {
                    val b = exprToRadical(expr.base) ?: return null
                    val e = exprToRadical(expr.exp) ?: return null
                    if (e.terms.size == 1 && e.terms.containsKey(1L)) {
                        val num = e.terms[1L]!!
                        val den = e.den
                        if (den == 1L) {
                            b.pow(num)
                        } else if (den == 2L && num == 1L) {
                            b.sqrt()
                        } else if (den == 2L) {
                            b.pow(num)?.sqrt()
                        } else null
                    } else null
                }
                is Expr.Fn -> {
                    if (expr.name == "abs") {
                        val a = exprToRadical(expr.arg) ?: return null
                        if (a.toDouble() >= 0) a else -a
                    } else null
                }
                is Expr.Root -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun nodeToExpr(node: MathNode): Expr? {
        return try {
            when (node) {
                is MathNode.Cursor -> null
                is MathNode.Text -> parseTextToExpr(node.text)
                is MathNode.Operator -> null // Operators are handled at Row-level parsing
                is MathNode.SpecialSymbol -> {
                    when (node.symbol) {
                        "π", "pi", "\\pi" -> Expr.Const("\\pi")
                        "e" -> Expr.Const("e")
                        "i" -> Expr.Const("i")
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
                    if (node.rootIndex != null) {
                        val idx = nodeToExpr(node.rootIndex) ?: return null
                        if (idx is Expr.Num && idx.value == 2.0) {
                            Expr.Sqrt(content)
                        } else {
                            Expr.Root(idx, content)
                        }
                    } else {
                        Expr.Sqrt(content)
                    }
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

    private fun combineAdjacentNodes(nodes: List<MathNode>): List<MathNode> {
        val filtered = nodes.filter { it !is MathNode.Cursor }
        if (filtered.isEmpty()) return emptyList()

        val result = mutableListOf<MathNode>()
        var i = 0
        while (i < filtered.size) {
            val curr = filtered[i]
            if (curr is MathNode.Text && isMergeable(curr.text)) {
                val sb = StringBuilder(curr.text)
                var j = i + 1
                while (j < filtered.size) {
                    val next = filtered[j]
                    if (next is MathNode.Text && isMergeable(next.text) && isCompatible(sb.toString(), next.text)) {
                        sb.append(next.text)
                        j++
                    } else {
                        break
                    }
                }
                result.add(MathNode.Text(sb.toString(), isItalic = curr.isItalic, isBold = curr.isBold))
                i = j
            } else {
                result.add(curr)
                i++
            }
        }
        return result
    }

    private fun isMergeable(s: String): Boolean {
        if (s.isEmpty()) return false
        return s.all { it.isDigit() || it == '.' } || s.all { it.isLetter() }
    }

    private fun isCompatible(s1: String, s2: String): Boolean {
        val num1 = s1.all { it.isDigit() || it == '.' }
        val num2 = s2.all { it.isDigit() || it == '.' }
        if (num1 && num2) return true
        val alpha1 = s1.all { it.isLetter() }
        val alpha2 = s2.all { it.isLetter() }
        if (alpha1 && alpha2) return true
        return false
    }

    private fun isIntegerText(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.toLongOrNull() != null
    }

    private fun isDerivativeFraction(fraction: MathNode.Fraction): Boolean {
        val numText = (fraction.numerator as? MathNode.Text)?.text?.trim()
        val denText = (fraction.denominator as? MathNode.Text)?.text?.trim()
        return numText == "d" && denText == "dx"
    }

    // Helper parser to handle rows of MathNodes with priority, parentheses, and implicit multiplication
    private fun parseRowToExpr(nodes: List<MathNode>): Expr? {
        val mergedNodes = combineAdjacentNodes(nodes)
        val exprTokens = mutableListOf<Any>() // Can be Expr or Operator string
        var i = 0
        while (i < mergedNodes.size) {
            val node = mergedNodes[i]

            // Check for mixed fraction pattern: Integer followed by Fraction (e.g., 1 \frac{1}{2} or 1 1/2)
            if (node is MathNode.Text && isIntegerText(node.text) && i + 1 < mergedNodes.size) {
                val nextNode = mergedNodes[i + 1]
                if (nextNode is MathNode.Fraction && !isDerivativeFraction(nextNode)) {
                    val wholeExpr = nodeToExpr(node)
                    val fracExpr = nodeToExpr(nextNode)
                    if (wholeExpr is Expr.Num && fracExpr != null) {
                        val op = if (wholeExpr.value < 0) "-" else "+"
                        exprTokens.add("(")
                        exprTokens.add(wholeExpr)
                        exprTokens.add(op)
                        exprTokens.add(fracExpr)
                        exprTokens.add(")")
                        i += 2
                        continue
                    }
                } else if (nextNode is MathNode.Text && isIntegerText(nextNode.text) && i + 3 < mergedNodes.size) {
                    val opNode = mergedNodes[i + 2]
                    val denNode = mergedNodes[i + 3]
                    if (opNode is MathNode.Operator && (opNode.op == "/" || opNode.op == "÷")) {
                        val wholeExpr = nodeToExpr(node)
                        val numExpr = nodeToExpr(nextNode)
                        val denExpr = nodeToExpr(denNode)
                        if (wholeExpr is Expr.Num && numExpr != null && denExpr != null) {
                            val op = if (wholeExpr.value < 0) "-" else "+"
                            exprTokens.add("(")
                            exprTokens.add(wholeExpr)
                            exprTokens.add(op)
                            exprTokens.add(numExpr)
                            exprTokens.add("/")
                            exprTokens.add(denExpr)
                            exprTokens.add(")")
                            i += 4
                            continue
                        }
                    }
                }
            }

            when (node) {
                is MathNode.Operator -> {
                    val op = if (node.op == "%" || node.op == "mod" || node.op.equals("rem", ignoreCase = true)) "%" else node.op
                    exprTokens.add(op)
                    i++
                }
                is MathNode.Text -> {
                    val txt = node.text.trim()
                    if (txt == "%" || txt.equals("mod", ignoreCase = true) || txt.equals("rem", ignoreCase = true)) {
                        exprTokens.add("%")
                        i++
                    } else if (txt in listOf("sin", "cos", "tan", "arcsin", "arccos", "arctan", "asin", "acos", "atan", "log", "ln", "exp", "sinh", "cosh", "tanh", "cot", "sec", "csc", "abs", "floor", "ceil", "cuberoot")) {
                        // Function. Parse its argument
                        if (i + 1 < mergedNodes.size) {
                            val argNode = mergedNodes[i + 1]
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
        // Standard Shunting-yard algorithm to parse tokens with standard operators: +, -, ×, ÷, %, ^, !
        val values = Stack<Expr>()
        val ops = Stack<String>()

        fun precedence(op: String): Int {
            return when (op) {
                "=", "±" -> 1
                "+", "-" -> 2
                "×", "*", "÷", "/", "%", "mod" -> 3
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
            if (op == "%" || op == "mod") {
                if (values.size >= 2) {
                    val right = values.pop()
                    val left = values.pop()
                    values.push(Expr.Mod(left, right))
                } else if (values.isNotEmpty()) {
                    val v = values.pop()
                    values.push(Expr.Div(v, Expr.Num(100.0)))
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
                    "!" -> {
                        // Unary postfix factorial
                        while (ops.isNotEmpty() && ops.peek() != "(" && precedence(ops.peek()) >= 4) {
                            applyOp(ops.pop())
                        }
                        applyOp("!")
                    }
                    "%" -> {
                        // Check if % is followed by a number / operand (binary modulo) or postfix (percent)
                        val hasNextOperand = (i + 1 < tokens.size) && when (val next = tokens[i + 1]) {
                            is Expr -> true
                            is String -> next == "(" || (next == "-" && i + 2 < tokens.size && (tokens[i + 2] is Expr || tokens[i + 2] == "("))
                            else -> false
                        }
                        if (hasNextOperand) {
                            // Binary modulo
                            while (ops.isNotEmpty() && precedence(ops.peek()) >= precedence("%")) {
                                applyOp(ops.pop())
                            }
                            ops.push("%")
                        } else {
                            // Unary postfix percentage: immediately divide preceding expression by 100
                            while (ops.isNotEmpty() && ops.peek() != "(" && precedence(ops.peek()) >= 4) {
                                applyOp(ops.pop())
                            }
                            if (values.isNotEmpty()) {
                                val v = values.pop()
                                values.push(Expr.Div(v, Expr.Num(100.0)))
                            }
                        }
                    }
                    else -> {
                        // Operator
                        // Handle unary minus: if '-' is preceded by an operator or at start
                        if (token == "-" && (i == 0 || (tokens[i - 1] is String && tokens[i - 1] in listOf("+", "-", "×", "*", "÷", "/", "%", "mod", "(", "=")))) {
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
        var inputLaTeX = input.trim()
        while (inputLaTeX.endsWith("=")) {
            inputLaTeX = inputLaTeX.substring(0, inputLaTeX.length - 1).trim()
        }
        if (inputLaTeX.isEmpty()) {
            return SolutionResult(
                type = "calculation",
                inputLaTeX = input.trim(),
                steps = listOf("请输入有效的数学算式。"),
                exactResultLaTeX = "\\text{Error}",
                decimalResult = "Empty input"
            )
        }

        if (inputLaTeX.contains("\n")) {
            val lines = inputLaTeX.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size > 1) {
                return solveSystemOfEquations(lines, inputLaTeX)
            }
        }

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
            val leftTokens = tokens.subList(0, equalsIndex)
            val rightTokens = tokens.subList(equalsIndex + 1, tokens.size)

            val leftLaTeX = MathParser.tokensToLaTeX(leftTokens)
            val rightLaTeX = MathParser.tokensToLaTeX(rightTokens)

            val leftNode = MathParser.parseTokens(leftTokens)
            val rightNode = MathParser.parseTokens(rightTokens)

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

            // Check if equation is of form x = expr or expr = x
            if (leftExpr is Expr.Var && !rightExpr.getVariables().contains(leftExpr.name)) {
                val resVal = try { rightExpr.eval(emptyMap()) } catch(e: Exception) { Double.NaN }
                if (!resVal.isNaN()) {
                    val simRight = simplifySymbolic(rightExpr)
                    val exactRad = trySimplifyExprToRadical(rightExpr)
                    val radical = exprToRadical(rightExpr)
                    val rightLaTeXStr = if (exactRad != null) exactRad else if (radical != null) radical.toLaTeX() else simRight.toLaTeX()
                    val exactStr = "${leftExpr.name} = $rightLaTeXStr"
                    val steps = mutableListOf<String>()
                    steps.add("原方程: $leftLaTeX = $rightLaTeX")
                    steps.add("计算并化简代数根形式:")
                    steps.add(exactStr)
                    steps.add("数值近似值: ${leftExpr.name} \\approx ${String.format(Locale.US, "%.6f", resVal).replace(Regex("\\.?0+$"), "")}")

                    return SolutionResult(
                        type = "equation",
                        inputLaTeX = inputLaTeX,
                        steps = steps,
                        exactResultLaTeX = exactStr,
                        decimalResult = "${leftExpr.name} = ${String.format(Locale.US, "%.6f", resVal).replace(Regex("\\.?0+$"), "")}",
                        rootXValues = listOf(resVal)
                    )
                }
            }
            if (rightExpr is Expr.Var && !leftExpr.getVariables().contains(rightExpr.name)) {
                val resVal = try { leftExpr.eval(emptyMap()) } catch(e: Exception) { Double.NaN }
                if (!resVal.isNaN()) {
                    val simLeft = simplifySymbolic(leftExpr)
                    val exactRad = trySimplifyExprToRadical(leftExpr)
                    val radical = exprToRadical(leftExpr)
                    val leftLaTeXStr = if (exactRad != null) exactRad else if (radical != null) radical.toLaTeX() else simLeft.toLaTeX()
                    val exactStr = "${rightExpr.name} = $leftLaTeXStr"
                    val steps = mutableListOf<String>()
                    steps.add("原方程: $leftLaTeX = $rightLaTeX")
                    steps.add("计算并化简代数根形式:")
                    steps.add(exactStr)
                    steps.add("数值近似值: ${rightExpr.name} \\approx ${String.format(Locale.US, "%.6f", resVal).replace(Regex("\\.?0+$"), "")}")

                    return SolutionResult(
                        type = "equation",
                        inputLaTeX = inputLaTeX,
                        steps = steps,
                        exactResultLaTeX = exactStr,
                        decimalResult = "${rightExpr.name} = ${String.format(Locale.US, "%.6f", resVal).replace(Regex("\\.?0+$"), "")}",
                        rootXValues = listOf(resVal)
                    )
                }
            }

            // Let f(x) = left - right
            // We want to solve f(x) = 0
            val f = Expr.Sub(leftExpr, rightExpr)

            // Try symbolic quadratic/linear coefficient extraction first
            val quadCoeffs = extractQuadraticCoeffs(f, "x")
            if (quadCoeffs != null) {
                val (aE, bE, cE) = quadCoeffs
                val radA = exprToRadical(aE)
                val radB = exprToRadical(bE)
                val radC = exprToRadical(cE)

                if (radA != null && radB != null && radC != null) {
                    if (radA.isZero()) {
                        // Linear equation: radB * x + radC = 0
                        if (radB.isZero()) {
                            return if (radC.isZero()) {
                                SolutionResult(
                                    type = "equation",
                                    inputLaTeX = inputLaTeX,
                                    steps = listOf("方程化简为: 0 = 0", "此方程有无穷多个解。"),
                                    exactResultLaTeX = "x \\in \\mathbb{R}",
                                    decimalResult = "Infinite solutions"
                                )
                            } else {
                                SolutionResult(
                                    type = "equation",
                                    inputLaTeX = inputLaTeX,
                                    steps = listOf("方程化简为: ${radC.toLaTeX()} = 0", "此方程无解。"),
                                    exactResultLaTeX = "\\varnothing",
                                    decimalResult = "No solution",
                                    geometricInterpretation = "在几何上，这代表方程左右两侧的函数图像相互平行，在实数空间内没有任何交点。"
                                )
                            }
                        } else {
                            val solRad = (-radC) / radB
                            if (solRad != null) {
                                val solLaTeX = solRad.toLaTeX()
                                val rootVal = solRad.toDouble()
                                val steps = mutableListOf<String>()
                                steps.add("原方程: $leftLaTeX = $rightLaTeX")
                                steps.add("移项并合并同类项，化简为一次方程:")
                                steps.add("(${radB.toLaTeX()})x + (${radC.toLaTeX()}) = 0")
                                steps.add("解得精确解:")
                                steps.add("x = $solLaTeX")
                                steps.add("数值近似值: x \\approx ${String.format(Locale.US, "%.6f", rootVal).replace(Regex("\\.?0+$"), "")}")

                                return SolutionResult(
                                    type = "equation",
                                    inputLaTeX = inputLaTeX,
                                    steps = steps,
                                    exactResultLaTeX = "x = $solLaTeX",
                                    decimalResult = "x = ${String.format(Locale.US, "%.6f", rootVal).replace(Regex("\\.?0+$"), "")}",
                                    rootXValues = listOf(rootVal),
                                    geometricInterpretation = "在几何上，一元一次方程代表一条直线 \$y = Ax + B\$ 与 x 轴的交点。此直线在实数轴上有且仅有一个交点。"
                                )
                            }
                        }
                    } else {
                        // Quadratic equation: radA * x^2 + radB * x + radC = 0
                        val deltaRad = radB * radB - RadicalExpr.fromLong(4L) * radA * radC
                        val steps = mutableListOf<String>()
                        steps.add("原方程: $leftLaTeX = $rightLaTeX")
                        steps.add("移项并合并同类项，化简为二次方程:")
                        steps.add("(${radA.toLaTeX()})x^2 + (${radB.toLaTeX()})x + (${radC.toLaTeX()}) = 0")
                        steps.add("计算判别式 $\\Delta = b^2 - 4ac$:")
                        steps.add("\\Delta = (${radB.toLaTeX()})^2 - 4 \\times (${radA.toLaTeX()}) \\times (${radC.toLaTeX()})")
                        steps.add("\\Delta = ${deltaRad.toLaTeX()}")

                        val deltaVal = deltaRad.toDouble()
                        if (deltaVal < 0) {
                            steps.add("因为 $\\Delta < 0$，该方程在实数范围内无解。")
                            return SolutionResult(
                                type = "equation",
                                inputLaTeX = inputLaTeX,
                                steps = steps,
                                exactResultLaTeX = "\\varnothing",
                                decimalResult = "No real roots",
                                geometricInterpretation = "在几何上，当判别式 \$\\Delta < 0\$ 时，对应的抛物线 \$y = Ax^2 + Bx + C\$ 与 x 轴无任何实数交点。"
                            )
                        } else if (abs(deltaVal) < 1e-9) {
                            val solRad = (-radB) / (RadicalExpr.fromLong(2L) * radA)
                            val solLaTeX = solRad?.toLaTeX() ?: "0"
                            val rootVal = solRad?.toDouble() ?: 0.0
                            steps.add("因为 $\\Delta = 0$，方程有且只有一个重根:")
                            steps.add("x = $solLaTeX")
                            steps.add("数值近似值: x \\approx ${String.format(Locale.US, "%.6f", rootVal).replace(Regex("\\.?0+$"), "")}")

                            return SolutionResult(
                                type = "equation",
                                inputLaTeX = inputLaTeX,
                                steps = steps,
                                exactResultLaTeX = "x = $solLaTeX",
                                decimalResult = "x = ${String.format(Locale.US, "%.6f", rootVal).replace(Regex("\\.?0+$"), "")}",
                                rootXValues = listOf(rootVal),
                                geometricInterpretation = "在几何上，一元二次方程代表一条抛物线与 x 轴相切。"
                            )
                        } else {
                            val sqrtDeltaRad = deltaRad.sqrt()
                            val twoRadA = RadicalExpr.fromLong(2L) * radA
                            if (sqrtDeltaRad != null) {
                                val r1Rad = (-radB + sqrtDeltaRad) / twoRadA
                                val r2Rad = (-radB - sqrtDeltaRad) / twoRadA
                                if (r1Rad != null && r2Rad != null) {
                                    val r1Val = r1Rad.toDouble()
                                    val r2Val = r2Rad.toDouble()
                                    val exactStr = if (radB.isZero() && r1Rad == -r2Rad) {
                                        "x = \\pm ${r1Rad.toLaTeX()}"
                                    } else {
                                        "x_1 = ${r1Rad.toLaTeX()}, \\quad x_2 = ${r2Rad.toLaTeX()}"
                                    }
                                    steps.add("因为 $\\Delta > 0$，方程有两个不同的实根:")
                                    steps.add(exactStr)
                                    steps.add("数值近似值: x_1 \\approx ${String.format(Locale.US, "%.6f", r1Val).replace(Regex("\\.?0+$"), "")}, \\quad x_2 \\approx ${String.format(Locale.US, "%.6f", r2Val).replace(Regex("\\.?0+$"), "")}")

                                    return SolutionResult(
                                        type = "equation",
                                        inputLaTeX = inputLaTeX,
                                        steps = steps,
                                        exactResultLaTeX = exactStr,
                                        decimalResult = "x_1 = ${String.format(Locale.US, "%.6f", r1Val)}\nx_2 = ${String.format(Locale.US, "%.6f", r2Val)}",
                                        rootXValues = listOf(r1Val, r2Val),
                                        geometricInterpretation = "在几何上，一元二次方程代表抛物线与 x 轴有两个不同的实数交点。"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Analyze coefficients of f(x) safely
            var isLinear = false
            var isQuadratic = false
            var A = 0.0; var B = 0.0; var C = 0.0

            try {
                val f0 = f.eval(mapOf("x" to 0.0))
                val f1 = f.eval(mapOf("x" to 1.0))
                val fn1 = f.eval(mapOf("x" to -1.0))

                C = f0
                B = (f1 - fn1) / 2.0
                A = (f1 + fn1) / 2.0 - f0

                val f2 = f.eval(mapOf("x" to 2.0))
                val fn2 = f.eval(mapOf("x" to -2.0))

                val expectedF2 = A * 4.0 + B * 2.0 + C
                val expectedFn2 = A * 4.0 - B * 2.0 + C

                isQuadratic = abs(f2 - expectedF2) < 1e-7 && abs(fn2 - expectedFn2) < 1e-7
                isLinear = isQuadratic && abs(A) < 1e-7
            } catch (e: Exception) {
                isLinear = false
                isQuadratic = false
            }

            try {
                if (isLinear) {
                    var exactStrFromLinear: String? = null
                    var rootVal = -C / B
                    val linearCoeffs = extractLinearCoeffs(f, "x")
                    if (linearCoeffs != null) {
                        val (coeffX, constTerm) = linearCoeffs
                        val coeffVal = try { coeffX.eval(emptyMap()) } catch(e: Exception) { B }
                        if (abs(coeffVal) > 1e-9) {
                            val solExpr = Expr.Div(Expr.Neg(constTerm), coeffX)
                            val rootRadical = exprToRadical(solExpr)
                            try { rootVal = solExpr.eval(emptyMap()) } catch(e: Exception) {}
                            if (rootRadical != null) {
                                exactStrFromLinear = "x = ${rootRadical.toLaTeX()}"
                            } else {
                                val simSol = simplifySymbolic(solExpr)
                                exactStrFromLinear = "x = ${simSol.toLaTeX()}"
                            }
                        }
                    }

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

                    val steps = mutableListOf<String>()
                    steps.add("原方程: $leftLaTeX = $rightLaTeX")
                    steps.add("移项并合并同类项，化简为一次方程:")

                    val frac = doubleToFraction(rootVal)
                    val formattedExact = exactStrFromLinear ?: run {
                        if (frac != null) "x = $frac"
                        else "x = ${String.format(Locale.US, "%.4f", rootVal)}"
                    }
                    steps.add(formattedExact)

                    return SolutionResult(
                        type = "equation",
                        inputLaTeX = inputLaTeX,
                        steps = steps,
                        exactResultLaTeX = formattedExact,
                        decimalResult = "x = ${String.format(Locale.US, "%.4f", rootVal)}",
                        rootXValues = listOf(rootVal),
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

                        var exactStr: String? = null

                        // Attempt symbolic resolution first using RadicalExpr
                        val quadCoeffs = extractQuadraticCoeffs(f, "x")
                        if (quadCoeffs != null) {
                            val (aE, bE, cE) = quadCoeffs
                            val radA = exprToRadical(aE)
                            val radB = exprToRadical(bE)
                            val radC = exprToRadical(cE)
                            if (radA != null && radB != null && radC != null && !radA.isZero()) {
                                val deltaRad = radB * radB - RadicalExpr.fromLong(4L) * radA * radC
                                val sqrtDeltaRad = deltaRad.sqrt()
                                if (sqrtDeltaRad != null) {
                                    val twoRadA = RadicalExpr.fromLong(2L) * radA
                                    val r1Rad = (-radB + sqrtDeltaRad) / twoRadA
                                    val r2Rad = (-radB - sqrtDeltaRad) / twoRadA
                                    if (r1Rad != null && r2Rad != null) {
                                        if (radB.isZero() && r1Rad == -r2Rad) {
                                            exactStr = "x = \\pm ${r1Rad.toLaTeX()}"
                                        } else {
                                            exactStr = "x_1 = ${r1Rad.toLaTeX()}, \\quad x_2 = ${r2Rad.toLaTeX()}"
                                        }
                                    }
                                }
                            }
                        }

                        if (exactStr == null) {
                            val fA = doubleToFraction(A)
                            val fB = doubleToFraction(B)
                            val fC = doubleToFraction(C)
                            val (aL, bL, cL) = if (fA != null && fB != null && fC != null) {
                                val denLcm = lcm(fA.den, lcm(fB.den, fC.den))
                                Triple(
                                    fA.num * (denLcm / fA.den),
                                    fB.num * (denLcm / fB.den),
                                    fC.num * (denLcm / fC.den)
                                )
                            } else {
                                val aL = A.roundToLong()
                                val bL = B.roundToLong()
                                val cL = C.roundToLong()
                                Triple(aL, bL, cL)
                            }

                            val discL = bL * bL - 4 * aL * cL
                            if (discL > 0) {
                                val (outSq, inSq) = simplifySqrt(discL)
                                if (inSq == 1L) {
                                    val root1Frac = doubleToFraction(root1)
                                    val root2Frac = doubleToFraction(root2)
                                    exactStr = "x_1 = ${root1Frac ?: formatVal(root1)}, \\quad x_2 = ${root2Frac ?: formatVal(root2)}"
                                } else {
                                    val twoA = 2 * aL
                                    val negB = -bL
                                    var g = gcd(abs(negB), gcd(outSq, abs(twoA)))
                                    if (g == 0L) g = 1L

                                    var simNegB = negB / g
                                    var simOutSq = outSq / g
                                    var simDen = twoA / g
                                    if (simDen < 0) {
                                        simNegB = -simNegB
                                        simOutSq = -simOutSq
                                        simDen = -simDen
                                    }

                                    val outStr = if (abs(simOutSq) == 1L) "" else "${abs(simOutSq)}"
                                    val numStr = if (simNegB == 0L) {
                                        "\\pm $outStr\\sqrt{$inSq}"
                                    } else {
                                        "$simNegB \\pm $outStr\\sqrt{$inSq}"
                                    }

                                    exactStr = if (simDen == 1L) {
                                        "x = $numStr"
                                    } else {
                                        "x = \\frac{$numStr}{$simDen}"
                                    }
                                }
                            }
                        }

                        if (exactStr == null) {
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
                        val exactLaTeX = roots.mapIndexed { idx, r ->
                            val frac = doubleToFraction(r)
                            val rStr = frac?.toLaTeX() ?: "\\approx ${String.format(Locale.US, "%.4f", r)}"
                            if (roots.size > 1) "x_${idx + 1} = $rStr" else "x = $rStr"
                        }.joinToString(", \\quad ")
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
                val simplifiedExpr = simplifySymbolic(expr)
                val steps = mutableListOf<String>()
                steps.add("输入表达式: $inputLaTeX")

                val radicalSimp = trySimplifyExprToRadical(expr)
                val radical = exprToRadical(expr)
                val exactStr = if (radicalSimp != null) {
                    radicalSimp
                } else if (radical != null) {
                    radical.toLaTeX()
                } else {
                    simplifiedExpr.toLaTeX()
                }

                var resVal = try { simplifiedExpr.eval(emptyMap()) } catch (e: Exception) { Double.NaN }
                if (resVal.isNaN()) {
                    resVal = try { expr.eval(emptyMap()) } catch (e: Exception) { Double.NaN }
                }

                val decStr = if (!resVal.isNaN() && !resVal.isInfinite()) {
                    String.format(Locale.US, "%.6f", resVal).replace(Regex("\\.?0+$"), "")
                } else {
                    formatComplexDecimal(simplifiedExpr)
                }

                steps.add("化简为最简形式 (精确解):")
                steps.add(exactStr)
                if (decStr.isNotEmpty() && decStr != exactStr) {
                    steps.add("数值近似值:")
                    steps.add("\\approx $decStr")
                }

                SolutionResult(
                    type = "calculation",
                    inputLaTeX = inputLaTeX,
                    steps = steps,
                    exactResultLaTeX = exactStr,
                    decimalResult = decStr
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

    private fun extractLinearCoeffs2Vars(expr: Expr, v1: String, v2: String): Triple<Double, Double, Double>? {
        return try {
            val f00 = expr.eval(mapOf(v1 to 0.0, v2 to 0.0))
            val f10 = expr.eval(mapOf(v1 to 1.0, v2 to 0.0))
            val f01 = expr.eval(mapOf(v1 to 0.0, v2 to 1.0))

            val k = f00
            val a = f10 - f00
            val b = f01 - f00

            val f11 = expr.eval(mapOf(v1 to 1.0, v2 to 1.0))
            val expectedF11 = a + b + k
            if (abs(f11 - expectedF11) > 1e-7) return null

            Triple(a, b, k)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractLinearCoeffs3Vars(expr: Expr, v1: String, v2: String, v3: String): Quadruple<Double, Double, Double, Double>? {
        return try {
            val f000 = expr.eval(mapOf(v1 to 0.0, v2 to 0.0, v3 to 0.0))
            val f100 = expr.eval(mapOf(v1 to 1.0, v2 to 0.0, v3 to 0.0))
            val f010 = expr.eval(mapOf(v1 to 0.0, v2 to 1.0, v3 to 0.0))
            val f001 = expr.eval(mapOf(v1 to 0.0, v2 to 0.0, v3 to 1.0))

            val k = f000
            val a = f100 - f000
            val b = f010 - f000
            val m = f001 - f000

            Quadruple(a, b, m, k)
        } catch (e: Exception) {
            null
        }
    }

    private fun solveSystemOfEquations(lines: List<String>, inputLaTeX: String): SolutionResult {
        val steps = mutableListOf<String>()
        steps.add("收到连立方程组:")
        val braceLaTeX = "\\begin{cases} " + lines.joinToString(" \\\\ ") + " \\end{cases}"
        steps.add(braceLaTeX)

        val eqExprs = mutableListOf<Expr>()

        for (line in lines) {
            val tokens = MathParser.tokenize(line)
            val equalsIdx = tokens.indexOfFirst { it.type == TokenType.OPERATOR && it.value == "=" }
            if (equalsIdx != -1) {
                val leftTokens = tokens.subList(0, equalsIdx)
                val rightTokens = tokens.subList(equalsIdx + 1, tokens.size)
                val leftNode = MathParser.parseTokens(leftTokens)
                val rightNode = MathParser.parseTokens(rightTokens)
                val leftExpr = nodeToExpr(leftNode)
                val rightExpr = nodeToExpr(rightNode)
                if (leftExpr != null && rightExpr != null) {
                    eqExprs.add(Expr.Sub(leftExpr, rightExpr))
                }
            } else {
                val rootNode = MathParser.parse(line)
                val expr = nodeToExpr(rootNode)
                if (expr != null) {
                    eqExprs.add(expr)
                }
            }
        }

        if (eqExprs.size != lines.size) {
            val lineResults = lines.map { solve(it) }
            val combinedExact = "\\begin{cases} " + lineResults.joinToString(" \\\\ ") { it.exactResultLaTeX.removePrefix("x = ").removePrefix("y = ") } + " \\end{cases}"
            val combinedDec = lineResults.mapIndexed { idx, res -> "算式 ${idx + 1}: ${res.decimalResult}" }.joinToString("\n")
            return SolutionResult(
                type = "equation",
                inputLaTeX = inputLaTeX,
                steps = listOf("逐行计算结果:"),
                exactResultLaTeX = combinedExact,
                decimalResult = combinedDec
            )
        }

        val allVars = eqExprs.flatMap { it.getVariables() }.toSet() - setOf("e", "pi", "π")

        if (allVars.size == 2 && eqExprs.size >= 2) {
            val varList = allVars.toList().sorted()
            val v1 = varList[0]
            val v2 = varList[1]

            val c1 = extractLinearCoeffs2Vars(eqExprs[0], v1, v2)
            val c2 = extractLinearCoeffs2Vars(eqExprs[1], v1, v2)

            if (c1 != null && c2 != null) {
                val (a1, b1, k1) = c1
                val (a2, b2, k2) = c2

                val D = a1 * b2 - a2 * b1
                val Dx = (-k1) * b2 - (-k2) * b1
                val Dy = a1 * (-k2) - a2 * (-k1)

                steps.add("检测到包含未知数 \$$v1, $v2\$ 的二元一次方程组。")
                steps.add("化简为标准方程组形式:")
                steps.add("${formatCoef(a1)}$v1 + ${formatCoef(b1)}$v2 = ${formatVal(-k1)}")
                steps.add("${formatCoef(a2)}$v1 + ${formatCoef(b2)}$v2 = ${formatVal(-k2)}")

                if (abs(D) < 1e-9) {
                    if (abs(Dx) < 1e-9 && abs(Dy) < 1e-9) {
                        steps.add("计算行列式 \$D = 0\$，且 \$D_$v1 = 0, D_$v2 = 0\$，此方程组有无穷多个解。")
                        return SolutionResult(
                            type = "equation",
                            inputLaTeX = inputLaTeX,
                            steps = steps,
                            exactResultLaTeX = "\\begin{cases} $v1, $v2 \\in \\mathbb{R} \\end{cases}",
                            decimalResult = "Infinite solutions"
                        )
                    } else {
                        steps.add("计算行列式 \$D = 0\$，但 \$D_$v1 \\neq 0\$ 或 \$D_$v2 \\neq 0\$，此方程组无解。")
                        return SolutionResult(
                            type = "equation",
                            inputLaTeX = inputLaTeX,
                            steps = steps,
                            exactResultLaTeX = "\\varnothing",
                            decimalResult = "No solution",
                            geometricInterpretation = "在几何上，代表平面直角坐标系中的两条直线相互平行，无交点。"
                        )
                    }
                } else {
                    val res1 = Dx / D
                    val res2 = Dy / D

                    val frac1 = doubleToFraction(res1)
                    val frac2 = doubleToFraction(res2)

                    val str1 = frac1?.toLaTeX() ?: formatVal(res1)
                    val str2 = frac2?.toLaTeX() ?: formatVal(res2)

                    steps.add("使用克拉默法则 (Cramer's Rule) 消元解得:")
                    steps.add("D = ${formatVal(D)}, \\quad D_$v1 = ${formatVal(Dx)}, \\quad D_$v2 = ${formatVal(Dy)}")

                    val exactResult = "\\begin{cases} $v1 = $str1 \\\\ $v2 = $str2 \\end{cases}"
                    steps.add("解得连立方程组的精确解:")
                    steps.add(exactResult)

                    val decResult = "$v1 = ${String.format(Locale.US, "%.4f", res1)}\n$v2 = ${String.format(Locale.US, "%.4f", res2)}"

                    return SolutionResult(
                        type = "equation",
                        inputLaTeX = inputLaTeX,
                        steps = steps,
                        exactResultLaTeX = exactResult,
                        decimalResult = decResult,
                        rootXValues = listOf(res1, res2),
                        geometricInterpretation = "在几何上，二元一次方程组代表平面直角坐标系中两条直线的交点。连立求解确定了该唯一公共交点坐标 ($str1, $str2)。"
                    )
                }
            }
        }

        if (allVars.size == 3 && eqExprs.size >= 3) {
            val varList = allVars.toList().sorted()
            val v1 = varList[0]; val v2 = varList[1]; val v3 = varList[2]

            val c1 = extractLinearCoeffs3Vars(eqExprs[0], v1, v2, v3)
            val c2 = extractLinearCoeffs3Vars(eqExprs[1], v1, v2, v3)
            val c3 = extractLinearCoeffs3Vars(eqExprs[2], v1, v2, v3)

            if (c1 != null && c2 != null && c3 != null) {
                val (a1, b1, m1, k1) = c1
                val (a2, b2, m2, k2) = c2
                val (a3, b3, m3, k3) = c3

                val matA = listOf(
                    listOf(a1, b1, m1),
                    listOf(a2, b2, m2),
                    listOf(a3, b3, m3)
                )
                val detA = det3x3(matA)

                if (abs(detA) > 1e-9) {
                    val matX = listOf(listOf(-k1, b1, m1), listOf(-k2, b2, m2), listOf(-k3, b3, m3))
                    val matY = listOf(listOf(a1, -k1, m1), listOf(a2, -k2, m2), listOf(a3, -k3, m3))
                    val matZ = listOf(listOf(a1, b1, -k1), listOf(a2, b2, -k2), listOf(a3, b3, -k3))

                    val res1 = det3x3(matX) / detA
                    val res2 = det3x3(matY) / detA
                    val res3 = det3x3(matZ) / detA

                    val frac1 = doubleToFraction(res1)
                    val frac2 = doubleToFraction(res2)
                    val frac3 = doubleToFraction(res3)

                    val str1 = frac1?.toLaTeX() ?: formatVal(res1)
                    val str2 = frac2?.toLaTeX() ?: formatVal(res2)
                    val str3 = frac3?.toLaTeX() ?: formatVal(res3)

                    val exactResult = "\\begin{cases} $v1 = $str1 \\\\ $v2 = $str2 \\\\ $v3 = $str3 \\end{cases}"
                    steps.add("使用三阶行列式消元解得三元一次方程组:")
                    steps.add(exactResult)

                    return SolutionResult(
                        type = "equation",
                        inputLaTeX = inputLaTeX,
                        steps = steps,
                        exactResultLaTeX = exactResult,
                        decimalResult = "$v1 = ${String.format(Locale.US, "%.4f", res1)}\n$v2 = ${String.format(Locale.US, "%.4f", res2)}\n$v3 = ${String.format(Locale.US, "%.4f", res3)}",
                        geometricInterpretation = "在三维空间中，三元一次方程组代表三个平面的公共交点。"
                    )
                }
            }
        }

        val lineResults = lines.map { solve(it) }
        val combinedExact = "\\begin{cases} " + lineResults.joinToString(" \\\\ ") { it.exactResultLaTeX.removePrefix("x = ").removePrefix("y = ") } + " \\end{cases}"
        val combinedDec = lineResults.mapIndexed { idx, res -> "算式 ${idx + 1}: ${res.decimalResult}" }.joinToString("\n")

        steps.add("分别对连立算式进行逐行计算并组合求解结果:")
        lineResults.forEachIndexed { idx, res ->
            steps.add("算式 ${idx + 1}: ${lines[idx]} \\implies ${res.exactResultLaTeX}")
        }

        return SolutionResult(
            type = "equation",
            inputLaTeX = inputLaTeX,
            steps = steps,
            exactResultLaTeX = combinedExact,
            decimalResult = combinedDec
        )
    }

    data class IrrationalItem(
        val symbol: String,
        val displayName: String,
        val latexSymbol: String,
        val exactValue: Double
    ) : java.io.Serializable

    fun getRoundedValue(exactValue: Double, level: Int): Double {
        if (level <= 0) return exactValue
        val decimals = when (level) {
            1 -> 0
            2 -> 1
            3 -> 2
            4 -> 3
            5 -> 4
            6 -> 6
            else -> 0
        }
        val factor = 10.0.pow(decimals)
        return round(exactValue * factor) / factor
    }

    fun getPrecisionLabel(level: Int): String {
        return when (level) {
            0 -> "不取整 (精确值)"
            1 -> "0位小数 (取整)"
            2 -> "1位小数"
            3 -> "2位小数"
            4 -> "3位小数"
            5 -> "4位小数"
            6 -> "6位小数"
            else -> "不取整"
        }
    }

    fun formatSubstitutedValueDisplay(item: IrrationalItem, level: Int): String {
        if (level <= 0) {
            return item.displayName.substringBefore(" ")
        }
        val rounded = getRoundedValue(item.exactValue, level)
        val decimals = when (level) {
            1 -> 0
            2 -> 1
            3 -> 2
            4 -> 3
            5 -> 4
            6 -> 6
            else -> 0
        }
        return if (decimals == 0) {
            "≈ ${rounded.roundToLong()}"
        } else {
            String.format(Locale.US, "≈ %.$decimals" + "f", rounded)
        }
    }

    fun extractIrrationalItems(result: SolutionResult): List<IrrationalItem> {
        val items = mutableListOf<IrrationalItem>()
        val combined = "${result.exactResultLaTeX} ${result.inputLaTeX}"

        // 1. Pi
        if (combined.contains("\\pi") || combined.contains("π") || Regex("""\bpi\b""").containsMatchIn(combined)) {
            items.add(
                IrrationalItem(
                    symbol = "\\pi",
                    displayName = "π (圆周率)",
                    latexSymbol = "\\pi",
                    exactValue = Math.PI
                )
            )
        }

        // 2. Natural constant e
        val eRegex = Regex("""(?<![a-zA-Z\\])e(?![a-zA-Z])""")
        if (eRegex.containsMatchIn(result.exactResultLaTeX)) {
            items.add(
                IrrationalItem(
                    symbol = "e",
                    displayName = "e (自然底数)",
                    latexSymbol = "e",
                    exactValue = Math.E
                )
            )
        }

        // 3. Square/cube roots \sqrt{N} or \sqrt[k]{N}
        val sqrtRegex = Regex("""\\sqrt(?:\[(\d+)\])?\{(\d+(?:\.\d+)?)\}""")
        sqrtRegex.findAll(result.exactResultLaTeX).forEach { match ->
            val kStr = match.groupValues[1]
            val nStr = match.groupValues[2]
            val nVal = nStr.toDoubleOrNull()
            if (nVal != null && nVal > 0) {
                val kVal = kStr.toDoubleOrNull() ?: 2.0
                val rootVal = nVal.pow(1.0 / kVal)
                val nearestInt = round(rootVal)
                if (abs(rootVal - nearestInt) > 1e-6) {
                    val symbol = match.value
                    val display = if (kVal == 2.0) "√$nStr" else "^{$kStr}√$nStr"
                    items.add(
                        IrrationalItem(
                            symbol = symbol,
                            displayName = display,
                            latexSymbol = symbol,
                            exactValue = rootVal
                        )
                    )
                }
            }
        }

        return items.distinctBy { it.symbol }
    }

    fun computeDecimalWithPrecisions(
        result: SolutionResult,
        items: List<IrrationalItem>,
        precisions: Map<String, Int>
    ): String {
        if (items.isEmpty() || precisions.values.all { it <= 0 }) {
            return result.decimalResult
        }

        return try {
            val activeLevels = items.mapNotNull { precisions[it.symbol] }.filter { it > 0 }
            if (activeLevels.isEmpty()) return result.decimalResult

            val maxLevel = activeLevels.maxOrNull() ?: 0
            val targetDecimals = when (maxLevel) {
                1 -> 0
                2 -> 1
                3 -> 2
                4 -> 3
                5 -> 4
                6 -> 6
                else -> 2
            }

            fun formatNum(value: Double): String {
                return if (targetDecimals == 0) {
                    round(value).toLong().toString()
                } else {
                    String.format(Locale.US, "%.${targetDecimals}f", value)
                }
            }

            val replacements = mutableMapOf<String, Double>()
            for (item in items) {
                val level = precisions[item.symbol] ?: 0
                val effVal = if (level > 0) getRoundedValue(item.exactValue, level) else item.exactValue
                replacements[item.symbol] = effVal
            }

            fun evalLatexPart(part: String): String {
                var prefix = ""
                var exprStr = part.trim()

                if (exprStr.contains("=")) {
                    val p = exprStr.split("=", limit = 2)
                    prefix = p[0].trim() + " \\approx "
                    exprStr = p[1].trim()
                }

                if (exprStr.contains("\\pm")) {
                    val pPlus = exprStr.replace("\\pm", "+")
                    val pMinus = exprStr.replace("\\pm", "-")
                    val vPlus = evalSingleLatex(pPlus, replacements)
                    val vMinus = evalSingleLatex(pMinus, replacements)
                    if (vPlus != null && vMinus != null) {
                        return if (abs(vPlus + vMinus) < 1e-9 && abs(vPlus) > 1e-9) {
                            "${prefix}\\pm ${formatNum(abs(vPlus))}"
                        } else if (abs(vPlus - vMinus) < 1e-9) {
                            "${prefix}${formatNum(vPlus)}"
                        } else {
                            if (prefix.isNotEmpty()) {
                                val varName = prefix.substringBefore("\\approx").trim()
                                "${varName}_1 \\approx ${formatNum(vPlus)}, \\quad ${varName}_2 \\approx ${formatNum(vMinus)}"
                            } else {
                                "${formatNum(vPlus)}, \\quad ${formatNum(vMinus)}"
                            }
                        }
                    }
                }

                val resVal = evalSingleLatex(exprStr, replacements)
                if (resVal != null) {
                    return "$prefix${formatNum(resVal)}"
                }

                return part
            }

            val exactLaTeX = result.exactResultLaTeX
            val parts = exactLaTeX.split(Regex("""\\quad|;|\n|\\\\"""))
            val evaluatedParts = parts.map { part ->
                val trimmed = part.trim().trimEnd(',')
                if (trimmed.isEmpty()) "" else evalLatexPart(trimmed)
            }.filter { it.isNotEmpty() }

            if (evaluatedParts.isNotEmpty()) {
                evaluatedParts.joinToString(", \\quad ")
            } else {
                result.decimalResult
            }
        } catch (e: Throwable) {
            result.decimalResult
        }
    }

    private fun evalSingleLatex(latexStr: String, replacements: Map<String, Double>): Double? {
        return try {
            var cleaned = latexStr.trim()
            for ((sym, valDouble) in replacements) {
                cleaned = cleaned.replace(sym, " ($valDouble) ")
                if (sym == "\\pi") {
                    cleaned = cleaned.replace("π", " ($valDouble) ")
                    cleaned = cleaned.replace("pi", " ($valDouble) ")
                }
            }
            cleaned = cleaned.replace(Regex("""\\text\{([^}]+)\}"""), "$1")
            while (cleaned.contains("\\frac")) {
                val next = cleaned.replace(Regex("""\\frac\{([^}]+)\}\{([^}]+)\}"""), "(($1)/($2))")
                if (next == cleaned) break
                cleaned = next
            }
            cleaned = cleaned.replace("\\cdot", "*").replace("\\times", "*")
            cleaned = cleaned.replace("\\left(", "(").replace("\\right)", ")")
            cleaned = cleaned.replace("\\left", "").replace("\\right", "")

            val node = MathParser.parse(cleaned)
            val expr = nodeToExpr(node) ?: return null
            val res = expr.eval(emptyMap())
            if (res.isNaN() || res.isInfinite()) null else res
        } catch (e: Throwable) {
            null
        }
    }
}
