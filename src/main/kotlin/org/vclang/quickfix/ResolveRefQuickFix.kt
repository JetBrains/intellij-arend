package org.vclang.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.jetpad.vclang.naming.reference.RedirectingReferable
import com.jetbrains.jetpad.vclang.naming.scope.*
import com.jetbrains.jetpad.vclang.util.LongName
import org.vclang.psi.*
import org.vclang.psi.ext.PsiLocatedReferable
import java.util.*

interface ResolveRefFixAction {
    fun execute(editor: Editor?)
}

class ImportFileAction(private val importFile: VcFile, private val currentFile: VcFile) : ResolveRefFixAction {
    override fun toString(): String {
        return "Import file "+ importFile.fullName
    }

    override fun execute(editor: Editor?) {
        val fullName = importFile.fullName
        val factory = VcPsiFactory(importFile.project)
        val file = factory.createFromText("\\import "+fullName)
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
                    currentFile.addAfter(factory.createWhitespace("\n"), anchor)
                } else {
                    currentFile.addBefore(commands[0].parent, anchor)
                    currentFile.addBefore(factory.createWhitespace("\n"), anchor)
                }
            }
        }
    }
}

class AddIdToUsingAction(private val statCmd: VcStatCmd, val id : String) : ResolveRefFixAction {
    override fun toString(): String {
        return "Add "+ id + " to "+ ResolveRefQuickFix.statCmdName(statCmd)+" import's \"using\" list"
    }

    override fun execute(editor: Editor?) {
        if (statCmd.nsCmd.importKw != null) {
            val project = statCmd.project
            val using = statCmd.nsUsing
            if (using != null) {
                val nsIds = using.nsIdList
                var anchor = using.lparen
                var needsCommaBefore = false

                for (nsId in nsIds) {
                    val name = nsId.refIdentifier.referenceName
                    if (name > id) break

                    anchor = nsId
                    needsCommaBefore = true
                }

                val factory = VcPsiFactory(project)
                val vcFile = factory.createFromText("\\import Dummy (a,$id)")
                val nsCmd = vcFile?.namespaceCommands?.first()
                val nsId = nsCmd?.nsUsing?.nsIdList?.get(1)

                if (nsId != null) {
                    val comma = nsId.prevSibling //we will need the comma only once

                    if (!needsCommaBefore && !nsIds.isEmpty()) {
                        anchor.parent.addAfter(factory.createWhitespace(" "), anchor)
                        anchor.parent.addAfter(comma, anchor)
                    }
                    anchor.parent.addAfter(nsId, anchor)
                    if (needsCommaBefore) {
                        anchor.parent.addAfter(factory.createWhitespace(" "), anchor)
                        anchor.parent.addAfter(comma, anchor)
                    }
                }
            }
        }
    }
}

class RemoveFromHidingAction(private val statCmd: VcStatCmd, val id : VcRefIdentifier) : ResolveRefFixAction {
    override fun toString(): String {
        return "Remove "+ id.referenceName + " from " + ResolveRefQuickFix.statCmdName(statCmd) + " import's \"hiding\" list"
    }

    override fun execute(editor: Editor?) {
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

        if (leftEnd && rightEnd) {
            startSibling = startSibling.prevSibling
            endSibling = endSibling.nextSibling
            if (startSibling.prevSibling is PsiWhiteSpace) startSibling = startSibling.prevSibling
            if (startSibling.prevSibling.node.elementType == VcElementTypes.HIDING_KW) startSibling = startSibling.prevSibling
            if (startSibling.prevSibling is PsiWhiteSpace) startSibling = startSibling.prevSibling
        }

        id.parent.deleteChildRange(startSibling, endSibling)
    }
}

class RenameReferenceAction(private val element: VcRefIdentifier, private val id : List<String>) : ResolveRefFixAction {
    override fun toString(): String {
        return "Rename " + element.text + " to "+LongName(id).toString()
    }

    override fun execute(editor: Editor?) {
        if (element.parent is VcLongName) {
            val lName = LongName(id).toString()
            val factory = VcPsiFactory(element.project)
            val vcfile = factory.createFromText("\\func dummy => "+lName)
            val defFunc = vcfile?.subgroups?.get(0) as VcDefFunction
            val appExpr = (defFunc.functionBody?.expr as VcNewExpr?)?.appExpr as VcArgumentAppExpr?
            val longName = (appExpr)?.atomFieldsAcc?.atom?.literal?.longName
            val offset = element.textOffset
            if (longName != null) {
                element.parent.addRangeBefore(longName.firstChild, longName.lastChild, element)
                element.delete()
                editor?.caretModel?.moveToOffset(offset + lName.length)
            }
        }
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

        fun getDecision(target: PsiElement, element: VcRefIdentifier): List<List<ResolveRefFixAction>> {
            val targetFile = target.containingFile
            val elementFile = element.containingFile
            val result = ArrayList<ResolveRefFixAction>()

            val fullName = ArrayList<String>()
            var psi2: PsiElement = target
            var targetTop : PsiLocatedReferable? = null

            while (psi2.parent != null) {
                if (psi2 is PsiLocatedReferable && psi2 !is VcFile) {
                    val name = psi2.name ?: return ArrayList()
                    fullName.add(0, name)
                    targetTop = psi2
                }
                psi2 = psi2.parent
            }
            val fullNames = HashSet<List<String>>()

            if (elementFile is VcFile && targetFile is VcFile) {
                if (elementFile != targetFile) {
                    var validImportFound = false
                    val originalName = fullName.first()
                    val aliases = HashSet<String>()

                    for (namespaceCommand in elementFile.namespaceCommands) if (namespaceCommand.nsCmd.importKw != null) {
                        val fileIdent = namespaceCommand.longName?.refIdentifierList?.last()
                        if (fileIdent?.reference?.resolve() == targetFile) {
                            val nsUsing = namespaceCommand.nsUsing
                            val hiddenList = namespaceCommand.refIdentifierList
                            val needNoFurtherFixes = result.size > 0
                            var importIsCorrect = true

                            if (nsUsing != null) {
                                var found = false
                                for (refIdent in nsUsing.nsIdList) {
                                    if (refIdent.refIdentifier.text == originalName) {
                                        found = true
                                        val defIdentifier = refIdent.defIdentifier
                                        if (defIdentifier != null) {
                                            aliases.add(defIdentifier.name!!)
                                        } else {
                                            aliases.add(originalName)
                                        }
                                    }
                                }

                                if (!found) {
                                    importIsCorrect = false
                                    if (!needNoFurtherFixes) result.add(AddIdToUsingAction(namespaceCommand, originalName))
                                }
                            }

                            if (aliases.isEmpty() && hiddenList.isNotEmpty()) {
                                for (ref in hiddenList) {
                                    if (ref.referenceName == originalName) {
                                        importIsCorrect = false
                                        if (!needNoFurtherFixes) result.add(RemoveFromHidingAction(namespaceCommand, ref))
                                    }
                                }
                            }

                            if (importIsCorrect) validImportFound = true
                        }
                    }

                    if (aliases.isEmpty()) aliases.add(originalName) //

                    for (name in aliases) {
                        val fullName2 = ArrayList<String>()
                        fullName2.addAll(fullName)
                        fullName2.removeAt(0)
                        fullName2.add(0, name)
                        fullNames.add(fullName2)
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


            var currentBlock : Set<List<String>>
            if (fullName.size > 1) {
                val namespaceCommands = ArrayList<List<VcStatCmd>>()
                psi2 = element
                while (psi2.parent != null) {
                    var statements : List<VcStatCmd>? = null

                    if (psi2 is VcWhere) statements = psi2.children.mapNotNull { (it as? VcStatement)?.statCmd }
                    else if (psi2 is VcFile) statements = psi2.namespaceCommands

                    if (statements != null) namespaceCommands.add(0, statements.filter { it.nsCmd.openKw != null })

                    psi2 = psi2.parent
                }

                currentBlock = fullNames

                for (commandBlock in namespaceCommands) {
                    val newBlock = HashSet<List<String>>()
                    newBlock.addAll(currentBlock)

                    for (command in commandBlock) {
                        val refIdentifiers = command.longName?.refIdentifierList?.map { it.referenceName }
                        var renamings : HashMap<String, String>? = null
                        val using = command.nsUsing

                        if (using != null) {
                            renamings = HashMap()
                            for (nsId in using.nsIdList) {
                                val oldName = nsId.refIdentifier.referenceName
                                val defIdentifier = nsId.defIdentifier
                                if (defIdentifier != null) renamings[oldName] = defIdentifier.name!!
                                  else renamings[oldName] = oldName
                            }
                        }

                        if (refIdentifiers != null && refIdentifiers.isNotEmpty()) {
                            for (fName in currentBlock) {
                                val i1 = fName.iterator()
                                val i2 = refIdentifiers.iterator()
                                var equals = true
                                while (i2.hasNext()) {
                                    if (i1.next() != i2.next()) {
                                        equals = false
                                        break
                                    }
                                }
                                if (equals && i1.hasNext()) {
                                    val fName2 = ArrayList<String>()
                                    while (i1.hasNext()) fName2.add(i1.next())
                                    if (renamings != null) {
                                        val newName = renamings[fName2[0]]
                                        if (newName != null) {
                                            fName2.removeAt(0)
                                            fName2.add(0, newName)
                                        } else {
                                            equals = false
                                        }
                                    }

                                    if (equals) newBlock.add(fName2)

                                }
                            }
                        }
                    }

                    currentBlock = newBlock
                }

                val newBlock = HashSet<List<String>>()

                for (fName in currentBlock) {
                    var correctedScope = element.scope

                    if (result.size > 0 && targetTop != null) { // calculate the scope imitating current scope after the import command have been fixed
                        val tt = targetTop
                        val complementScope = object : SingletonScope(tt) {
                            override fun resolveNamespace(name: String?, resolveModuleNames: Boolean): Scope? {
                                if (tt is VcDefinition && name == tt.textRepresentation()) return LexicalScope.opened(tt)
                                return super.resolveNamespace(name, resolveModuleNames)
                            }
                        }

                        correctedScope = MergeScope(correctedScope, complementScope)
                    }

                    var referable = Scope.Utils.resolveName(correctedScope, fName)
                    if (referable is RedirectingReferable) {
                        referable = referable.originalReferable
                    }

                    if (referable == target) {
                        newBlock.add(fName)
                    }

                }

                if (newBlock.isEmpty()) { //If we cannot resolve anything -- then perhaps there is some obstruction in scopes -- let us use the longest possible name
                    val veryLongName = ArrayList<String>()
                    veryLongName.addAll(targetFile.modulePath.toList())
                    veryLongName.addAll(fullName)
                    newBlock.add(veryLongName)
                }

                currentBlock = newBlock

            } else {
                currentBlock = fullNames
            }

            // Determine shortest possible name in current scope
            val iterator = currentBlock.iterator()
            var length = -1
            val resultNames : HashSet<List<String>> = HashSet()

            do {
                val lName = iterator.next()
                if (length == -1 || lName.size == length) {
                    length = lName.size
                    resultNames.add(lName)
                } else if (lName.size < length) {
                    length = lName.size
                    resultNames.clear()
                    resultNames.add(lName)
                }
            } while (iterator.hasNext())

            // Form resulting actions
            val results = ArrayList<List<ResolveRefFixAction>>()
            for (resultName in resultNames) {
                val actions = ArrayList<ResolveRefFixAction>()
                actions.addAll(result)

                if ((resultName.size > 1 || (resultName[0] != element.referenceName))) {
                    actions.add(RenameReferenceAction(element, resultName))
                }
                results.add(actions)
            }

            return results
        }
    }
}