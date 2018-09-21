package org.arend

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.arend.psi.ArendElementTypes.LGOAL
import org.arend.psi.AREND_COMMENTS
import org.arend.psi.AREND_WHITE_SPACES
import org.arend.psi.ArendElementTypes.*

class ArendBraceMatcher : PairedBraceMatcher {

    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(
            lbraceType: IElementType,
            contextType: IElementType?
    ): Boolean = contextType in InsertPairBraceBefore

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int =
            openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
                BracePair(LBRACE, RBRACE, true),
                BracePair(LPAREN, RPAREN, false),
                BracePair(LGOAL, RBRACE, false)
        )

        private val InsertPairBraceBefore = TokenSet.orSet(
                AREND_COMMENTS,
                AREND_WHITE_SPACES,
                TokenSet.create(COMMA, RPAREN, LBRACE, RBRACE)
        )
    }
}
