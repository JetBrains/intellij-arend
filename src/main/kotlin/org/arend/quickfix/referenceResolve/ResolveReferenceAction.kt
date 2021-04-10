package org.arend.quickfix.referenceResolve

import com.intellij.openapi.editor.Editor
import org.arend.ext.module.LongName
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.impl.ArendGroup
import org.arend.refactoring.*

class ResolveReferenceAction(val target: PsiLocatedReferable,
                             private val targetFullName: List<String>,
                             private val statCmdFixAction: NsCmdRefactoringAction?,
                             private val nameFixAction: RenameReferenceAction) {

    override fun toString(): String {
        val prefix = LongName(targetFullName).toString()
        val suffix = (target.containingFile as? ArendFile)?.moduleLocation?.toString() ?: "NULL"
        return if (prefix.isNotEmpty()) "$prefix in $suffix" else suffix
    }

    fun execute(editor: Editor?) {
        statCmdFixAction?.execute()
        nameFixAction.execute(editor)
    }

    companion object {
        fun checkIfAvailable(target: PsiLocatedReferable, element: ArendReferenceElement): Boolean { // should return true iff getProposedFix with the same arguments returns a nonnull value
            val containingFile = element.containingFile as? ArendFile ?: return false
            return canCalculateReferenceName(LocationData(target), containingFile)
        }

        fun getProposedFix(target: PsiLocatedReferable, element: ArendReferenceElement): ResolveReferenceAction? {
            val currentTarget = element.reference?.resolve()
            val fixRequired = currentTarget != target
            val containingFile = element.containingFile as? ArendFile ?: return null
            val location = LocationData(target)
            val (importAction, resultName) = calculateReferenceName(location, containingFile, element, true) ?: return null
            val renameAction = when {
                !fixRequired -> RenameReferenceAction(element, element.longName) // forces idle behavior of renameAction
                target is ArendFile -> RenameReferenceAction(element, target.moduleLocation?.modulePath?.toList() ?: return null)
                else -> RenameReferenceAction(element, resultName, target as? ArendGroup)
            }

            return ResolveReferenceAction(target, location.getLongName(), importAction, renameAction)
        }

        fun getTargetName(target: PsiLocatedReferable?, element: ArendCompositeElement): String? {
            val containingFile = element.containingFile as? ArendFile ?: return null
            val location = LocationData(target ?: return null)
            val (importAction, resultName) = calculateReferenceName(location, containingFile, element) ?: return null
            importAction?.execute()
            return LongName(resultName).toString()
        }
    }
}