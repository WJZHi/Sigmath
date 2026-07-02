package com.example

sealed class MathNode {
    data class Text(val text: String, val isItalic: Boolean = false, val isBold: Boolean = false) : MathNode()
    data class Fraction(val numerator: MathNode, val denominator: MathNode) : MathNode()
    data class Sqrt(val content: MathNode) : MathNode()
    data class Power(val base: MathNode, val exponent: MathNode) : MathNode()
    data class Subscript(val base: MathNode, val subscript: MathNode) : MathNode()
    data class Parentheses(val content: MathNode) : MathNode()
    data class SquareBrackets(val content: MathNode) : MathNode()
    data class Row(val children: List<MathNode>) : MathNode()
    data class Operator(val op: String) : MathNode()
    data class SpecialSymbol(val symbol: String) : MathNode() // e.g. π, α, β, ∞, θ
    data class Integral(val from: MathNode?, val to: MathNode?, val body: MathNode) : MathNode()
    data class Sum(val from: MathNode?, val to: MathNode?, val body: MathNode) : MathNode()
    data class Limit(val variable: MathNode?, val approach: MathNode?, val body: MathNode) : MathNode()
    data class Matrix(val rows: List<List<MathNode>>, val type: String = "pmatrix") : MathNode()
}
