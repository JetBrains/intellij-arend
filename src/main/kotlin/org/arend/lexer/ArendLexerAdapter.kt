package org.arend.lexer

import com.intellij.lexer.FlexAdapter
import java.io.Reader

class ArendLexerAdapter : FlexAdapter(ArendLexer(null as Reader?))
