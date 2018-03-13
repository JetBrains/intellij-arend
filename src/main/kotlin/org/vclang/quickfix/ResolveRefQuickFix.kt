package org.vclang.quickfix

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.util.LongName
import org.vclang.psi.*
import org.vclang.psi.ext.PsiGlobalReferable
import java.util.*

interface ResolveRefFixAction {

}

class ImportFileAction(val importFile: VcFile) : ResolveRefFixAction {
    override fun toString(): String {
        return "Import file "+ importFile.fullName
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

class RenameReferenceAction(val element: PsiElement, val id : String) : ResolveRefFixAction {
    override fun toString(): String {
        return "Rename usage of "+element.text+" to "+id+" as suggested by the namespace/import command"
    }
}

class AddItemsToLongName(val element: PsiElement, val id : List<String>) : ResolveRefFixAction {
    override fun toString(): String {
        return "Replace " + element.text + " with \"long name\" "+LongName(id).toString()
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

        fun getDecision(target: PsiElement, element: PsiElement): List<List<ResolveRefFixAction>> {
            val targetFile = target.containingFile
            val elementFile = element.containingFile
            val result = ArrayList<ResolveRefFixAction>()
            val results = ArrayList<List<ResolveRefFixAction>>()

            val fullName = ArrayList<String>()

            var psi2: PsiElement = target

            while (psi2.parent != null) {
                if (psi2 is PsiGlobalReferable && psi2 !is VcFile) {
                    val name = psi2.name ?: return ArrayList()
                    fullName.add(0, name)
                }
                psi2 = psi2.parent
            }

            val fullNames = HashSet<List<String>>()

            if (elementFile is VcFile && targetFile is VcFile) {
                if (elementFile != targetFile) {
                    var validImportFound = false
                    for (namespaceCommand in elementFile.namespaceCommands) if (namespaceCommand.nsCmd.importKw != null) {
                        val fileIdent = namespaceCommand.longName?.refIdentifierList?.last()
                        if (fileIdent?.reference?.resolve() == targetFile) {
                            val nsUsing = namespaceCommand.nsUsing
                            val hidden = namespaceCommand.hiddenReferences
                            val id = fullName.first()
                            val needNoFurtherFixes = result.size > 0

                            var ok = true // meaning: namespace command correctly imports "id" (possibly renaming it at the same time)
                            val ids = HashSet<String>()

                            if (nsUsing != null) {
                                var found = false
                                for (refIdent in nsUsing.nsIdList) {
                                    if (refIdent.refIdentifier.text == id) {
                                        found = true
                                        val defIdentifier = refIdent.defIdentifier
                                        if (defIdentifier != null) {
                                            ids.add(defIdentifier.name!!)
                                        } else {
                                            ids.add(id)
                                        }
                                    }
                                }

                                if (!found) {
                                    ok = false
                                    if (!needNoFurtherFixes) result.add(AddIdToUsingAction(namespaceCommand, id))
                                }
                            }

                            if (ids.isEmpty() && hidden.isNotEmpty()) {
                                for (ref in hidden) {
                                    if (ref.textRepresentation() == id) {
                                        ok = false
                                        if (!needNoFurtherFixes) result.add(RemoveFromHidingAction(namespaceCommand, ref))
                                    }
                                }
                            }

                            if (ids.isEmpty()) ids.add(id) //

                            for (name in ids) {
                                val fullName2 = ArrayList<String>()
                                fullName2.addAll(fullName)
                                fullName2.removeAt(0)
                                fullName2.add(0, name)
                                fullNames.add(fullName2)
                            }

                            if (ok) validImportFound = true
                        }
                    }

                    if (!validImportFound && result.size == 0) { //no valid import and no other proposed ways of fixing imports
                        result.add(ImportFileAction(targetFile))
                    }

                    if (validImportFound) {
                        result.clear()
                    }
                } else {
                    fullNames.add(fullName) // source & target file coincide -- in this case there are no tricky renaming commands to process
                }
            } else {
                return ArrayList()
            }



            if (fullName.size > 1) {
                for (fName in fullNames) {
                    val actions = ArrayList<ResolveRefFixAction>()
                    actions.addAll(result)
                    actions.add(AddItemsToLongName(element, fName))
                    results.add(actions)
                }
                // need to add further \open commands
                /*val namespaceCommands = ArrayList<List<VcStatCmd>>()
                psi2 = element
                while (psi2.parent != null) {
                    var statements : List<VcStatCmd>? = null

                    if (psi2 is VcWhere) statements = psi2.children.mapNotNull { (it as? VcStatement)?.statCmd }
                    else if (psi2 is VcFile) statements = psi2.namespaceCommands

                    if (statements != null) namespaceCommands.add(0, statements.filter { it.nsCmd.openKw != null })

                    psi2 = psi2.parent
                }

                var currentBlock = fullNames
                for (block in namespaceCommands) {
                    val newBlock = ArrayList<List<String>>()

                    currentBlock = newBlock
                } */

            } else {
                var allNamesDifferent = fullNames.isNotEmpty() && result.isEmpty()
                fullNames.filter { it.size != 1 || it[0] == fullName[0] }.forEach { allNamesDifferent = false }
                if (allNamesDifferent) {
                    for (fName in fullNames) {
                        val actions = ArrayList<ResolveRefFixAction>()
                        actions.add(RenameReferenceAction(element, fName[0]))
                        results.add(actions)
                    }

                } else {
                    results.add(result)
                }
            }


            return results
        }
    }
}