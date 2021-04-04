package org.arend.formatting

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.Indent
import com.intellij.psi.formatter.FormattingDocumentModelImpl
import com.intellij.psi.formatter.PsiBasedFormattingModel
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.impl.source.tree.TreeUtil
import org.arend.ArendLanguage
import org.arend.formatting.block.SimpleArendBlock

class ArendFormattingModelBuilder: FormattingModelBuilder {
    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val containingFile = formattingContext.psiElement.containingFile
        val documentModel = if (containingFile != null) FormattingDocumentModelImpl.createOn(containingFile) else null
        val tree = TreeUtil.getFileElement(containingFile.node as TreeElement)
        val arendSettings = formattingContext.codeStyleSettings.getCommonSettings(ArendLanguage.INSTANCE)
        return PsiBasedFormattingModel(containingFile, SimpleArendBlock(tree, arendSettings, null, null, Indent.getNoneIndent(), null), documentModel)
    }
}