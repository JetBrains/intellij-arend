package org.vclang.quickfix

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import org.vclang.psi.VcDefinition
import org.vclang.psi.VcFile
import org.vclang.psi.VcStatCmd
import org.vclang.psi.VcWhere

interface ResolveRefFixAction {

}

class ImportFileAction(val importFile: PsiFile) : ResolveRefFixAction {
    override fun toString(): String {
        return "Import file "+ importFile.name
    }
}

class AddIdToUsingAction(val statCmd: VcStatCmd, val id : String) : ResolveRefFixAction {
    override fun toString(): String {
        return "Add "+ id + " to "+ ResolveRefQuickFix.statCmdName(statCmd)+" import's \"using\" list"
    }
}

class RemoveFromHidingAction(val statCmd: VcStatCmd, val id : Referable) : ResolveRefFixAction {
    override fun toString(): String {
        return "Remove "+ id.textRepresentation() + " from " + ResolveRefQuickFix.statCmdName(statCmd) + " import's \"hiding\" list"
    }
}

class ResolveRefQuickFix {
    companion object {

        fun statCmdName(statCmd : VcStatCmd) : String {
            val file = statCmd.longName?.refIdentifierList?.last()?.reference?.resolve()
            if (file is VcFile) {
                return file.fullName
            }
            return "???"
        }

        fun getDecision(target: PsiElement, element: PsiElement): List<ResolveRefFixAction> {
            val targetFile = target.containingFile
            val elementFile = element.containingFile
            val project = element.project
            val result = ArrayList<ResolveRefFixAction>()

            val fullName = ArrayList<String?>()

            var psi2: PsiElement = target;

            while (psi2.parent != null) {
                if (psi2 is VcDefinition) {
                    fullName.add(psi2.name)
                }
                psi2 = psi2.parent
            }

            if (elementFile is VcFile && targetFile is VcFile && elementFile != targetFile) {
                var nsCmdFound = false
                for (namespaceCommand in elementFile.namespaceCommands)
                    if (namespaceCommand.nsCmd.importKw != null) {
                        val fileIdent = namespaceCommand.longName?.refIdentifierList?.last()
                        if (fileIdent?.reference?.resolve() == targetFile) {
                            val nsUsing = namespaceCommand.nsUsing
                            val hidden = namespaceCommand.hiddenReferences
                            val id = fullName.last()

                            if (nsUsing != null && id != null) {
                                var found = false;
                                for (refIdent in nsUsing.nsIdList) {
                                    if (refIdent.text.equals(id)) {
                                        found = true;
                                        break
                                    }
                                }
                                if (!found) result.add(AddIdToUsingAction(namespaceCommand, id))
                            }

                            if (hidden.isNotEmpty()) {
                                for (ref in hidden) {
                                    if (ref.textRepresentation().equals(id)) {
                                        result.add(RemoveFromHidingAction(namespaceCommand, ref))
                                    }
                                }
                            }

                            nsCmdFound = true;
                            break
                        }
                    }
                if (!nsCmdFound) {
                    result.add(ImportFileAction(targetFile))
                }
            } else {
                return result
            }

            psi2 = element
            while (psi2.parent != null) {
                if (psi2 is VcWhere) {
                    for (statement in psi2.statementList) {
                        val statCmd = statement.statCmd
                        if (statCmd != null && statCmd.nsCmd.openKw != null) {

                        }
                    }

                }

                psi2 = psi2.parent
            }

            return result
        }
    }
}