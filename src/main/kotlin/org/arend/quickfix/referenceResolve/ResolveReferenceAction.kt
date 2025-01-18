package org.arend.quickfix.referenceResolve

import com.intellij.openapi.editor.Editor
import org.arend.ext.module.LongName
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.ArendGroup
import org.arend.refactoring.*
import org.arend.psi.ArendExpressionCodeFragment
import org.arend.term.group.AccessModifier

class ResolveReferenceAction(val target: PsiLocatedReferable,
                             private val targetFullName: List<String>,
                             val statCmdFixAction: NsCmdRefactoringAction?,
                             val nameFixAction: RenameReferenceAction?) {
    private val suffix: String = (target.containingFile as? ArendFile)?.moduleLocation?.toString() ?: "NULL"

    override fun toString(): String {
        val prefix = LongName(targetFullName).toString()
        return if (prefix.isNotEmpty()) "$prefix in $suffix" else suffix
    }

    fun execute(editor: Editor?) {
        statCmdFixAction?.execute()
        nameFixAction?.execute(editor)
    }

    companion object {
        fun checkIfAvailable(target: PsiLocatedReferable, element: ArendReferenceElement): Boolean { // should return true iff getProposedFix with the same arguments returns a nonnull value
            val containingFile = element.containingFile as? ArendFile ?: return false
            if (target.accessModifier == AccessModifier.PRIVATE) return false
            return isVisible(target.containingFile as ArendFile, containingFile)
        }

        fun getProposedFix(target: PsiLocatedReferable, element: ArendReferenceElement): ResolveReferenceAction? {
            /* TODO[server2]
            val currentTarget = element.reference?.resolve() // TODO[server2]: Do not invoke resolve on EDT
            val fixRequired = currentTarget != target
            val containingFile: ArendFile = when (val file = element.containingFile) {
                is ArendFile -> file
                is ArendExpressionCodeFragment -> file.context?.containingFile as? ArendFile ?: return null
                else -> return null
            }
            val location = LocationData.createLocationData(target)
            if (location != null) {
                val (importAction, resultName) = calculateReferenceName(location, containingFile, element) ?: return null
                val renameAction = when {
                    !fixRequired -> RenameReferenceAction(element, element.longName) // forces idle behavior of renameAction
                    target is ArendFile -> RenameReferenceAction(element, target.moduleLocation?.modulePath?.toList() ?: return null)
                    else -> RenameReferenceAction(element, resultName, target as? ArendGroup)
                }

                return ResolveReferenceAction(target, location.getLongName(), importAction, renameAction)
            }
            */

            return null
        }

        fun getTargetName(target: PsiLocatedReferable, element: ArendCompositeElement, deferredImports: List<NsCmdRefactoringAction>? = null): Pair<String, NsCmdRefactoringAction?> {
            val containingFile = element.containingFile as? ArendFile ?: return Pair("", null)
            val location = LocationData.createLocationData(target)
            if (location != null) {
                val (importAction, resultName) = doCalculateReferenceName(location, containingFile, element, deferredImports = deferredImports)
                return Pair(LongName(resultName.ifEmpty { listOf(target.name) }).toString(), importAction)
            }

            return Pair("", null)
        }
    }
}