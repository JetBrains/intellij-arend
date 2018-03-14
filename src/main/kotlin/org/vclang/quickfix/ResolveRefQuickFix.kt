package org.vclang.quickfix

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.jetpad.vclang.util.LongName
import org.vclang.VcFileType
import org.vclang.psi.*
import org.vclang.psi.ext.PsiGlobalReferable
import java.util.*

interface ResolveRefFixAction {
    fun execute()
}

class ImportFileAction(private val importFile: VcFile, private val currentFile: VcFile) : ResolveRefFixAction {
    override fun toString(): String {
        return "Import file "+ importFile.fullName
    }

    override fun execute() {
        val fullName = importFile.fullName
        val file = ResolveRefQuickFix.createFromText(importFile.project, "\\import "+fullName)
        val commands = file?.namespaceCommands
        if (commands != null && commands.isNotEmpty()) {
            if (currentFile.children.isEmpty()) currentFile.add(commands[0])
            var anchor = currentFile.children[0]
            var after = false

            val currFileCommands = currentFile.namespaceCommands.filter { it.nsCmd.importKw != null }
            if (currFileCommands.isNotEmpty()) {
                val name = LongName(currFileCommands[0].path).toString()
                anchor = currFileCommands[0].parent
                if (fullName >= name) after = true
            }

            if (after) for (nC in currFileCommands.drop(1)) {
                val name = LongName(nC.path).toString()
                if (fullName >= name) anchor = nC.parent else break
            }

            if (anchor.parent == currentFile) {
                if (after) {
                    currentFile.addAfter(commands[0].parent, anchor)
                    currentFile.addAfter(ResolveRefQuickFix.createWhitespace(currentFile.project, "\n"), anchor)
                } else {
                    currentFile.addBefore(commands[0].parent, anchor)
                    currentFile.addBefore(ResolveRefQuickFix.createWhitespace(currentFile.project, "\n"), anchor)
                }
            }
        }
    }
}

class AddIdToUsingAction(private val statCmd: VcStatCmd, val id : String) : ResolveRefFixAction {
    override fun toString(): String {
        return "Add "+ id + " to "+ ResolveRefQuickFix.statCmdName(statCmd)+" import's \"using\" list"
    }

    override fun execute() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class RemoveFromHidingAction(private val statCmd: VcStatCmd, val id : VcRefIdentifier) : ResolveRefFixAction {
    override fun toString(): String {
        return "Remove "+ id.referenceName + " from " + ResolveRefQuickFix.statCmdName(statCmd) + " import's \"hiding\" list"
    }

    override fun execute() {
        var startSibling : PsiElement = id
        var endSibling : PsiElement = id

        if (startSibling.prevSibling is PsiWhiteSpace) startSibling = startSibling.prevSibling


        val leftEnd = startSibling.prevSibling.node.elementType == VcElementTypes.LPAREN

        while (endSibling.nextSibling is PsiWhiteSpace || endSibling.nextSibling.node.elementType == VcElementTypes.COMMA) {
            endSibling = endSibling.nextSibling
            if (endSibling.node.elementType == VcElementTypes.COMMA && !leftEnd) break
        }

        val rightEnd = endSibling.nextSibling.node.elementType == VcElementTypes.RPAREN

        if (rightEnd && startSibling.prevSibling.node.elementType == VcElementTypes.COMMA) {
            startSibling = startSibling.prevSibling
            if (startSibling.prevSibling is PsiWhiteSpace) startSibling = startSibling.prevSibling
        }

        id.parent.deleteChildRange(startSibling, endSibling)
    }
}

class RenameReferenceAction(val element: PsiElement, val id : String) : ResolveRefFixAction {
    override fun toString(): String {
        return "Rename usage of "+element.text+" to "+id+" as suggested by the namespace/import command"
    }

    override fun execute() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class AddItemsToLongName(val element: PsiElement, val id : List<String>) : ResolveRefFixAction {
    override fun toString(): String {
        return "Replace " + element.text + " with \"long name\" "+LongName(id).toString()
    }

    override fun execute() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class ResolveRefQuickFix {
    companion object {

        fun createFromText(project: Project, code: String) : VcFile? {
            return PsiFileFactory.getInstance(project).createFileFromText("DUMMY.vc", VcFileType, code) as? VcFile
        }

        fun createWhitespace(project: Project, symbol: String): PsiElement {
            return PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(symbol)
        }

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
            val project = element.project
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
                            val hidden = namespaceCommand.refIdentifierList
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
                                    if (ref.referenceName == id) {
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
                        result.add(ImportFileAction(targetFile, elementFile))
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