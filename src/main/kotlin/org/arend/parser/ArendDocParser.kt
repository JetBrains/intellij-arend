package org.arend.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.LightPsiParser
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilder.Marker
import com.intellij.lang.PsiParser
import com.intellij.psi.TokenType.*
import com.intellij.psi.tree.IElementType
import org.arend.parser.ParserMixin.*
import org.arend.psi.ArendElementTypes.*

class ArendDocParser : PsiParser, LightPsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        parseLight(root, builder)
        return builder.treeBuilt
    }

    override fun parseLight(root: IElementType, builder: PsiBuilder) {
        val rootMarker = builder.mark()
        if (builder.tokenType === DOC_START) {
            builder.advanceLexer()
        }

        var bodyMarker: Marker? = null
        while (!builder.eof()) {
            when (val token = builder.tokenType) {
                DOC_TEXT, DOC_IGNORED, DOC_CODE -> builder.advanceLexer()
                DOC_PARAGRAPH_SEP -> {
                    if (bodyMarker == null) bodyMarker = builder.mark()
                    builder.advanceLexer()
                }
                DOC_LBRACKET -> parseReferenceText(builder)
                LBRACE -> parseReference(builder, builder.mark())
                DOC_CODE_BLOCK_BORDER -> parseCodeBlock(builder)
                else -> parseBad(token, builder)
            }
        }

        bodyMarker?.done(DOC_BODY)
        rootMarker.done(root)
    }

    private fun parseBad(token: IElementType?, builder: PsiBuilder) {
        if (token == BAD_CHARACTER) {
            builder.error("Unexpected character")
        } else {
            builder.error("Unexpected token: $token")
        }
        builder.advanceLexer()
    }

    private fun parseCodeBlock(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer()

        while (!builder.eof()) {
            when (val token = builder.tokenType) {
                DOC_CODE_LINE -> builder.advanceLexer()
                DOC_CODE_BLOCK_BORDER -> {
                    builder.advanceLexer()
                    marker.done(DOC_CODE_BLOCK)
                    return
                }
                else -> parseBad(token, builder)
            }
        }

        builder.error("Expected '```'")
        marker.done(DOC_CODE_BLOCK)
    }

    private fun parseReferenceText(builder: PsiBuilder) {
        val refMarker = builder.mark()
        val refTextMarker = builder.mark()
        builder.advanceLexer()

        while (!builder.eof()) {
            when (val token = builder.tokenType) {
                DOC_TEXT -> builder.advanceLexer()
                DOC_RBRACKET -> {
                    builder.advanceLexer()
                    if (builder.tokenType == LBRACE) {
                        refTextMarker.done(DOC_REFERENCE_TEXT)
                        parseReference(builder, refMarker)
                    } else {
                        refTextMarker.drop()
                        refMarker.drop()
                    }
                    return
                }
                else -> parseBad(token, builder)
            }
        }

        refTextMarker.drop()
        refMarker.drop()
    }

    private fun parseReference(builder: PsiBuilder, refMarker: Marker) {
        builder.advanceLexer()
        val marker = builder.mark()

        var isComplete = false
        while (!builder.eof()) {
            when (val token = builder.tokenType) {
                ID -> {
                    val idMarker = builder.mark()
                    builder.advanceLexer()
                    if (isComplete) {
                        idMarker.error("Unexpected identifier")
                    } else {
                        idMarker.done(REF_IDENTIFIER)
                        isComplete = true
                    }
                }
                DOT -> {
                    if (isComplete) {
                        isComplete = false
                    } else {
                        builder.error("Unexpected '.'")
                    }
                    builder.advanceLexer()
                }
                RBRACE -> {
                    if (!isComplete) {
                        builder.error("Expected an identifier")
                    }
                    marker.done(LONG_NAME)
                    builder.advanceLexer()
                    refMarker.done(DOC_REFERENCE)
                    return
                }
                else -> parseBad(token, builder)
            }
        }

        builder.error("Expected '}'")
        marker.done(LONG_NAME)
        refMarker.done(DOC_REFERENCE)
    }
}