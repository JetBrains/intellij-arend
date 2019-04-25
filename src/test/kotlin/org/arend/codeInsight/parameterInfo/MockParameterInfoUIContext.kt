package org.arend.codeInsight.parameterInfo

import com.intellij.psi.PsiElement
import com.intellij.testFramework.utils.parameterInfo.MockParameterInfoUIContext
import java.awt.Color

class MockParameterInfoUIContext(owner: PsiElement): MockParameterInfoUIContext<PsiElement>(owner) {
    var resultText: String? = ""
        private set

    override fun setupUIComponentPresentation(text: String, highlightStartOffset: Int, highlightEndOffset: Int,
                                              isDisabled: Boolean, strikeout: Boolean,
                                              isDisabledBeforeHighlight: Boolean, background: Color?): String {
        if (highlightStartOffset != -1 && highlightEndOffset != -1) {
            resultText = (text.substring(0, highlightStartOffset)
                    + "<highlight>"
                    + text.substring(highlightStartOffset, highlightEndOffset - 1)
                    + "</highlight>"
                    + text.substring(highlightEndOffset - 1))
        } else {
            resultText = text
        }

        return ""
    }

}