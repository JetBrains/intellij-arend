package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.descendantsOfType
import org.arend.psi.ext.ArendLongName
import org.arend.psi.ext.ArendRefIdentifier
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.refactoring.RenameReferenceAction
import org.arend.util.ArendBundle

class ReplaceWithShortNameIntention: SelfTargetingIntention<ArendLongName>(ArendLongName::class.java, ArendBundle.message("arend.import.replaceWithShortName")) {
    override fun isApplicableTo(element: ArendLongName, caretOffset: Int, editor: Editor): Boolean {
        return element.refIdentifierList.size > 1 && element.refIdentifierList.all { it.resolve is PsiLocatedReferable } &&
                isApplicableTo(element.refIdentifierList.last())
    }

    override fun applyTo(element: ArendLongName, project: Project, editor: Editor) {
        val currentRefIdentifier = element.refIdentifierList.last()
        val target = currentRefIdentifier.resolve
        val containingGroup = element.containingFile
        if (target is PsiLocatedReferable && containingGroup != null)
            containingGroup.descendantsOfType<ArendLongName>().map { it.refIdentifierList.last() }
                .filter { it.resolve == target && Companion.isApplicableTo(it) }.forEach {
                RenameReferenceAction(it, element.longName, target, true).execute(if (it == currentRefIdentifier) editor else null)
            }
    }

    companion object {
       fun isApplicableTo(l : ArendRefIdentifier) =
           l.resolve is PsiLocatedReferable && (l.parent as ArendLongName).scope.resolveName(l.text).let { it == null || it == l.resolve }
    }
}