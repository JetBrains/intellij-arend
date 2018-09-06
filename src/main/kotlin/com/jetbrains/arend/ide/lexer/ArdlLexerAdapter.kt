package com.jetbrains.arend.ide.lexer

import com.intellij.lexer.FlexAdapter
import java.io.Reader

class ArdlLexerAdapter : FlexAdapter(ArdlLexer(null as Reader?))
