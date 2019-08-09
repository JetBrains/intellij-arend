package org.arend.quickfix.referenceResolve

import com.intellij.openapi.editor.Editor
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.refactoring.*
import org.arend.util.LongName

class ResolveReferenceAction(val target: PsiLocatedReferable,
                             private val targetFullName: List<String>,
                             private val statCmdFixAction: AbstractRefactoringAction?,
                             private val nameFixAction: AbstractRefactoringAction?) {

    override fun toString(): String = LongName(targetFullName).toString() + ((target.containingFile as? ArendFile)?.modulePath?.let { " in $it" }
            ?: "")

    fun execute(editor: Editor?) {
        statCmdFixAction?.execute(editor)
        nameFixAction?.execute(editor)
    }

    fun getFullName(): List<String> = targetFullName

    companion object {
        fun getProposedFix(target: PsiLocatedReferable, element: ArendReferenceElement): ResolveReferenceAction? {
            val containingFile = element.containingFile as? ArendFile ?: return null
            val location = LocationData(target)
            val (importAction, resultName) = computeAliases(location, containingFile, element)
                    ?: return null
            val renameAction = if (target is ArendFile) {
                RenameReferenceAction(element, target.modulePath?.toList() ?: return null)
            } else RenameReferenceAction(element, resultName)

            return ResolveReferenceAction(target, location.getLongName(), importAction, renameAction)
        }
    }
}