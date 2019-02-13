package org.arend.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.arend.mapFirstNotNull
import org.arend.module.config.ArendModuleConfigService
import org.arend.psi.*
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.quickfix.ResolveRefQuickFix
import org.arend.util.LongName

interface ResolveRefFixAction {
    fun execute(editor: Editor?)
    fun isValid(): Boolean = true
}

class ImportFileAction(private val importFile: ArendFile, private val currentFile: ArendFile, private val usingList: List<String>?): ResolveRefFixAction {
    override fun toString() = "Import file " + importFile.fullName

    private fun isTheSameFile(): Boolean {
        val modulePath = importFile.modulePath ?: return false
        val module = currentFile.module ?: return false
        return ArendModuleConfigService.getConfig(module).availableConfigs.mapFirstNotNull { it.findArendFile(modulePath) } == importFile
    }

    override fun isValid() = isTheSameFile() || ResolveRefQuickFix.isPrelude(importFile)

    override fun execute(editor: Editor?) {
        val fullName = importFile.modulePath?.toString() ?: return
        val factory = ArendPsiFactory(importFile.project)
        val commandStatement = factory.createImportCommand(fullName + (if (usingList == null) "" else " ()"))

        if (currentFile.children.isEmpty())
            currentFile.add(commandStatement)
        var anchor = currentFile.children[0]
        var after = false

        val currFileCommands = currentFile.namespaceCommands.filter { it.importKw != null }
        if (currFileCommands.isNotEmpty()) {
            val name = LongName(currFileCommands[0].path).toString()
            anchor = currFileCommands[0].parent
            if (fullName >= name)
                after = true
        }

        if (after) for (nC in currFileCommands.drop(1)) {
            val name = LongName(nC.path).toString()
            if (fullName >= name)
                anchor = nC.parent else break
        }

        if (usingList != null)
            AddIdToUsingAction(commandStatement.statCmd!!, usingList.map{Pair(it, null)}.toList()).execute(editor)

        if (anchor.parent == currentFile) {
            if (after) {
                val insertedCommand = currentFile.addAfter(commandStatement, anchor)
                currentFile.addAfter(factory.createWhitespace("\n"), anchor)
                currentFile.addAfter(factory.createWhitespace(" "), insertedCommand)
            } else {
                val insertedCommand = currentFile.addBefore(commandStatement, anchor)
                currentFile.addAfter(factory.createWhitespace("\n"), insertedCommand)
            }
        }
    }
}

class AddIdToUsingAction(private val statCmd: ArendStatCmd, private val idList: List<Pair<String, String?>>): ResolveRefFixAction {
    override fun toString(): String {
        val name = if (idList.size == 1) idList[0].first else {
            val buffer = StringBuffer()
            for ((m, entry) in idList.withIndex()) {
                buffer.append(entry.first + (if (entry.second == null) "" else " \\as ${entry.second}"))
                if (m < idList.size - 1) buffer.append(", ")
            }
            buffer.toString()
        }
        return "Add $name to the \"using\" list of the namespace command `${statCmd.text}`"
    }

    private fun addId(id : String, newName: String?) {
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

            val factory = ArendPsiFactory(project)
            val remapStr = if (newName == null) "" else " \\as $newName"
            val nsCmd = factory.createImportCommand("Dummy (a,${id+remapStr})").statCmd
            val newNsUsing = nsCmd!!.nsUsing!!
            val nsId = newNsUsing.nsIdList[1]

            if (nsId != null) {
                val comma = nsId.prevSibling //we will need the comma only once

                if (anchor == null) {
                    anchor = using.usingKw ?: error("Can't find anchor within namespace command")
                    anchor = anchor.parent.addAfter(newNsUsing.lparen!!, anchor)
                    anchor.parent.addBefore(factory.createWhitespace(" "), anchor)
                    anchor.parent.addAfter(newNsUsing.rparen!!, anchor)
                }

                if (anchor != null) {
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
            addId(id.first, id.second)
    }
}

class RemoveFromHidingAction(private val statCmd: ArendStatCmd, val id: ArendRefIdentifier): ResolveRefFixAction {
    override fun toString(): String {
        return "Remove "+ id.referenceName + " from " + ResolveRefQuickFix.statCmdName(statCmd) + " import's \"hiding\" list"
    }

    override fun execute(editor: Editor?) {
        var startSibling : PsiElement = id
        var endSibling : PsiElement = id

        if (startSibling.prevSibling is PsiWhiteSpace) startSibling = startSibling.prevSibling

        val leftEnd = startSibling.prevSibling.node.elementType == ArendElementTypes.LPAREN

        while (endSibling.nextSibling is PsiWhiteSpace || endSibling.nextSibling.node.elementType == ArendElementTypes.COMMA) {
            endSibling = endSibling.nextSibling
            if (endSibling.node.elementType == ArendElementTypes.COMMA && !leftEnd)
                break
        }

        val rightEnd = endSibling.nextSibling.node.elementType == ArendElementTypes.RPAREN

        if (rightEnd && startSibling.prevSibling.node.elementType == ArendElementTypes.COMMA) {
            startSibling = startSibling.prevSibling
            if (startSibling.prevSibling is PsiWhiteSpace)
                startSibling = startSibling.prevSibling
        }

        if (leftEnd && rightEnd) {
            startSibling = startSibling.prevSibling
            endSibling = endSibling.nextSibling
            if (startSibling.prevSibling is PsiWhiteSpace)
                startSibling = startSibling.prevSibling
            if (startSibling.prevSibling.node.elementType == ArendElementTypes.HIDING_KW)
                startSibling = startSibling.prevSibling
            if (startSibling.prevSibling is PsiWhiteSpace)
                startSibling = startSibling.prevSibling
        }

        id.parent.deleteChildRange(startSibling, endSibling)
    }
}

class RenameReferenceAction(private val element: ArendReferenceElement, private val id: List<String>): ResolveRefFixAction {
    override fun toString(): String {
        return "Rename " + element.text + " to "+LongName(id).toString()
    }

    override fun execute(editor: Editor?) {
        val currentLongName = element.parent
        if (currentLongName is ArendLongName) {
            val lName = LongName(id).toString()
            val factory = ArendPsiFactory(element.project)
            val literal = factory.createLiteral(lName)
            val longName = literal.longName
            val offset = element.textOffset
            if (longName != null) {
                currentLongName.addRangeAfter(longName.firstChild, longName.lastChild, element)
                currentLongName.deleteChildRange(currentLongName.firstChild, element)
                editor?.caretModel?.moveToOffset(offset + lName.length)
            }
        }
    }
}

class ResolveRefFixData(val target: PsiLocatedReferable,
                        private val targetFullName: List<String>,
                        private val commandFixAction: ResolveRefFixAction?,
                        private val cursorFixAction: ResolveRefFixAction?): ResolveRefFixAction {

    override fun toString(): String = LongName(targetFullName).toString() +
            ((target.containingFile as? ArendFile)?.modulePath?.let { " in " + it.toString() } ?: "")

    override fun execute(editor: Editor?) {
        commandFixAction?.execute(editor)
        cursorFixAction?.execute(editor)
    }
}