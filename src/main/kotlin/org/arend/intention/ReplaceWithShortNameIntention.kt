package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.descendantsOfType
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendLongName
import org.arend.psi.ancestor
import org.arend.psi.ancestors
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.impl.ArendGroup
import org.arend.refactoring.RenameReferenceAction

class ReplaceWithShortNameIntention: SelfTargetingIntention<ArendLongName>(ArendLongName::class.java, "Replace with short name") {
    override fun isApplicableTo(element: ArendLongName, caretOffset: Int, editor: Editor): Boolean =
        element.refIdentifierList.size > 1 && element.refIdentifierList.all { it.resolve is PsiLocatedReferable } &&
                element.refIdentifierList.last().let {l -> l.resolve is ArendGroup && element.scope.resolveName(l.text).let { it == null || it == l.resolve }}

    override fun applyTo(element: ArendLongName, project: Project, editor: Editor) {
        val currentRefIdentifier = element.refIdentifierList.last()
        val target = currentRefIdentifier.resolve
        val containingDefinition = (element as? PsiElement)?.ancestor<ArendDefinition>()
        if (target is ArendGroup && containingDefinition != null)
            containingDefinition.descendantsOfType<ArendLongName>().map { it.refIdentifierList.last() }
                .filter { it.resolve == target }.forEach {
                RenameReferenceAction(it, element.longName, target, true).execute(if (it == currentRefIdentifier) editor else null)
            }
    }
}