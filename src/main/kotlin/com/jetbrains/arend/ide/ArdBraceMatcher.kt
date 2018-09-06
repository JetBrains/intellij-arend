package com.jetbrains.arend.ide

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.jetbrains.arend.ide.psi.ArdElementTypes.*
import com.jetbrains.arend.ide.psi.VC_COMMENTS
import com.jetbrains.arend.ide.psi.VC_WHITE_SPACES

class ArdBraceMatcher : PairedBraceMatcher {

    override fun getPairs(): Array<BracePair> = com.jetbrains.arend.ide.ArdBraceMatcher.Companion.PAIRS

    override fun isPairedBracesAllowedBeforeType(
            lbraceType: IElementType,
            contextType: IElementType?
    ): Boolean = contextType in com.jetbrains.arend.ide.ArdBraceMatcher.Companion.InsertPairBraceBefore

    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int =
            openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
                BracePair(LBRACE, RBRACE, true),
                BracePair(LPAREN, RPAREN, false),
                BracePair(LGOAL, RBRACE, false)
        )

        private val InsertPairBraceBefore = TokenSet.orSet(
                VC_COMMENTS,
                VC_WHITE_SPACES,
                TokenSet.create(COMMA, RPAREN, LBRACE, RBRACE)
        )
    }
}
