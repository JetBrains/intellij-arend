package org.arend.intention.generating

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.ext.error.MissingClausesError
import org.arend.psi.ArendElementTypes
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.*
import org.arend.quickfix.ImplementMissingClausesQuickFix
import org.arend.typechecking.ArendTypechecking
import org.arend.typechecking.error.ErrorService

internal fun checkMissingClauses(element: PsiElement, editor: Editor): Boolean {
    if (element.elementType != ArendElementTypes.TGOAL) {
        return false
    }
    var fileText = (element.containingFile as ArendFile).text

    var parent: PsiElement? = element
    while (parent !is ArendFunctionBody) {
        parent = parent?.parent
        if (parent == null) {
            return false
        }
    }
    fileText = fileText.removeRange(parent.startOffset, parent.endOffset)

    val project = editor.project ?: return false
    val psiFactory = ArendPsiFactory(project)
    val newFile = psiFactory.createFromText(fileText) ?: return false

    ArendTypechecking.create(project).typecheckModules(listOf(newFile), null)

    val errorService = project.service<ErrorService>()
    val error = errorService.getErrors(newFile).filter { it.error is MissingClausesError }.find {
        it.definition?.endOffset?.plus(1) == parent.startOffset
    }

    errorService.clearNameResolverErrors(newFile)
    errorService.clearTypecheckingErrors(newFile)
    return error != null
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
