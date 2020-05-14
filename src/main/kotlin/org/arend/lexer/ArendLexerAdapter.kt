package org.arend.lexer

import com.intellij.lexer.FlexAdapter

class ArendLexerAdapter : FlexAdapter(ArendLexerImpl())
