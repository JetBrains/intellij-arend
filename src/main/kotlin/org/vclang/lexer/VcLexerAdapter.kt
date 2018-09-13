package org.vclang.lexer

import com.intellij.lexer.FlexAdapter
import java.io.Reader

class VcLexerAdapter : FlexAdapter(VcLexer(null as Reader?))
