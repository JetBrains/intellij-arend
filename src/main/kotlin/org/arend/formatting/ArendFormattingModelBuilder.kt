package org.arend.formatting

import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.Indent
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.FormattingDocumentModelImpl
import com.intellij.psi.formatter.PsiBasedFormattingModel
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.impl.source.tree.TreeUtil
import org.arend.ArendLanguage
import org.arend.formatting.block.SimpleArendBlock

class ArendFormattingModelBuilder: FormattingModelBuilder {
    override fun createModel(element: PsiElement?, settings: CodeStyleSettings?): FormattingModel {
        val containingFile = element?.containingFile
        val documentModel = if (containingFile != null) FormattingDocumentModelImpl.createOn(containingFile) else null
        val tree = TreeUtil.getFileElement((SourceTreeToPsiMap.psiElementToTree(containingFile) as TreeElement?)!!) //TODO: No !! please
        val arendSettings = settings?.getCommonSettings(ArendLanguage.INSTANCE)
        return PsiBasedFormattingModel(containingFile, SimpleArendBlock(tree, arendSettings, null, null, Indent.getNoneIndent()), documentModel)
    }
}