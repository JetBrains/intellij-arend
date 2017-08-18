package org.vclang.ide.search

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.psi.tree.TokenSet
import org.vclang.lang.core.lexer.VcLexerAdapter
import org.vclang.lang.core.psi.VC_COMMENTS
import org.vclang.lang.core.psi.VcTypes.IDENTIFIER
import org.vclang.lang.core.psi.VcTypes.LITERAL

class VcWordScanner : DefaultWordsScanner(
        VcLexerAdapter(),
        TokenSet.create(IDENTIFIER),
        VC_COMMENTS,
        TokenSet.create(LITERAL)
)
