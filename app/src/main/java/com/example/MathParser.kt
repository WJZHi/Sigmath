package com.example

import android.util.Log

enum class TokenType {
    COMMAND, NUMBER, LETTER, CHAR, OPEN_BRACE, CLOSE_BRACE, OPEN_PAREN, CLOSE_PAREN, OPEN_BRACKET, CLOSE_BRACKET, POWER, SUBSCRIPT, OPERATOR
}

data class Token(val type: TokenType, val value: String) {
    override fun toString(): String = "$type($value)"
}

object MathParser {
    private const val TAG = "MathParser"

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> {
                    i++
                }
                c == '\\' -> {
                    if (i + 1 < input.length && input[i + 1] == '\\') {
                        tokens.add(Token(TokenType.OPERATOR, "\\\\"))
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
                            tokens.add(Token(TokenType.COMMAND, cmd))
                        }
                    }
                }
                c.isDigit() -> {
                    val sb = StringBuilder()
                    while (i < input.length && (input[i].isDigit() || input[i] == '.')) {
                        sb.append(input[i])
                        i++
                    }
                    tokens.add(Token(TokenType.NUMBER, sb.toString()))
                }
                c == '{' -> {
                    tokens.add(Token(TokenType.OPEN_BRACE, "{"))
                    i++
                }
                c == '}' -> {
                    tokens.add(Token(TokenType.CLOSE_BRACE, "}"))
                    i++
                }
                c == '(' -> {
                    tokens.add(Token(TokenType.OPEN_PAREN, "("))
                    i++
                }
                c == ')' -> {
                    tokens.add(Token(TokenType.CLOSE_PAREN, ")"))
                    i++
                }
                c == '[' -> {
                    tokens.add(Token(TokenType.OPEN_BRACKET, "["))
                    i++
                }
                c == ']' -> {
                    tokens.add(Token(TokenType.CLOSE_BRACKET, "]"))
                    i++
                }
                c == '^' -> {
                    tokens.add(Token(TokenType.POWER, "^"))
                    i++
                }
                c == '_' -> {
                    tokens.add(Token(TokenType.SUBSCRIPT, "_"))
                    i++
                }
                c in "+-=*/<>&" || c == '÷' || c == '×' || c == '±' || c == '!' -> {
                    tokens.add(Token(TokenType.OPERATOR, c.toString()))
                    i++
                }
                c.isLetter() -> {
                    tokens.add(Token(TokenType.LETTER, c.toString()))
                    i++
                }
                else -> {
                    tokens.add(Token(TokenType.CHAR, c.toString()))
                    i++
                }
            }
        }
        return tokens
    }

    fun parse(input: String): MathNode {
        val tokens = tokenize(input)
        SafeLog.d(TAG, "Parsing input: $input, tokens: $tokens")
        val (node, _) = parseSubExpression(tokens, 0)
        return node
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
                TokenType.NUMBER -> {
                    children.add(MathNode.Text(token.value))
                    i++
                }
                TokenType.LETTER -> {
                    children.add(MathNode.Text(token.value, isItalic = true))
                    i++
                }
                TokenType.OPERATOR -> {
                    children.add(MathNode.Operator(token.value))
                    i++
                }
                TokenType.CHAR -> {
                    children.add(MathNode.Text(token.value))
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
                    children.add(MathNode.Parentheses(groupNode))
                    i = if (nextIdx < tokens.size && tokens[nextIdx].type == TokenType.CLOSE_PAREN) nextIdx + 1 else nextIdx
                }
                TokenType.CLOSE_PAREN -> {
                    children.add(MathNode.Text(")"))
                    i++
                }
                TokenType.OPEN_BRACKET -> {
                    val (groupNode, nextIdx) = parseSubExpression(tokens, i + 1, stopOnBracket = true)
                    children.add(MathNode.SquareBrackets(groupNode))
                    i = if (nextIdx < tokens.size && tokens[nextIdx].type == TokenType.CLOSE_BRACKET) nextIdx + 1 else nextIdx
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
                        "sqrt" -> {
                            val (content, nextIdx) = parseArg(tokens, i + 1)
                            children.add(MathNode.Sqrt(content))
                            i = nextIdx
                        }
                        "sin", "cos", "tan", "arcsin", "arccos", "arctan", "asin", "acos", "atan", "log", "ln", "abs", "floor", "ceil", "cuberoot", "max", "gcf" -> {
                            children.add(MathNode.Text(cmd, isItalic = true))
                            i++
                        }
                        "pm" -> {
                            children.add(MathNode.Operator("±"))
                            i++
                        }
                        "times" -> {
                            children.add(MathNode.Operator("×"))
                            i++
                        }
                        "div" -> {
                            children.add(MathNode.Operator("÷"))
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
        val token = tokens[startIndex]
        return if (token.type == TokenType.OPEN_BRACE) {
            val (node, nextIdx) = parseSubExpression(tokens, startIndex + 1, stopOnBrace = true)
            val finalIdx = if (nextIdx < tokens.size && tokens[nextIdx].type == TokenType.CLOSE_BRACE) nextIdx + 1 else nextIdx
            Pair(node, finalIdx)
        } else {
            // Parse single token as argument
            val node = when (token.type) {
                TokenType.NUMBER -> MathNode.Text(token.value)
                TokenType.LETTER -> MathNode.Text(token.value, isItalic = true)
                TokenType.OPERATOR -> MathNode.Operator(token.value)
                TokenType.CHAR -> MathNode.Text(token.value)
                TokenType.COMMAND -> {
                    when (token.value) {
                        "pi" -> MathNode.SpecialSymbol("π")
                        "alpha" -> MathNode.SpecialSymbol("α")
                        "beta" -> MathNode.SpecialSymbol("β")
                        "theta" -> MathNode.SpecialSymbol("θ")
                        "gamma" -> MathNode.SpecialSymbol("γ")
                        "tau" -> MathNode.SpecialSymbol("τ")
                        "mu" -> MathNode.SpecialSymbol("μ")
                        "infty" -> MathNode.SpecialSymbol("∞")
                        else -> MathNode.Text("\\${token.value}")
                    }
                }
                else -> MathNode.Text(token.value)
            }
            Pair(node, startIndex + 1)
        }
    }
}
