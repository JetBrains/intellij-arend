package org.arend.psi

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.arend.ArendLanguage
import org.arend.parser.ParserMixin.*
import org.arend.psi.ArendElementTypes.*

class ArendTokenType(debugName: String) : IElementType(debugName, ArendLanguage.INSTANCE)

fun initTokenSet(collection: Collection<IElementType>): TokenSet {
    var tokenSet = TokenSet.create()
    collection.forEach {
        tokenSet = TokenSet.orSet(tokenSet, TokenSet.create(it))
    }
    return tokenSet
}

val AREND_COMMENTS: TokenSet = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT, DOC_TEXT)

val AREND_NAMES: TokenSet = TokenSet.create(ID, INFIX, POSTFIX)

val AREND_WHITE_SPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

val AREND_STRINGS: TokenSet = TokenSet.create(STRING)

val AREND_GOALS: TokenSet = TokenSet.create(TGOAL, UNDERSCORE, APPLY_HOLE)

val AREND_DOC_TOKENS: TokenSet = TokenSet.create(DOC_SINGLE_LINE_START, DOC_START, DOC_END, DOC_INLINE_CODE_BORDER,
    DOC_TEXT, DOC_CODE, DOC_CODE_LINE, DOC_PARAGRAPH_SEP, DOC_LBRACKET, DOC_RBRACKET, DOC_CODE_BLOCK_BORDER, DOC_CODE_BLOCK,
    DOC_REFERENCE, DOC_REFERENCE_TEXT, DOC_INLINE_LATEX_CODE, DOC_NEWLINE_LATEX_CODE, DOC_LATEX_CODE,
    DOC_ITALICS_CODE_BORDER, DOC_ITALICS_CODE, DOC_BOLD_CODE_BORDER, DOC_BOLD_CODE, DOC_NEWLINE, DOC_LINEBREAK, DOC_TABS,
    DOC_UNORDERED_LIST, DOC_ORDERED_LIST, DOC_BLOCKQUOTES, DOC_HEADER_1, DOC_HEADER_2)