package org.arend.codeInsight

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.psi.TokenType
import org.arend.psi.ArendElementTypes

class ArendStringQuoteHandler : SimpleTokenSetQuoteHandler(ArendElementTypes.STRING, TokenType.BAD_CHARACTER)
