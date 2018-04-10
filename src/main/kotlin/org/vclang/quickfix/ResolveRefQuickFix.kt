package org.vclang.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.jetpad.vclang.naming.reference.RedirectingReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.scope.*
import com.jetbrains.jetpad.vclang.term.group.Group
import com.jetbrains.jetpad.vclang.util.LongName
import org.vclang.psi.*
import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.ext.VcReferenceElement
import java.util.Collections.singletonList

interface ResolveRefFixAction {
    fun execute(editor: Editor?)
}

class ImportFileAction(private val importFile: VcFile, private val currentFile: VcFile, private val usingList: List<String>?) : ResolveRefFixAction {
    override fun toString(): String {
        return "Import file "+ importFile.fullName
    }

    override fun execute(editor: Editor?) {
        val fullName = importFile.fullName
        val factory = VcPsiFactory(importFile.project)
        val commandStatement = factory.createImportCommand(fullName + (if (usingList == null) "" else " ()"))

        if (currentFile.children.isEmpty()) currentFile.add(commandStatement)
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

        if (usingList != null) AddIdToUsingAction(commandStatement.statCmd!!, usingList).execute(editor)

        if (anchor.parent == currentFile) {
            if (after) {
                currentFile.addAfter(commandStatement, anchor)
                currentFile.addAfter(factory.createWhitespace("\n"), anchor)
            } else {
                currentFile.addBefore(commandStatement, anchor)
                currentFile.addBefore(factory.createWhitespace("\n"), anchor)
            }
        }
    }
}

class AddIdToUsingAction(private val statCmd: VcStatCmd, private val idList : List<String>) : ResolveRefFixAction {
    override fun toString(): String {
        val name = if (idList.size == 1) idList[0] else idList.toString()
        return "Add "+ name + " to "+ ResolveRefQuickFix.statCmdName(statCmd)+" import's \"using\" list"
    }

    private fun executeId(editor: Editor?, id : String) {
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
                val nsCmd = factory.createImportCommand("Dummy (a,$id)").statCmd
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

    override fun execute(editor: Editor?) {
        for (id in idList)
            executeId(editor, id)
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

class RenameReferenceAction(private val element: VcReferenceElement, private val id : List<String>) : ResolveRefFixAction {
    override fun toString(): String {
        return "Rename " + element.text + " to "+LongName(id).toString()
    }

    override fun execute(editor: Editor?) {
        if (element.parent is VcLongName) {
            val lName = LongName(id).toString()
            val factory = VcPsiFactory(element.project)
            val literal =  factory.createLiteral(lName)
            val longName = literal.longName
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

        fun getDecision(target: PsiElement, element: VcReferenceElement): List<List<ResolveRefFixAction>> {
            val targetFile = target.containingFile
            val currentFile = element.containingFile
            val result = ArrayList<ResolveRefFixAction>()

            val fullName = ArrayList<String>()
            val alternativeFullName : ArrayList<String>? = if (target is VcClassFieldSyn || target is VcClassField || target is VcConstructor) ArrayList() else null
            var ignoreFlag = true

            var psi: PsiElement = target
            var targetTop : MutableList<PsiReferable> = ArrayList()

            while (psi.parent != null) {
                if (psi is PsiReferable && psi !is VcFile) {
                    val name = psi.name ?: return ArrayList()

                    fullName.add(0, name)
                    if (alternativeFullName != null) {
                        if (ignoreFlag && alternativeFullName.isNotEmpty()) {
                            ignoreFlag = false
                            targetTop.add(psi)
                        } else {
                            alternativeFullName.add(0, name)
                            targetTop = ArrayList()
                            targetTop.add(psi)
                        }
                    } else {
                        targetTop = ArrayList()
                        targetTop.add(psi)
                    }

                }
                psi = psi.parent
            }

            val fullNames = HashSet<List<String>>()
            fullNames.add(fullName)
            if (alternativeFullName != null) fullNames.add(alternativeFullName)

            if (currentFile is VcFile && targetFile is VcFile) {
                if (currentFile != targetFile) {
                    var precariousMode = false // True if imported scope of the current file has nonempty intersection with the scope of the target file
                    val fileGroup =  object: Group by currentFile {
                        override fun getSubgroups(): Collection<Group> = emptyList()
                    }
                    val importedScope = ScopeFactory.forGroup(fileGroup, currentFile.moduleScopeProvider, null, false)

                    for (vcDef in targetFile.subgroups) {
                        if (importedScope.resolveName(vcDef.name) != null) {
                            precariousMode = true
                            break
                        }
                    }

                    var suitableImport : VcStatCmd? = null
                    val aliases = HashMap<List<String>, HashSet<String>>()

                    for (fName in fullNames) {
                        aliases[fName] = HashSet()
                    }

                    for (namespaceCommand in currentFile.namespaceCommands) if (namespaceCommand.nsCmd.importKw != null) {
                        val fileIdent = namespaceCommand.longName?.refIdentifierList?.last()
                        if (fileIdent?.reference?.resolve() == targetFile) {
                            suitableImport = namespaceCommand // even if some of the members are unused or hidden we still can access them using "very long name"

                            val nsUsing = namespaceCommand.nsUsing
                            val hiddenList = namespaceCommand.refIdentifierList
                            val defaultNameHiddenFNames : HashSet<List<String>> = HashSet()

                            if (hiddenList.isNotEmpty()) for (ref in hiddenList) fullNames.filterTo(defaultNameHiddenFNames) { ref.referenceName == it[0] }

                            if (nsUsing != null) {
                                for (refIdentifier in nsUsing.nsIdList) {
                                    for (fName in fullNames) {
                                        val originalName = fName[0]
                                        if (refIdentifier.refIdentifier.text == originalName) {
                                            val defIdentifier = refIdentifier.defIdentifier
                                            aliases[fName]?.add(if (defIdentifier != null) defIdentifier.name!! else originalName)
                                        }
                                    }
                                }
                            } else {
                                fullNames.filter { !defaultNameHiddenFNames.contains(it) }.forEach { aliases[it]!!.add(it[0]) }
                            }

                        }
                    }

                    fullNames.clear()

                    for (fName in aliases.keys) {
                        for (alias in aliases[fName]!!) {
                            val fName2 = ArrayList<String>()
                            fName2.addAll(fName)
                            fName2.removeAt(0)
                            fName2.add(0, alias)
                            fullNames.add(fName2)
                        }
                    }

                    if (fullNames.isEmpty()) { // target definition is inaccessible in current context
                        if (importedScope.resolveName(fullName[0]) == null) fullNames.add(fullName)
                        if (alternativeFullName != null && importedScope.resolveName(alternativeFullName[0]) == null) fullNames.add(alternativeFullName)

                        if (suitableImport != null) { // the definition is unused or hidden
                            val nsUsing = suitableImport.nsUsing
                            val hiddenList = suitableImport.refIdentifierList
                            val addToUsing = ArrayList<String>()

                            for (fName in fullNames) {
                                var hiddenRef : VcRefIdentifier? = null
                                for (ref in hiddenList) if (ref.referenceName == fName[0]) hiddenRef = ref
                                if (hiddenRef != null) {
                                    result.add(RemoveFromHidingAction(suitableImport, hiddenRef))
                                } else if (nsUsing != null) {
                                    addToUsing.add(fName[0])
                                }
                            }

                            if (addToUsing.isNotEmpty()) result.add(AddIdToUsingAction(suitableImport, addToUsing))

                        } else // target file not imported at all
                            result.add(ImportFileAction(targetFile, currentFile, if (precariousMode) fullNames.map { it[0] } else null))
                    }
                }
            } else {
                return ArrayList()
            }

            var currentBlock : Set<List<String>>
            if (fullName.size > 1) {
                val namespaceCommands = ArrayList<List<VcStatCmd>>()
                psi = element
                while (psi.parent != null) {
                    var statements : List<VcStatCmd>? = null

                    if (psi is VcWhere) statements = psi.children.mapNotNull { (it as? VcStatement)?.statCmd }
                    else if (psi is VcFile) statements = psi.namespaceCommands

                    if (statements != null) namespaceCommands.add(0, statements.filter { it.nsCmd.openKw != null })

                    psi = psi.parent
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

                    if (result.size > 0 && targetTop.isNotEmpty()) { // calculate the scope imitating current scope after the import command have been fixed
                        val complementScope = object : ListScope(targetTop as List<Referable>?) {
                            override fun resolveNamespace(name: String?, resolveModuleNames: Boolean): Scope? {
                                return targetTop
                                        .filterIsInstance<VcDefinition>()
                                        .firstOrNull { name == it.textRepresentation() }
                                        ?.let { LexicalScope.opened(it) }
                                        ?: super.resolveNamespace(name, resolveModuleNames)
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

                currentBlock = newBlock

            } else {
                currentBlock = fullNames
            }

            if (currentBlock.isEmpty()) {
                // If we cannot resolve anything -- then perhaps there is some obstruction in scopes
                // Let us use the "longest possible name" when referring to the element
                val veryLongName = ArrayList<String>()
                veryLongName.addAll(targetFile.modulePath.toList())
                veryLongName.addAll(fullName)
                currentBlock.add(veryLongName)

                // no point removing anything from the list of hidden definitions or adding anything to the using list if very long name is used anyway
                result.removeAll(result.filter { it is RemoveFromHidingAction })
                result.removeAll(result.filter { it is AddIdToUsingAction })
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