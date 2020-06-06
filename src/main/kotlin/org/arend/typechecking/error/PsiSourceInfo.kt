package org.arend.typechecking.error

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.*
import org.arend.ext.error.SourceInfo
import org.arend.ext.reference.DataContainer
import org.arend.highlight.BasePass
import org.arend.psi.ext.moduleTextRepresentationImpl
import org.arend.psi.ext.positionTextRepresentationImpl


open class PsiSourceInfo(private val psiPointer: SmartPsiElementPointer<out PsiElement>) : SourceInfo, DataContainer {
    override fun getData() = psiPointer

    override fun moduleTextRepresentation() = runReadAction { psiPointer.element?.moduleTextRepresentationImpl() }

    override fun positionTextRepresentation() = runReadAction { psiPointer.element?.positionTextRepresentationImpl() }
}

class FixedPsiSourceInfo(element: PsiElement) : PsiSourceInfo(SmartPointerManager.createPointer(element)) {
    private val file = element.containingFile
    private val line: Int
    private val column: Int

    init {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
        if (document == null) {
            line = -1
            column = -1
        } else {
            val offset = BasePass.getImprovedTextOffset(null, element)
            line = document.getLineNumber(offset)
            column = offset - document.getLineStartOffset(line)
        }
    }

    override fun moduleTextRepresentation() = file.name

    override fun positionTextRepresentation() =
        if (line < 0 || column < 0) null else (line + 1).toString() + ":" + (column + 1).toString()
}