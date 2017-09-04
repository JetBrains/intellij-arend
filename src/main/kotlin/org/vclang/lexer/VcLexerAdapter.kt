package org.vclang.lexer

import com.intellij.lexer.FlexAdapter
import org.vclang.lang.lexer._VcLexer
import java.io.Reader

class VcLexerAdapter : FlexAdapter(_VcLexer(null as Reader?))
