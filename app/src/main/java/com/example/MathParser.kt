package com.example

import android.util.Log

enum class TokenType {
    COMMAND, NUMBER, LETTER, CHAR, OPEN_BRACE, CLOSE_BRACE, OPEN_PAREN, CLOSE_PAREN, OPEN_BRACKET, CLOSE_BRACKET, POWER, SUBSCRIPT, OPERATOR, CURSOR
}

data class Token(
    val type: TokenType,
    val value: String,
    val startIndex: Int = -1,
    val endIndex: Int = -1
) {
    override fun toString(): String = "$type($value)"
}

object MathParser {
    private const val TAG = "MathParser"

    fun tokenize(input: String, cursorPosition: Int? = null): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        var insertedCursor = false

        fun checkAndInsertCursor() {
            if (cursorPosition != null && !insertedCursor && i >= cursorPosition) {
                tokens.add(Token(TokenType.CURSOR, "|", cursorPosition, cursorPosition))
                insertedCursor = true
            }
        }

        while (i < input.length) {
            checkAndInsertCursor()
            val startTokenIdx = i
            val c = input[i]
            when {
                c.isWhitespace() -> {
                    i++
                }
                c == '\\' -> {
                    if (i + 1 < input.length && input[i + 1] == '\\') {
                        tokens.add(Token(TokenType.OPERATOR, "\\\\", startTokenIdx, startTokenIdx + 2))
                        i += 2
                    } else {
                        val sb = StringBuilder()
                        i++ // skip '\\'
                        while (i < input.length && (input[i].isLetter() || input[i] == '*')) {
                            sb.append(input[i])
                            i++
                        }
                        val cmd = sb.toString()
                        if (cmd.isNotEmpty()) {
                            tokens.add(Token(TokenType.COMMAND, cmd, startTokenIdx, i))
                        }
                    }
                }
                c.isDigit() -> {
                    tokens.add(Token(TokenType.NUMBER, c.toString(), startTokenIdx, startTokenIdx + 1))
                    i++
                }
                c == '{' -> {
                    tokens.add(Token(TokenType.OPEN_BRACE, "{", startTokenIdx, startTokenIdx + 1))
                    i++
                }
                c == '}' -> {
                    tokens.add(Token(TokenType.CLOSE_BRACE, "}", startTokenIdx, startTokenIdx + 1))
                    i++
                }
                c == '(' -> {
                    tokens.add(Token(TokenType.OPEN_PAREN, "(", startTokenIdx, startTokenIdx + 1))
                    i++
                }
                c == ')' -> {
                    tokens.add(Token(TokenType.CLOSE_PAREN, ")", startTokenIdx, startTokenIdx + 1))
                    i++
                }
                c == '[' -> {
                    tokens.add(Token(TokenType.OPEN_BRACKET, "[", startTokenIdx, startTokenIdx + 1))
                    i++
                }
                c == ']' -> {
                    tokens.add(Token(TokenType.CLOSE_BRACKET, "]", startTokenIdx, startTokenIdx + 1))
                    i++
                }
                c == '^' -> {
                    tokens.add(Token(TokenType.POWER, "^", startTokenIdx, startTokenIdx + 1))
                    i++
                }
                c == '_' -> {
                    tokens.add(Token(TokenType.SUBSCRIPT, "_", startTokenIdx, startTokenIdx + 1))
                    i++
                }
                c in "+-=*/<>&≤≥≠≈≪≫%" || c == '÷' || c == '×' || c == '±' || c == '!' -> {
                    tokens.add(Token(TokenType.OPERATOR, c.toString(), startTokenIdx, startTokenIdx + 1))
                    i++
                }
                c.isLetter() -> {
                    tokens.add(Token(TokenType.LETTER, c.toString(), startTokenIdx, startTokenIdx + 1))
                    i++
                }
                else -> {
                    tokens.add(Token(TokenType.CHAR, c.toString(), startTokenIdx, startTokenIdx + 1))
                    i++
                }
            }
        }
        checkAndInsertCursor()
        return tokens
    }

    fun parse(input: String, cursorPosition: Int? = null): MathNode {
        val tokens = tokenize(input, cursorPosition)
        SafeLog.d(TAG, "Parsing input: $input, tokens: $tokens")
        val (node, _) = parseSubExpression(tokens, 0)
        return node
    }

    fun parseTokens(tokens: List<Token>): MathNode {
        SafeLog.d(TAG, "Parsing tokens: $tokens")
        val (node, _) = parseSubExpression(tokens, 0)
        return node
    }

    fun tokensToLaTeX(tokens: List<Token>): String {
        val sb = StringBuilder()
        for (i in tokens.indices) {
            val t = tokens[i]
            if (t.type == TokenType.CURSOR) continue
            if (t.type == TokenType.COMMAND) {
                sb.append("\\").append(t.value)
                if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.LETTER) {
                    sb.append(" ")
                }
            } else {
                sb.append(t.value)
            }
        }
        return sb.toString()
    }

    private fun parseSubExpression(
        tokens: List<Token>,
        startIndex: Int,
        stopOnBrace: Boolean = false,
        stopOnParen: Boolean = false,
        stopOnBracket: Boolean = false
    ): Pair<MathNode, Int> {
        val children = mutableListOf<MathNode>()
        var i = startIndex

        while (i < tokens.size) {
            val token = tokens[i]

            if (stopOnBrace && token.type == TokenType.CLOSE_BRACE) {
                break
            }
            if (stopOnParen && token.type == TokenType.CLOSE_PAREN) {
                break
            }
            if (stopOnBracket && token.type == TokenType.CLOSE_BRACKET) {
                break
            }

            when (token.type) {
                TokenType.CURSOR -> {
                    children.add(MathNode.Cursor)
                    i++
                }
                TokenType.NUMBER -> {
                    children.add(MathNode.Text(token.value, startIndex = token.startIndex, endIndex = token.endIndex))
                    i++
                }
                TokenType.LETTER -> {
                    children.add(MathNode.Text(token.value, isItalic = true, startIndex = token.startIndex, endIndex = token.endIndex))
                    i++
                }
                TokenType.OPERATOR -> {
                    children.add(MathNode.Operator(token.value, startIndex = token.startIndex, endIndex = token.endIndex))
                    i++
                }
                TokenType.CHAR -> {
                    children.add(MathNode.Text(token.value, startIndex = token.startIndex, endIndex = token.endIndex))
                    i++
                }
                TokenType.OPEN_BRACE -> {
                    val (groupNode, nextIdx) = parseSubExpression(tokens, i + 1, stopOnBrace = true)
                    children.add(groupNode)
                    i = if (nextIdx < tokens.size && tokens[nextIdx].type == TokenType.CLOSE_BRACE) nextIdx + 1 else nextIdx
                }
                TokenType.CLOSE_BRACE -> {
                    // Unmatched closing brace, just skip or treat as text
                    children.add(MathNode.Text("}"))
                    i++
                }
                TokenType.OPEN_PAREN -> {
                    val (groupNode, nextIdx) = parseSubExpression(tokens, i + 1, stopOnParen = true)
                    if (nextIdx < tokens.size && tokens[nextIdx].type == TokenType.CLOSE_PAREN) {
                        children.add(MathNode.Parentheses(groupNode))
                        i = nextIdx + 1
                    } else {
                        children.add(MathNode.Text("("))
                        if (groupNode is MathNode.Row) {
                            children.addAll(groupNode.children)
                        } else if (groupNode !is MathNode.Text || (groupNode as MathNode.Text).text.isNotEmpty()) {
                            children.add(groupNode)
                        }
                        i = nextIdx
                    }
                }
                TokenType.CLOSE_PAREN -> {
                    children.add(MathNode.Text(")"))
                    i++
                }
                TokenType.OPEN_BRACKET -> {
                    val (groupNode, nextIdx) = parseSubExpression(tokens, i + 1, stopOnBracket = true)
                    if (nextIdx < tokens.size && tokens[nextIdx].type == TokenType.CLOSE_BRACKET) {
                        children.add(MathNode.SquareBrackets(groupNode))
                        i = nextIdx + 1
                    } else {
                        children.add(MathNode.Text("["))
                        if (groupNode is MathNode.Row) {
                            children.addAll(groupNode.children)
                        } else if (groupNode !is MathNode.Text || (groupNode as MathNode.Text).text.isNotEmpty()) {
                            children.add(groupNode)
                        }
                        i = nextIdx
                    }
                }
                TokenType.CLOSE_BRACKET -> {
                    children.add(MathNode.Text("]"))
                    i++
                }
                TokenType.COMMAND -> {
                    val cmd = token.value
                    when (cmd) {
                        "frac" -> {
                            // Needs two arguments
                            val (num, next1) = parseArg(tokens, i + 1)
                            val (den, next2) = parseArg(tokens, next1)
                            children.add(MathNode.Fraction(num, den))
                            i = next2
                        }
                        "sqrt", "root" -> {
                            if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.OPEN_BRACKET) {
                                val (indexNode, next1) = parseSubExpression(tokens, i + 2, stopOnBracket = true)
                                val idxAfterBracket = if (next1 < tokens.size && tokens[next1].type == TokenType.CLOSE_BRACKET) next1 + 1 else next1
                                val (content, next2) = parseArg(tokens, idxAfterBracket)
                                children.add(MathNode.Sqrt(content, indexNode))
                                i = next2
                            } else {
                                val (content, nextIdx) = parseArg(tokens, i + 1)
                                children.add(MathNode.Sqrt(content))
                                i = nextIdx
                            }
                        }
                        "quad" -> {
                            children.add(MathNode.Text("  "))
                            i++
                        }
                        "qquad" -> {
                            children.add(MathNode.Text("    "))
                            i++
                        }
                        "text", "mathbb", "mathrm", "mathbf", "mathsf", "mathcal" -> {
                            val (arg, nextIdx) = parseArg(tokens, i + 1)
                            children.add(clearItalic(arg))
                            i = nextIdx
                        }
                        "in" -> {
                            children.add(MathNode.Operator("∈"))
                            i++
                        }
                        "notin" -> {
                            children.add(MathNode.Operator("∉"))
                            i++
                        }
                        "varnothing" -> {
                            children.add(MathNode.SpecialSymbol("∅"))
                            i++
                        }
                        "approx" -> {
                            children.add(MathNode.Operator("≈"))
                            i++
                        }
                        "neq" -> {
                            children.add(MathNode.Operator("≠"))
                            i++
                        }
                        "leq", "le" -> {
                            children.add(MathNode.Operator("≤"))
                            i++
                        }
                        "geq", "ge" -> {
                            children.add(MathNode.Operator("≥"))
                            i++
                        }
                        "ll" -> {
                            children.add(MathNode.Operator("≪"))
                            i++
                        }
                        "gg" -> {
                            children.add(MathNode.Operator("≫"))
                            i++
                        }
                        "Delta" -> {
                            children.add(MathNode.SpecialSymbol("Δ"))
                            i++
                        }
                        "delta" -> {
                            children.add(MathNode.SpecialSymbol("δ"))
                            i++
                        }
                        "to" -> {
                            children.add(MathNode.Operator("→"))
                            i++
                        }
                        "sin", "cos", "tan", "arcsin", "arccos", "arctan", "asin", "acos", "atan", "log", "ln", "abs", "floor", "ceil", "cuberoot", "max", "gcf", "exp", "sinh", "cosh", "tanh", "cot", "sec", "csc" -> {
                            children.add(MathNode.Text(cmd, isItalic = true))
                            i++
                        }
                        "left", "right" -> {
                            i++ // skip LaTeX \left and \right alignment commands
                        }
                        "pm" -> {
                            children.add(MathNode.Operator("±"))
                            i++
                        }
                        "mp" -> {
                            children.add(MathNode.Operator("∓"))
                            i++
                        }
                        "times", "cdot" -> {
                            children.add(MathNode.Operator("×"))
                            i++
                        }
                        "div" -> {
                            children.add(MathNode.Operator("÷"))
                            i++
                        }
                        "bmod", "mod" -> {
                            children.add(MathNode.Operator("%"))
                            i++
                        }
                        "pi" -> {
                            children.add(MathNode.SpecialSymbol("π"))
                            i++
                        }
                        "alpha" -> {
                            children.add(MathNode.SpecialSymbol("α"))
                            i++
                        }
                        "beta" -> {
                            children.add(MathNode.SpecialSymbol("β"))
                            i++
                        }
                        "gamma" -> {
                            children.add(MathNode.SpecialSymbol("γ"))
                            i++
                        }
                        "theta" -> {
                            children.add(MathNode.SpecialSymbol("θ"))
                            i++
                        }
                        "tau" -> {
                            children.add(MathNode.SpecialSymbol("τ"))
                            i++
                        }
                        "mu" -> {
                            children.add(MathNode.SpecialSymbol("μ"))
                            i++
                        }
                        "infty" -> {
                            children.add(MathNode.SpecialSymbol("∞"))
                            i++
                        }
                        "begin" -> {
                            val (envNode, next1) = parseArg(tokens, i + 1)
                            val envName = (envNode as? MathNode.Text)?.text ?: "matrix"
                            
                            var depth = 1
                            val envTokens = mutableListOf<Token>()
                            var j = next1
                            while (j < tokens.size) {
                                val t = tokens[j]
                                if (t.type == TokenType.COMMAND && t.value == "begin") {
                                    depth++
                                } else if (t.type == TokenType.COMMAND && t.value == "end") {
                                    val (endEnvNode, _) = parseArg(tokens, j + 1)
                                    val endEnvName = (endEnvNode as? MathNode.Text)?.text ?: "matrix"
                                    if (endEnvName == envName) {
                                        depth--
                                        if (depth == 0) {
                                            break
                                        }
                                    }
                                }
                                envTokens.add(t)
                                j++
                            }
                            
                            val rows = mutableListOf<List<MathNode>>()
                            var currentCellTokens = mutableListOf<Token>()
                            var currentRowCells = mutableListOf<MathNode>()
                            
                            fun commitCell() {
                                if (currentCellTokens.isNotEmpty()) {
                                    val (cellNode, _) = parseSubExpression(currentCellTokens, 0)
                                    currentRowCells.add(cellNode)
                                    currentCellTokens = mutableListOf()
                                } else {
                                    currentRowCells.add(MathNode.Text(""))
                                }
                            }
                            
                            fun commitRow() {
                                commitCell()
                                if (currentRowCells.isNotEmpty()) {
                                    rows.add(currentRowCells)
                                    currentRowCells = mutableListOf()
                                }
                            }
                            
                            var k = 0
                            while (k < envTokens.size) {
                                val t = envTokens[k]
                                when {
                                    t.type == TokenType.OPERATOR && t.value == "\\\\" -> {
                                        commitRow()
                                    }
                                    t.type == TokenType.OPERATOR && t.value == "&" -> {
                                        commitCell()
                                    }
                                    else -> {
                                        currentCellTokens.add(t)
                                    }
                                }
                                k++
                            }
                            commitRow()
                            
                            children.add(MathNode.Matrix(rows, envName))
                            
                            if (j < tokens.size) {
                                val (_, endNext) = parseArg(tokens, j + 1)
                                i = endNext
                            } else {
                                i = tokens.size
                            }
                        }
                        "int" -> {
                            // Try to parse subscript and superscript for integral
                            var from: MathNode? = null
                            var to: MathNode? = null
                            var currIdx = i + 1
                            while (currIdx < tokens.size && (tokens[currIdx].type == TokenType.SUBSCRIPT || tokens[currIdx].type == TokenType.POWER)) {
                                val isSub = tokens[currIdx].type == TokenType.SUBSCRIPT
                                val (arg, nextIdx) = parseArg(tokens, currIdx + 1)
                                if (isSub) {
                                    from = arg
                                } else {
                                    to = arg
                                }
                                currIdx = nextIdx
                            }
                            children.add(MathNode.Integral(from, to, MathNode.Text("")))
                            i = currIdx
                        }
                        "sum" -> {
                            var from: MathNode? = null
                            var to: MathNode? = null
                            var currIdx = i + 1
                            while (currIdx < tokens.size && (tokens[currIdx].type == TokenType.SUBSCRIPT || tokens[currIdx].type == TokenType.POWER)) {
                                val isSub = tokens[currIdx].type == TokenType.SUBSCRIPT
                                val (arg, nextIdx) = parseArg(tokens, currIdx + 1)
                                if (isSub) {
                                    from = arg
                                } else {
                                    to = arg
                                }
                                currIdx = nextIdx
                            }
                            children.add(MathNode.Sum(from, to, MathNode.Text("")))
                            i = currIdx
                        }
                        "lim" -> {
                            var sub: MathNode? = null
                            var currIdx = i + 1
                            if (currIdx < tokens.size && tokens[currIdx].type == TokenType.SUBSCRIPT) {
                                val (arg, nextIdx) = parseArg(tokens, currIdx + 1)
                                sub = arg
                                currIdx = nextIdx
                            }
                            children.add(MathNode.Limit(sub, null, MathNode.Text("")))
                            i = currIdx
                        }
                        else -> {
                            children.add(MathNode.Text("\\$cmd"))
                            i++
                        }
                    }
                }
                TokenType.POWER -> {
                    val last = if (children.isNotEmpty()) children.removeAt(children.size - 1) else MathNode.Text("")
                    val (exponent, nextIdx) = parseArg(tokens, i + 1)
                    children.add(MathNode.Power(last, exponent))
                    i = nextIdx
                }
                TokenType.SUBSCRIPT -> {
                    val last = if (children.isNotEmpty()) children.removeAt(children.size - 1) else MathNode.Text("")
                    val (subscript, nextIdx) = parseArg(tokens, i + 1)
                    children.add(MathNode.Subscript(last, subscript))
                    i = nextIdx
                }
            }
        }

        val resultNode = when {
            children.isEmpty() -> MathNode.Text("")
            children.size == 1 -> children[0]
            else -> MathNode.Row(children)
        }
        return Pair(resultNode, i)
    }

    private fun parseArg(tokens: List<Token>, startIndex: Int): Pair<MathNode, Int> {
        if (startIndex >= tokens.size) {
            return Pair(MathNode.Text(""), startIndex)
        }

        var curr = startIndex
        var hasLeadingCursor = false
        if (tokens[curr].type == TokenType.CURSOR) {
            hasLeadingCursor = true
            curr++
            if (curr >= tokens.size) {
                return Pair(MathNode.Cursor, curr)
            }
        }

        val token = tokens[curr]
        val (node, nextIdx) = if (token.type == TokenType.OPEN_BRACE) {
            val (subNode, nIdx) = parseSubExpression(tokens, curr + 1, stopOnBrace = true)
            val finalIdx = if (nIdx < tokens.size && tokens[nIdx].type == TokenType.CLOSE_BRACE) nIdx + 1 else nIdx
            Pair(subNode, finalIdx)
        } else {
            val singleNode = when (token.type) {
                TokenType.NUMBER -> MathNode.Text(token.value, startIndex = token.startIndex, endIndex = token.endIndex)
                TokenType.LETTER -> MathNode.Text(token.value, isItalic = true, startIndex = token.startIndex, endIndex = token.endIndex)
                TokenType.OPERATOR -> MathNode.Operator(token.value, startIndex = token.startIndex, endIndex = token.endIndex)
                TokenType.CHAR -> MathNode.Text(token.value, startIndex = token.startIndex, endIndex = token.endIndex)
                TokenType.COMMAND -> {
                    when (token.value) {
                        "pi" -> MathNode.SpecialSymbol("π", startIndex = token.startIndex, endIndex = token.endIndex)
                        "alpha" -> MathNode.SpecialSymbol("α", startIndex = token.startIndex, endIndex = token.endIndex)
                        "beta" -> MathNode.SpecialSymbol("β", startIndex = token.startIndex, endIndex = token.endIndex)
                        "theta" -> MathNode.SpecialSymbol("θ", startIndex = token.startIndex, endIndex = token.endIndex)
                        "gamma" -> MathNode.SpecialSymbol("γ", startIndex = token.startIndex, endIndex = token.endIndex)
                        "tau" -> MathNode.SpecialSymbol("τ", startIndex = token.startIndex, endIndex = token.endIndex)
                        "mu" -> MathNode.SpecialSymbol("μ", startIndex = token.startIndex, endIndex = token.endIndex)
                        "infty" -> MathNode.SpecialSymbol("∞", startIndex = token.startIndex, endIndex = token.endIndex)
                        "Delta" -> MathNode.SpecialSymbol("Δ", startIndex = token.startIndex, endIndex = token.endIndex)
                        "delta" -> MathNode.SpecialSymbol("δ", startIndex = token.startIndex, endIndex = token.endIndex)
                        "varnothing" -> MathNode.SpecialSymbol("∅", startIndex = token.startIndex, endIndex = token.endIndex)
                        else -> MathNode.Text("\\${token.value}", startIndex = token.startIndex, endIndex = token.endIndex)
                    }
                }
                TokenType.CURSOR -> MathNode.Cursor
                else -> MathNode.Text(token.value, startIndex = token.startIndex, endIndex = token.endIndex)
            }
            Pair(singleNode, curr + 1)
        }

        return if (hasLeadingCursor) {
            val combined = when (node) {
                is MathNode.Row -> MathNode.Row(listOf(MathNode.Cursor) + node.children)
                else -> MathNode.Row(listOf(MathNode.Cursor, node))
            }
            Pair(combined, nextIdx)
        } else {
            Pair(node, nextIdx)
        }
    }

    private fun clearItalic(node: MathNode): MathNode = when (node) {
        is MathNode.Text -> node.copy(isItalic = false)
        is MathNode.Row -> MathNode.Row(node.children.map { clearItalic(it) })
        else -> node
    }
}
