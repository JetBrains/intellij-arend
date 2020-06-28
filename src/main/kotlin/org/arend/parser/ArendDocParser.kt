package org.arend.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.arend.parser.ParserMixin.*
import org.arend.psi.ArendElementTypes.*

class ArendDocParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        if (builder.tokenType === DOC_START) {
            builder.advanceLexer()
        }

        var longMarker: PsiBuilder.Marker? = null
        var bodyMarker: PsiBuilder.Marker? = null
        var last: IElementType? = null
        while (!builder.eof()) {
            when (val token = builder.tokenType) {
                ID -> {
                    if (longMarker == null) {
                        longMarker = builder.mark()
                    }
                    val marker = builder.mark()
                    builder.advanceLexer()
                    if (last == ID) {
                        marker.error("Unexpected identifier")
                    } else {
                        marker.done(REF_IDENTIFIER)
                        last = ID
                    }
                }
                DOT -> {
                    if (last != ID) {
                        builder.error("Unexpected '.'")
                    }
                    builder.advanceLexer()
                    last = DOT
                }
                TokenType.BAD_CHARACTER -> {
                    builder.error("Unexpected character")
                    builder.advanceLexer()
                }
                else -> {
                    if (longMarker != null) {
                        if (last == DOT) {
                            builder.error("Expected an identifier")
                        }
                        longMarker.done(LONG_NAME)
                        longMarker = null
                        last = null
                    }
                    if (token == DOC_PARAGRAPH_SEP && bodyMarker == null) {
                        bodyMarker = builder.mark()
                    }
                    builder.advanceLexer()
                }
            }
        }

        if (longMarker != null) {
            builder.error("Expected '}'")
            longMarker.done(LONG_NAME)
        }

        bodyMarker?.done(DOC_BODY)
        rootMarker.done(root)
        return builder.treeBuilt
    }
}