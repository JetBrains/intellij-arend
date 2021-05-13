package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.descendantsOfType
import org.arend.psi.ArendLongName
import org.arend.psi.ArendRefIdentifier
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.impl.ArendGroup
import org.arend.refactoring.RenameReferenceAction

class ReplaceWithShortNameIntention: SelfTargetingIntention<ArendLongName>(ArendLongName::class.java, "Replace with short name") {
    override fun isApplicableTo(element: ArendLongName, caretOffset: Int, editor: Editor): Boolean =
        element.refIdentifierList.size > 1 && element.refIdentifierList.all { it.resolve is PsiLocatedReferable } &&
                isApplicableTo(element.refIdentifierList.last())

    override fun applyTo(element: ArendLongName, project: Project, editor: Editor) {
        val currentRefIdentifier = element.refIdentifierList.last()
        val target = currentRefIdentifier.resolve
        val containingGroup = element.containingFile
        if (target is ArendGroup && containingGroup != null)
            containingGroup.descendantsOfType<ArendLongName>().map { it.refIdentifierList.last() }
                .filter { it.resolve == target && Companion.isApplicableTo(it) }.forEach {
                RenameReferenceAction(it, element.longName, target, true).execute(if (it == currentRefIdentifier) editor else null)
            }
    }

    companion object {
       fun isApplicableTo(l : ArendRefIdentifier) =
           l.resolve is ArendGroup && (l.parent as ArendLongName).scope.resolveName(l.text).let { it == null || it == l.resolve }
    }
}