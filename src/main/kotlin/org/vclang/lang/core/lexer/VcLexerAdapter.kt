package org.vclang.lang.core.lexer

import com.intellij.lexer.FlexAdapter
import java.io.Reader

class VcLexerAdapter : FlexAdapter(_VcLexer(null as Reader?))
