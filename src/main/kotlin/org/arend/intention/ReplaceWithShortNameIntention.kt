package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.arend.psi.ArendLongName
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.impl.ArendGroup
import org.arend.refactoring.RenameReferenceAction

class ReplaceWithShortNameIntention: SelfTargetingIntention<ArendLongName>(ArendLongName::class.java, "Replace with short name") {
    override fun isApplicableTo(element: ArendLongName, caretOffset: Int, editor: Editor): Boolean =
        element.refIdentifierList.size > 1 && element.refIdentifierList.all { it.resolve is PsiLocatedReferable } &&
                element.refIdentifierList.last().let {l -> l.resolve is ArendGroup && element.scope.resolveName(l.text).let { it == null || it == l.resolve }}

    override fun applyTo(element: ArendLongName, project: Project, editor: Editor) {
        RenameReferenceAction(element.refIdentifierList.last(), element.longName, element.refIdentifierList.last().resolve as? ArendGroup, true).execute(editor)
    }
}