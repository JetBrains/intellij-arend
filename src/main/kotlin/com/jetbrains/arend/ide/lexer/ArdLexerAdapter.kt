package com.jetbrains.arend.ide.lexer

import com.intellij.lexer.FlexAdapter
import java.io.Reader

class ArdLexerAdapter : FlexAdapter(ArdLexer(null as Reader?))
