package org.arend.lexer

import com.intellij.lexer.FlexAdapter
import java.io.Reader

class ArendDocLexerAdapter : FlexAdapter(ArendDocLexer(null as Reader?))
