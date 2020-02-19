package org.arend.quickfix.referenceResolve

import com.intellij.openapi.editor.Editor
import org.arend.ext.module.LongName
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.refactoring.AbstractRefactoringAction
import org.arend.refactoring.LocationData
import org.arend.refactoring.RenameReferenceAction
import org.arend.refactoring.computeAliases

class ResolveReferenceAction(val target: PsiLocatedReferable,
                             private val targetFullName: List<String>,
                             private val statCmdFixAction: AbstractRefactoringAction?,
                             private val nameFixAction: RenameReferenceAction) {

    override fun toString(): String = LongName(targetFullName).toString() + ((target.containingFile as? ArendFile)?.modulePath?.let { " in $it" }
            ?: "")

    fun execute(editor: Editor?) {
        statCmdFixAction?.execute(editor)
        nameFixAction.execute(editor)
    }

    companion object {
        fun getProposedFix(target: PsiLocatedReferable, element: ArendReferenceElement): ResolveReferenceAction? {
            val currentTarget = element.reference?.resolve()
            val fixRequired = currentTarget != target

            val containingFile = element.containingFile as? ArendFile ?: return null
            val location = LocationData(target)
            val (importAction, resultName) = computeAliases(location, containingFile, element)
                    ?: return null
            val renameAction = when {
                !fixRequired -> RenameReferenceAction(element, element.longName) // forces idle behavior of renameAction
                target is ArendFile -> RenameReferenceAction(element, target.modulePath?.toList() ?: return null)
                else -> RenameReferenceAction(element, resultName)
            }

            return ResolveReferenceAction(target, location.getLongName(), importAction, renameAction)
        }


        fun getTargetName(target: PsiLocatedReferable?, element: ArendCompositeElement): String? {
            val containingFile = element.containingFile as? ArendFile ?: return null
            val location = LocationData(target ?: return null)
            val (importAction, resultName) = computeAliases(location, containingFile, element) ?: return null
            importAction?.execute(null)
            return LongName(resultName).toString()
        }
    }
}