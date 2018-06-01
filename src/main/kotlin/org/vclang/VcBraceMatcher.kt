package org.vclang

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.vclang.psi.VC_COMMENTS
import org.vclang.psi.VC_WHITE_SPACES
import org.vclang.psi.VcElementTypes.*

class VcBraceMatcher : PairedBraceMatcher {

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
                BracePair(LPAREN, RPAREN, false)
        )

        private val InsertPairBraceBefore = TokenSet.orSet(
                VC_COMMENTS,
                VC_WHITE_SPACES,
                TokenSet.create(COMMA, RPAREN, LBRACE, RBRACE)
        )
    }
}
