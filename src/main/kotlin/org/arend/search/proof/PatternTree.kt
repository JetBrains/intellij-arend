package org.arend.search.proof

import java.util.regex.Pattern

sealed interface PatternTree {
    companion object {
        fun fromRawPattern(pattern: String) : PatternTree? = patternToTree(pattern)
    }

    enum class Implicitness {
        IMPLICIT, EXPLICIT;

        fun compactLBrace(): String = when (this) {
            IMPLICIT -> "{"
            EXPLICIT -> ""
        }

        fun compactRBrace(): String = when (this) {
            IMPLICIT -> "}"
            EXPLICIT -> ""
        }

        fun toBoolean(): Boolean = this == EXPLICIT
    }

    @JvmInline
    value class BranchingNode(val subNodes: List<Pair<PatternTree, Implicitness>>) : PatternTree {
        override fun toString(): String = subNodes.joinToString(
            " ",
            "[",
            "]",
            transform = { "${it.second.compactLBrace()}${it.first}${it.second.compactRBrace()}" })
    }

    @JvmInline
    value class LeafNode(val referenceName: List<String>) : PatternTree {
        override fun toString(): String = referenceName.joinToString(".")
    }

    object Wildcard : PatternTree {
        override fun toString(): String = "_"
    }
}

private typealias Token = String

internal fun patternToTree(pattern: String): PatternTree? {
    val tokens: List<Token> = Pattern.compile("[^()\\p{Space}]+|\\(|\\)|\\{|}").toRegex().findAll(pattern).map { it.value }.toList()
    return parseTokens(tokens)
}

private fun parseTokens(tokens: List<Token>): PatternTree? {

    fun findClosingBrace(start: Int, limit: Int, openBrace: Token, closingBrace: Token): Int? {
        var balance = 1
        var currentIndex = start + 1
        while (currentIndex < limit && balance > 0) {
            if (tokens[currentIndex] == openBrace) balance += 1
            if (tokens[currentIndex] == closingBrace) balance -= 1
            currentIndex += 1
        }
        return if (balance == 0) {
            currentIndex - 1
        } else {
            null
        }
    }

    fun doParseTokens(position: Int, limit: Int): PatternTree? {
        if (position == limit) return null
        val nodes: MutableList<Pair<PatternTree, PatternTree.Implicitness>> = mutableListOf()
        var currentPosition = position

        while (currentPosition != limit) {
            when (val token = tokens[currentPosition]) {
                "_" -> nodes.add(PatternTree.Wildcard to PatternTree.Implicitness.EXPLICIT)
                "(" -> {
                    val closingBrace = findClosingBrace(currentPosition, limit, token, ")") ?: return null
                    val subTree = doParseTokens(currentPosition + 1, closingBrace) ?: return null
                    nodes.add(subTree to PatternTree.Implicitness.EXPLICIT)
                    currentPosition = closingBrace
                }
                ")" -> return null
                "{" -> {
                    val closingBrace = findClosingBrace(currentPosition, limit, token, "}") ?: return null
                    val subTree = doParseTokens(currentPosition + 1, closingBrace) ?: return null
                    nodes.add(subTree to PatternTree.Implicitness.IMPLICIT)
                    currentPosition = closingBrace
                }
                "}" -> return null
                else -> nodes.add(PatternTree.LeafNode(token.split(".")) to PatternTree.Implicitness.EXPLICIT)
            }
            currentPosition += 1
        }
        return if (nodes.size == 1 && nodes[0].second != PatternTree.Implicitness.IMPLICIT) {
            nodes[0].first
        } else {
            PatternTree.BranchingNode(nodes)
        }
    }

    return doParseTokens(0, tokens.size)
}