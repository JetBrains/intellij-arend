package org.arend.intention.generating

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.arend.ext.error.MissingClausesError
import org.arend.psi.ArendElementTypes
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.quickfix.ImplementMissingClausesQuickFix
import org.arend.typechecking.ArendTypechecking
import org.arend.typechecking.error.ErrorService

internal fun checkMissingClauses(element: PsiElement): Boolean {
    return element.elementType == ArendElementTypes.TGOAL
}

internal fun deleteFunctionBody(element: PsiElement): Pair<ArendGroup, Int>? {
    var parent: PsiElement? = element
    while (parent !is ArendFunctionBody) {
        parent = parent?.parent
        if (parent == null) {
            return null
        }
    }

    val group = parent.parent as? ArendGroup? ?: return null
    val startOffsetParent = parent.startOffset
    runWriteAction {
        parent.delete()
    }
    return Pair(group, startOffsetParent)
}

internal fun fixMissingClausesError(project: Project, file: ArendFile, editor: Editor, group: ArendGroup, offset: Int) {
    val errorService = project.service<ErrorService>()
    (group as? ArendDefFunction?)?.let { errorService.clearTypecheckingErrors(it) }

    group.dropTypechecked()
    ArendTypechecking.create(project).typecheckModules(listOf(group), null)

    val error = errorService.getErrors(file).filter { it.error is MissingClausesError }.find {
        it.definition?.endOffset == offset
    } ?: return
    (error.definition as? TCDefinition?)?.let {
        errorService.clearTypecheckingErrors(it)
    }
    runWriteAction {
        ImplementMissingClausesQuickFix(
            error.error as MissingClausesError,
            SmartPointerManager.createPointer(error.cause as ArendCompositeElement)
        ).invoke(project, editor, file)
    }
}
