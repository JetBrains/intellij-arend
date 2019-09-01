package org.arend.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendIPNameImplMixin
import org.arend.psi.ext.ArendReferenceElement
import org.arend.term.Fixity
import org.arend.util.LongName
import org.arend.util.mapFirstNotNull
import java.util.Collections.singletonList

interface AbstractRefactoringAction {
    fun execute(editor: Editor?)
}

class ImportFileAction(private val importFile: ArendFile, private val currentFile: ArendFile, private val usingList: List<String>?) : AbstractRefactoringAction {
    override fun toString() = "Import file " + importFile.fullName

    private fun importFileCanBeFound(): Boolean {
        val modulePath = importFile.modulePath ?: return false
        val conf = currentFile.libraryConfig ?: return false
        return conf.availableConfigs.mapFirstNotNull { it.findArendFile(modulePath) } == importFile
    }

    fun isValid() = importFileCanBeFound() || isPrelude(importFile)

    override fun execute(editor: Editor?) {
        val factory = ArendPsiFactory(importFile.project)
        val fullName = importFile.modulePath?.toString() ?: return

        var anchor: PsiElement = currentFile
        val relativePosition = if (currentFile.children.isEmpty()) PositionKind.INSIDE_EMPTY_ANCHOR else {
            anchor = currentFile.children[0]
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

            if (after) PositionKind.AFTER_ANCHOR else PositionKind.BEFORE_ANCHOR
        }

        addStatCmd(factory, ArendPsiFactory.StatCmdKind.IMPORT, fullName, usingList?.map { Pair(it, null) }?.toList(), RelativePosition(relativePosition, anchor))
    }
}

class AddIdToUsingAction(private val statCmd: ArendStatCmd, private val idList: List<Pair<String, String?>>) : AbstractRefactoringAction {
    val insertedNsIds = ArrayList<ArendNsId>()

    override fun toString(): String = "Add ${usingListToString(idList)} to the \"using\" list of the namespace command `${statCmd.text}`"

    private fun addId(id: String, newName: String?, factory: ArendPsiFactory, using: ArendNsUsing): ArendNsId? {
        val nsIds = using.nsIdList
        var anchor = using.lparen
        var needsCommaBefore = false

        for (nsId in nsIds) {
            val idRefName = nsId.refIdentifier.referenceName
            val idDefName = nsId.defIdentifier?.name
            if (idRefName <= id) {
                anchor = nsId
                needsCommaBefore = true
            }
            if (id == idRefName && newName == idDefName) return null
        }

        val nsIdStr = if (newName == null) id else "$id \\as $newName"
        val nsCmd = factory.createImportCommand("Dummy (a,$nsIdStr)", ArendPsiFactory.StatCmdKind.IMPORT).statCmd
        val newNsUsing = nsCmd!!.nsUsing!!
        val nsId = newNsUsing.nsIdList[1]!!
        val comma = nsId.prevSibling

        if (anchor == null) {
            anchor = using.usingKw ?: error("Can't find anchor within namespace command")
            anchor = anchor.parent.addAfter(newNsUsing.lparen!!, anchor)
            anchor.parent.addBefore(factory.createWhitespace(" "), anchor)
            anchor.parent.addAfter(newNsUsing.rparen!!, anchor)
        }

        if (anchor != null) {
            if (!needsCommaBefore && nsIds.isNotEmpty()) anchor.parent.addAfter(comma, anchor)
            val insertedId = anchor.parent.addAfterWithNotification(nsId, anchor)
            if (needsCommaBefore) anchor.parent.addAfter(comma, anchor)
            return insertedId as ArendNsId
        }

        return null
    }

    override fun execute(editor: Editor?) {
        val factory = ArendPsiFactory(statCmd.project)
        val insertAnchor = statCmd.longName

        val actualNsUsing: ArendNsUsing? = statCmd.nsUsing
                ?: if (idList.any { it.second != null } && insertAnchor != null) {
                    val newUsing = factory.createImportCommand("Dummy \\using ()", ArendPsiFactory.StatCmdKind.IMPORT).statCmd!!.nsUsing!!
                    val insertedUsing = insertAnchor.parent.addAfterWithNotification(newUsing, insertAnchor)
                    insertAnchor.parent.addAfter(factory.createWhitespace(" "), insertAnchor)
                    insertedUsing as ArendNsUsing
                } else null

        val actualIdList = if (actualNsUsing?.usingKw != null) idList.filter { it.second != null } else idList
        if (actualNsUsing != null) insertedNsIds.addAll(actualIdList.mapNotNull { addId(it.first, it.second, factory, actualNsUsing) })
    }
}

class RemoveRefFromStatCmdAction(private val statCmd: ArendStatCmd?, val id: ArendRefIdentifier) : AbstractRefactoringAction {
    override fun toString(): String {
        val listType = when (id.parent) {
            is ArendStatCmd -> "\"hiding\" list"
            /* ArendNsUsing */ else -> "\"using\" list"
        }
        val name = if (statCmd != null) statCmdName(statCmd) else "???"
        return "Remove " + id.referenceName + " from " + name + " import's $listType"
    }

    override fun execute(editor: Editor?) {
        val elementToRemove = if (id.parent is ArendNsId) id.parent else id
        val parent = elementToRemove.parent

        val prevSibling = elementToRemove.findPrevSibling()
        val nextSibling = elementToRemove.findNextSibling()

        elementToRemove.deleteWithNotification()

        if (prevSibling?.node?.elementType == ArendElementTypes.COMMA) {
            prevSibling?.delete()
        } else if (prevSibling?.node?.elementType == ArendElementTypes.LPAREN) {
            if (nextSibling?.node?.elementType == ArendElementTypes.COMMA) {
                nextSibling?.delete()
            }
        }

        if (parent is ArendStatCmd && parent.refIdentifierList.isEmpty()) { // This means that we are removing something from "hiding" list
            parent.lparen?.delete()
            parent.rparen?.delete()
            parent.hidingKw?.delete()
        }

        val statCmd = if (parent is ArendStatCmd) parent else {
            val grandParent = parent.parent
            if (grandParent is ArendStatCmd) grandParent else null
        }

        if (statCmd != null && statCmd.openKw != null) { //Remove open command with null effect
            val nsUsing = statCmd.nsUsing
            if (nsUsing != null && nsUsing.usingKw == null && nsUsing.nsIdList.isEmpty()) {
                val statCmdStatement = statCmd.parent
                statCmdStatement.deleteWithNotification()
            }
        }
    }
}

class RenameReferenceAction(private val element: ArendReferenceElement, private val id: List<String>) : AbstractRefactoringAction {
    override fun toString(): String = "Rename " + element.text + " to " + LongName(id).toString()

    override fun execute(editor: Editor?) {
        val parent = element.parent
        val factory = ArendPsiFactory(element.project)

        when (element) {
            is ArendIPNameImplMixin -> if (parent is ArendLiteral) {
                val argumentStr = buildString {
                    if (id.size > 1) {
                        append(LongName(id.dropLast(1)))
                        append(".")
                    }
                    if (element.fixity == Fixity.INFIX || element.fixity == Fixity.POSTFIX) append("`")
                    append(id.last())
                    if (element.fixity == Fixity.INFIX) append("`")

                }
                val replacementLiteral = factory.createExpression(argumentStr).childOfType<ArendLiteral>()
                if (replacementLiteral != null) parent.replaceWithNotification(replacementLiteral)
            }
            else -> {
                val longNameStr = LongName(id).toString()
                val offset = element.textOffset
                val longName = factory.createLongName(longNameStr)
                when (parent) {
                    is ArendLongName -> {
                        parent.addRangeAfterWithNotification(longName.firstChild, longName.lastChild, element)
                        parent.deleteChildRangeWithNotification(parent.firstChild, element)
                    }
                    is ArendPattern -> element.replaceWithNotification(longName)
                }
                editor?.caretModel?.moveToOffset(offset + longNameStr.length)
            }
        }
    }
}

fun isPrelude(file: ArendFile) = file.modulePath == Prelude.MODULE_PATH && file.containingDirectory == null

fun statCmdName(statCmd: ArendStatCmd) =
        (statCmd.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? ArendFile)?.modulePath?.toString()
                ?: "???"

fun usingListToString(usingList: List<Pair<String, String?>>?): String {
    if (usingList == null) return ""
    val buffer = StringBuffer()
    buffer.append("(")
    for ((m, entry) in usingList.withIndex()) {
        buffer.append(entry.first + (if (entry.second == null) "" else " \\as ${entry.second}"))
        if (m < usingList.size - 1) buffer.append(", ")
    }
    buffer.append(")")
    return buffer.toString()
}

fun addStatCmd(factory: ArendPsiFactory, command: ArendPsiFactory.StatCmdKind, fullName: String, usingList: List<Pair<String, String?>>?, relativePosition: RelativePosition): PsiElement {
    val commandStatement = factory.createImportCommand(fullName + " " + usingListToString(usingList), command)
    val insertedStatement: PsiElement

    when (relativePosition.kind) {
        PositionKind.BEFORE_ANCHOR -> {
            insertedStatement = relativePosition.anchor.parent.addBeforeWithNotification(commandStatement, relativePosition.anchor)
            insertedStatement.parent.addAfter(factory.createWhitespace("\n"), insertedStatement)
        }
        PositionKind.AFTER_ANCHOR -> {
            insertedStatement = relativePosition.anchor.parent.addAfterWithNotification(commandStatement, relativePosition.anchor)
            insertedStatement.parent.addAfter(factory.createWhitespace("\n"), relativePosition.anchor)
            insertedStatement.parent.addAfter(factory.createWhitespace(" "), insertedStatement)
        }
        PositionKind.INSIDE_EMPTY_ANCHOR -> {
            insertedStatement = relativePosition.anchor.addWithNotification(commandStatement)
        }
    }
    return insertedStatement
}

fun addIdToUsing(groupMember: PsiElement?,
                 targetContainer: PsiElement,
                 targetContainerName: String,
                 renamings: List<Pair<String, String?>>,
                 factory: ArendPsiFactory,
                 relativePosition: RelativePosition): List<ArendNsId> {
    groupMember?.parent?.children?.filterIsInstance<ArendStatement>()?.map {
        val statCmd = it.statCmd
        if (statCmd != null/* && statCmd.nsUsing != null*/) {
            val ref = statCmd.longName?.refIdentifierList?.lastOrNull()
            if (ref != null) {
                val target = ref.reference?.resolve()
                if (target == targetContainer) {
                    val action = AddIdToUsingAction(statCmd, renamings)
                    action.execute(null)
                    return action.insertedNsIds
                }
            }
        }
    }

    if (targetContainerName.isNotEmpty()) {
        val insertedStatement = addStatCmd(factory, ArendPsiFactory.StatCmdKind.OPEN, targetContainerName, renamings, relativePosition)
        val statCmd = insertedStatement.childOfType<ArendStatCmd>()
        return statCmd?.nsUsing?.nsIdList ?: emptyList()
    }
    return emptyList()
}

fun getImportedNames(namespaceCommand: ArendStatCmd, shortName: String?): List<Pair<String, ArendNsId?>> {
    if (shortName == null) return emptyList()

    val nsUsing = namespaceCommand.nsUsing
    val isHidden = namespaceCommand.refIdentifierList.any { it.referenceName == shortName }

    if (nsUsing != null) {
        val resultList = ArrayList<Pair<String, ArendNsId?>>()

        for (nsId in nsUsing.nsIdList) {
            if (nsId.refIdentifier.text == shortName) {
                val defIdentifier = nsId.defIdentifier
                resultList.add(Pair(defIdentifier?.textRepresentation() ?: shortName, nsId))
            }
        }

        return resultList
    }

    return if (isHidden) emptyList() else singletonList(Pair(shortName, null))
}

fun deleteSuperfluousPatternParentheses(atomPattern: ArendAtomPattern) {
    if (atomPattern.lparen != null && atomPattern.rparen != null) {
        val pattern = atomPattern.parent as? ArendPattern
        if (pattern != null) {
            val parentAtom = pattern.parent as? ArendAtomPattern
            if (parentAtom != null && parentAtom.lparen != null && parentAtom.rparen != null) {
                parentAtom.replaceWithNotification(atomPattern)
            }
        }
    }

}