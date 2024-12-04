package org.arend.refactoring

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.arend.core.definition.Definition
import org.arend.ext.core.context.CoreBinding
import org.arend.ext.core.context.CoreParameter
import org.arend.ext.core.expr.CoreExpression
import org.arend.ext.core.expr.CoreReferenceExpression
import org.arend.ext.module.LongName
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.local.ElimScope
import org.arend.naming.scope.local.LocalListScope
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.psi.ext.ArendGroup
import org.arend.psi.ext.ArendDefFunction
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.settings.ArendSettings
import org.arend.term.Fixity
import org.arend.term.NamespaceCommand
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.ConcreteExpressionVisitor
import org.arend.typechecking.TypeCheckingService
import org.arend.util.getBounds
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.util.Collections.singletonList
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.max

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
        if (id == idRefName && newName == idDefName) return nsId
    }

    val nsIdStr = if (newName == null) id else "$id \\as $newName"
    val nsCmd = factory.createImportCommand("Dummy (a,$nsIdStr)", ArendPsiFactory.StatCmdKind.IMPORT).statCmd
    val newNsUsing = nsCmd!!.nsUsing!!
    val nsId = newNsUsing.nsIdList[1]
    val comma = nsId.prevSibling

    if (anchor == null) {
        anchor = using.usingKw ?: error("Can't find anchor within namespace command")
        anchor = anchor.parent.addAfter(newNsUsing.lparen!!, anchor)
        anchor.parent.addBefore(factory.createWhitespace(" "), anchor)
        anchor.parent.addAfter(newNsUsing.rparen!!, anchor)
    }

    if (anchor != null) {
        if (!needsCommaBefore && nsIds.isNotEmpty()) anchor.parent.addAfter(comma, anchor)
        val insertedId = anchor.parent.addAfter(nsId, anchor)
        if (needsCommaBefore) anchor.parent.addAfter(comma, anchor)
        return insertedId as ArendNsId
    }

    return null
}

fun doAddIdToUsing(statCmd: ArendStatCmd, idList: List<Pair<String, String?>>, forceUsing: Boolean = false): Pair<ArrayList<ArendNsId>, Boolean /* true if success */> {
    val insertedNsIds = ArrayList<ArendNsId>()
    val factory = ArendPsiFactory(statCmd.project)
    val insertAnchor = statCmd.longName
    val usingBlockRequired = idList.any { it.second != null } || forceUsing

    val actualNsUsing: ArendNsUsing? = statCmd.nsUsing
            ?: if (usingBlockRequired && insertAnchor != null) {
                val newUsing = factory.createImportCommand("Dummy \\using ()", ArendPsiFactory.StatCmdKind.IMPORT).statCmd!!.nsUsing!!
                val insertedUsing = insertAnchor.parent.addAfter(newUsing, insertAnchor)
                insertAnchor.parent.addAfter(factory.createWhitespace(" "), insertAnchor)
                insertedUsing as ArendNsUsing
            } else null

    if (actualNsUsing != null) insertedNsIds.addAll(idList.mapNotNull { addId(it.first, it.second, factory, actualNsUsing) })
    return Pair(insertedNsIds, insertedNsIds.isNotEmpty() || !usingBlockRequired && actualNsUsing == null)
}

private fun addIdToHiding(refs: List<ArendRefIdentifier>, startAnchor: PsiElement, name: String, factory: ArendPsiFactory): ArendRefIdentifier {
    var anchor = startAnchor
    var needsComma = false
    for (ref in refs) {
        if (ref.referenceName <= name) {
            needsComma = true
            anchor = ref
        }
        if (ref.referenceName == name) return ref
    }
    val statCmd = factory.createFromText("\\import Foo \\hiding (bar, $name)")?.descendantOfType<ArendStatCmd>()
    val ref = statCmd!!.refIdentifierList[1]
    val comma = ref.findPrevSibling()!!
    if (needsComma) anchor = anchor.parent.addAfter(comma, anchor)
    val insertedRef = anchor.parent.addAfter(ref, anchor) as ArendRefIdentifier
    if (!needsComma && insertedRef.findNextSibling() is ArendRefIdentifier) anchor.parent.addAfter(comma, insertedRef)
    return insertedRef
}

fun doAddIdToHiding(statCmd: ArendStatCmd, idList: List<String>) : List<ArendRefIdentifier> {
    val factory = ArendPsiFactory(statCmd.project)
    val statCmdSample = factory.createFromText("\\import Foo \\hiding (lol)")?.descendantOfType<ArendStatCmd>()
    if (statCmd.hidingKw == null) statCmd.addAfter(statCmdSample!!.hidingKw!!, statCmd.nsUsing ?: statCmd.longName)
    if (statCmd.lparen == null) {
        val pop = factory.createPairOfParens()
        val anchor = statCmd.addAfter(pop.first, statCmd.hidingKw)
        statCmd.addAfter(pop.second, anchor)
    }
    val lparen = statCmd.lparen
    val result = ArrayList<ArendRefIdentifier>()
    if (lparen != null) for (id in idList) result.add(addIdToHiding(statCmd.refIdentifierList, lparen, id, factory))
    return result
}

fun doRemoveRefFromStatCmd(id: ArendRefIdentifier, deleteEmptyCommands: Boolean = true) {
    val elementToRemove = if (id.parent is ArendNsId) id.parent else id
    val parent = elementToRemove.parent

    val prevSibling = elementToRemove.findPrevSibling()
    val nextSibling = elementToRemove.findNextSibling()

    elementToRemove.delete()

    if (prevSibling?.node?.elementType == COMMA) {
        prevSibling?.delete()
    } else if (prevSibling?.node?.elementType == LPAREN) {
        if (nextSibling?.node?.elementType == COMMA) {
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

    if (statCmd?.openKw != null && deleteEmptyCommands) { //Remove open command with null effect
        val nsUsing = statCmd.nsUsing
        if (nsUsing != null && nsUsing.usingKw == null && nsUsing.nsIdList.isEmpty()) {
            val statCmdStatement = statCmd.parent
            statCmdStatement.delete()
        }
    }
}

class RenameReferenceAction (private val element: ArendReferenceElement,
                             private val newName: List<String>,
                             private val target: PsiLocatedReferable? = null,
                             private val useOpen : Boolean = service<ArendSettings>().autoImportWriteOpenCommands) {
    override fun toString(): String = "Rename " + element.text + " to " + LongName(newName).toString()

    fun execute(editor: Editor?) {
        val parent = element.parent
        val factory = ArendPsiFactory(element.project)
        val id = if (newName.size > 1 && target != null && useOpen &&
            doAddIdToOpen(factory, newName, element, target)) singletonList(newName.last()) else newName
        val needsModification = element.longName != id

        when (element) {
            is ArendIPName -> if (parent is ArendLiteral) {
                if (!needsModification) return
                val argumentStr = buildString {
                    if (id.size > 1) {
                        append(LongName(id.dropLast(1)))
                        append(".")
                    }
                    if (element.fixity == Fixity.INFIX || element.fixity == Fixity.POSTFIX) append("`")
                    append(id.last())
                    if (element.fixity == Fixity.INFIX) append("`")

                }
                val replacementLiteral = factory.createExpression(argumentStr).descendantOfType<ArendLiteral>()
                if (replacementLiteral != null) parent.replace(replacementLiteral)
            }
            else -> {
                val longNameStr = LongName(id).toString()
                val longNameStartOffset = element.parent.textOffset
                val relativePosition = max(0, (editor?.caretModel?.offset ?: 0) - longNameStartOffset)
                val offset = max(0, relativePosition + LongName(id).toString().length - LongName(element.longName).toString().length)

                val longName = factory.createLongName(longNameStr)
                if (needsModification) {
                    when (parent) {
                        is ArendLongName -> {
                            parent.addRangeAfter(longName.firstChild, longName.lastChild, element)
                            parent.deleteChildRange(parent.firstChild, element)
                        }
                        is ArendPattern -> element.replace(longName)
                    }
                    editor?.caretModel?.moveToOffset(longNameStartOffset + offset)
                }
            }
        }
    }
}

fun isPrelude(file: ArendFile) = file.generatedModuleLocation == Prelude.MODULE_LOCATION

fun getCorrectPreludeItemStringReference(project: Project, location: ArendCompositeElement, preludeItem: Definition): String {
    //Notice that this method may modify PSI (by writing "import Prelude" in the file header)
    val itemName = preludeItem.name
    val itemReferable = project.service<TypeCheckingService>().preludeScope.resolveName(itemName)
    val data = ResolveReferenceAction.getTargetName(itemReferable as PsiLocatedReferable, location)
    data.second?.execute()
    return data.first
}

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

fun findPlaceForNsCmd(currentFile: ArendFile, fileToImport: LongName): RelativePosition =
        if (currentFile.children.isEmpty()) RelativePosition(PositionKind.INSIDE_EMPTY_ANCHOR, currentFile) else {
            var anchor: PsiElement = currentFile.children[0]
            val fullName = fileToImport.toString()
            var after = false

            val currFileCommands = currentFile.statements.mapNotNull { it.namespaceCommand }.filter { it.importKw != null }
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

            RelativePosition(if (after) PositionKind.AFTER_ANCHOR else PositionKind.BEFORE_ANCHOR, anchor)
        }

fun createStatCmdStatement(factory: ArendPsiFactory, fullName: String, usingList: List<Pair<String, String?>>?, kind: ArendPsiFactory.StatCmdKind) =
        factory.createImportCommand(fullName + " " + usingListToString(usingList), kind)

fun addStatCmd(factory: ArendPsiFactory,
               commandStatement: ArendStat,
               relativePosition: RelativePosition): ArendStatCmd? {
    val insertedStatement: PsiElement

    when (relativePosition.kind) {
        PositionKind.BEFORE_ANCHOR -> {
            insertedStatement = relativePosition.anchor.parent.addBefore(commandStatement, relativePosition.anchor)
            insertedStatement.parent.addAfter(factory.createWhitespace("\n"), insertedStatement)
        }
        PositionKind.AFTER_ANCHOR -> {
            insertedStatement = relativePosition.anchor.parent.addAfter(commandStatement, relativePosition.anchor)
            insertedStatement.parent.addAfter(factory.createWhitespace("\n"), relativePosition.anchor)
            insertedStatement.parent.addAfter(factory.createWhitespace(" "), insertedStatement)
        }
        PositionKind.INSIDE_EMPTY_ANCHOR -> {
            insertedStatement = relativePosition.anchor.add(commandStatement)
        }
    }
    return (insertedStatement as ArendStat).statCmd
}

fun doAddIdToOpen(psiFactory: ArendPsiFactory, openedName: List<String>, positionInFile: ArendCompositeElement, elementReferable: PsiLocatedReferable, instanceMode: Boolean = false): Boolean {
    val enclosingDefinition = positionInFile.ancestor<ArendDefinition<*>>()
    val scope = enclosingDefinition?.scope
    val shortName = openedName.last()
    if (scope != null && scope.resolveName(shortName) == elementReferable) return true

    // Find uppermost level container
    val mySourceContainer =  if (!instanceMode) enclosingDefinition?.containingFile as? ArendGroup else enclosingDefinition?.parentGroup

    if ((scope != null && scope.resolveName(shortName) == null || instanceMode) && mySourceContainer != null) {
        val anchor = mySourceContainer.statements.lastOrNull { it.namespaceCommand?.kind == NamespaceCommand.Kind.OPEN }?.let {RelativePosition(PositionKind.AFTER_ANCHOR, it)}
            ?: mySourceContainer.statements.lastOrNull {it.namespaceCommand != null}?.let{ RelativePosition(PositionKind.AFTER_ANCHOR, it) }
            ?: if (mySourceContainer is ArendFile || mySourceContainer.statements.size > 1) RelativePosition(PositionKind.BEFORE_ANCHOR, mySourceContainer.statements.first()) else
                getAnchorInAssociatedModule(psiFactory, mySourceContainer, headPosition = true)?.let{ RelativePosition(PositionKind.AFTER_ANCHOR, it) }

        if (anchor != null) {
            val targetContainer = when (elementReferable) {
                is ArendGroup -> elementReferable.parentGroup
                else -> elementReferable.typecheckable.let { if (it is ArendGroup) it.parentGroup else it }
            }
            if (openedName.size > 1 && targetContainer != null) {
                val containingFile = positionInFile.containingFile as? ArendFile
                val openPrefix = (if (containingFile != null) {
                    val locationData = LocationData.createLocationData(targetContainer)
                    val data = locationData?.let{ calculateReferenceName(it, containingFile, mySourceContainer) }
                    data?.first?.execute()
                    if (data != null) LongName(data.second).toString() else null
                } else null) ?: LongName(openedName.subList(0, openedName.size - 1)).toString()
                return addIdToUsing(mySourceContainer, targetContainer, openPrefix, singletonList(Pair(openedName.last(), null)), psiFactory, anchor).second
            }

        }
    }
    return false
}

fun addIdToUsing(groupMember: PsiElement?,
                 targetContainer: PsiElement,
                 targetContainerName: String,
                 renamings: List<Pair<String, String?>>,
                 factory: ArendPsiFactory,
                 relativePosition: RelativePosition): Pair<List<ArendNsId>, Boolean> {
    (groupMember?.ancestor<ArendGroup>())?.statements?.map { stat ->
        val statCmd = stat.namespaceCommand
        if (statCmd != null) {
            val ref = statCmd.longName?.refIdentifierList?.lastOrNull()
            if (ref != null) {
                val target = ref.reference.resolve()
                if (target == targetContainer)
                    return doAddIdToUsing(statCmd, renamings)
            }
        }
    }

    if (targetContainerName.isNotEmpty()) {
        val statCmd = addStatCmd(factory,
                createStatCmdStatement(factory, targetContainerName, renamings, ArendPsiFactory.StatCmdKind.OPEN),
                relativePosition)
        val nsIds = statCmd?.nsUsing?.nsIdList ?: emptyList()
        return Pair(nsIds, nsIds.isNotEmpty())
    }
    return Pair(emptyList(), false)
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

fun deleteSuperfluousPatternParentheses(pattern: ArendPattern) {
    val parent = pattern.parent as? ArendPattern ?: return
    if (parent.isTuplePattern && parent.sequence.size == 1) {
        parent.replace(pattern)
    }

}

fun moveCaretToEndOffset(editor: Editor?, anchor: PsiElement) {
    if (editor != null) {
        editor.caretModel.moveToOffset(anchor.textRange.endOffset)
        IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
    }
}

fun moveCaretToStartOffset(editor: Editor?, anchor: PsiElement) {
    if (editor != null) {
        editor.caretModel.moveToOffset(anchor.textRange.startOffset)
        IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
    }
}

fun getAllBindings(psi: PsiElement, stopAtWhere: Boolean = true): Set<String> {
    val result = mutableSetOf<String>()
    psi.accept(object : PsiRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element is ArendReferenceElement) result.add(element.referenceName)
            if (element is ArendWhere && stopAtWhere) return
            super.visitElement(element)
        }
    })
    return result
}

fun getClassifyingField(classDef: ArendDefClass): ArendFieldDefIdentifier? {
    fun doGetClassifyingField(classDef: ArendDefClass, visitedClasses: MutableSet<ArendDefClass>): ArendFieldDefIdentifier? {
        if (!visitedClasses.add(classDef) || classDef.isRecord) return null

        for (ancestor in classDef.superClassReferences)
            if (ancestor is ArendDefClass)
                doGetClassifyingField(ancestor, visitedClasses)?.let { return it }

        classDef.fieldTeleList.firstOrNull { it.isClassifying }?.referableList?.firstOrNull()?.let { return it }
        classDef.fieldTeleList.firstOrNull { it.isExplicit }?.referableList?.firstOrNull()?.let{ return it }

        return null
    }

    return doGetClassifyingField(classDef, HashSet())
}

fun surroundWithBraces(psiFactory: ArendPsiFactory, defClass: ArendDefClass) {
    val braces = psiFactory.createPairOfBraces()
    defClass.addAfter(braces.first, defClass.defIdentifier)
    defClass.addAfter(psiFactory.createWhitespace(" "), defClass.defIdentifier)
    defClass.addAfter(braces.second, defClass.lastChild)

    fun surroundWithClassStat(startChild: PsiElement, endChild: PsiElement) {
        val insertedClassStat = defClass.addAfter(psiFactory.createClassStat(), endChild) as ArendClassStat
        val definition = insertedClassStat.definition!!
        insertedClassStat.addRangeAfter(startChild, endChild, definition)
        definition.delete()
        defClass.deleteChildRange(startChild, endChild)
    }

    var pipePosition: PsiElement? = null
    var currentChild: PsiElement? = defClass.firstChild
    while (currentChild != null) {
        val nextSibling = currentChild.nextSibling
        if (pipePosition != null && nextSibling != null &&
                (nextSibling.elementType == RBRACE || nextSibling.elementType == PIPE)) {
            surroundWithClassStat(pipePosition, currentChild)
            pipePosition = null
        }
        if (currentChild.elementType == PIPE) pipePosition = currentChild

        currentChild = nextSibling
    }
}

fun getAnchorInAssociatedModule(psiFactory: ArendPsiFactory, myTargetContainer: ArendGroup, headPosition: Boolean = false): PsiElement? {
    if (myTargetContainer !is ArendDefModule && myTargetContainer !is ArendDefinition<*>) return null

    val actualWhereImpl = getCompleteWhere(myTargetContainer, psiFactory)

    return (if (!headPosition) actualWhereImpl.statList.lastOrNull() else null) ?: actualWhereImpl.lbrace
}

fun getCompleteWhere(myTargetContainer: ArendGroup, psiFactory: ArendPsiFactory): ArendWhere {
    val oldWhereImpl = myTargetContainer.where
    val actualWhereImpl = if (oldWhereImpl != null) oldWhereImpl else {
        val localAnchor = myTargetContainer.lastChild
        val insertedWhere =
            myTargetContainer.addAfter(psiFactory.createWhere(), localAnchor) as ArendWhere
        val space = if (myTargetContainer is ArendFunctionDefinition<*>) "\n" else " "
        myTargetContainer.addAfter(psiFactory.createWhitespace(space), localAnchor)
        insertedWhere
    }

    if (actualWhereImpl.lbrace == null || actualWhereImpl.rbrace == null) {
        val braces = psiFactory.createPairOfBraces()
        if (actualWhereImpl.lbrace == null) {
            actualWhereImpl.addAfter(braces.first, actualWhereImpl.whereKw)
            actualWhereImpl.addAfter(psiFactory.createWhitespace(" "), actualWhereImpl.whereKw)
        }
        if (actualWhereImpl.rbrace == null) {
            actualWhereImpl.addAfter(braces.second, actualWhereImpl.lastChild)
        }
    }
    return actualWhereImpl
}

/**
 * @return a new let expression inserted psi tree
 */
fun ArendLetExpr.addNewClause(clause: String): ArendLetExpr {
    val letClauses = letClauses
    if (letClauses.isEmpty()) error("There are no empty let expressions in Arend")
    val clauseTextList = letClauses.map { it.text ?: "" } + listOf(clause)
    val exprText = expr?.text ?: ""
    val newLetRepresentation = "${firstRelevantChild!!.text} ${clauseTextList.joinToString("") { "\n | $it" }} \n\\in $exprText"
    val newLetPsi = ArendPsiFactory(project).createExpression(newLetRepresentation)
    return this.replace(newLetPsi) as ArendLetExpr
}

@Suppress("UNCHECKED_CAST")
fun <T : ArendCompositeElement> ArendGroup.addToWhere(elementToAdd: T): T {
    val where = getCompleteWhere(this, ArendPsiFactory(project))
    return where.addBefore(elementToAdd, where.rbrace) as T
}

fun addImplicitClassDependency(psiFactory: ArendPsiFactory, definition: PsiConcreteReferable, typeExpr: String, variable: Variable = VariableImpl("this"), anchor: PsiElement? = definition.nameIdentifier): String {
    val thisVarName = StringRenamer().generateFreshName(variable, getAllBindings(definition).map { VariableImpl(it) }.toList())

    val thisTele: PsiElement = when (definition) {
        is ArendFunctionDefinition<*> -> {
            psiFactory.createNameTele(thisVarName, typeExpr, false)
        }
        is ArendDefData -> {
            psiFactory.createTypeTele(thisVarName, typeExpr, false)
        }
        else -> throw IllegalStateException()
    }

    definition.addAfter(thisTele, anchor)
    definition.addAfter(psiFactory.createWhitespace(" "), anchor)

    return thisVarName
}

// Support of pattern-matching on idp in refactorings
enum class PatternMatchingOnIdpResult {INAPPLICABLE, DO_NOT_ELIMINATE, IDP}

fun admitsPatternMatchingOnIdp(expr: CoreExpression,
                               caseParameters: CoreParameter?,
                               eliminatedBindings: Set<CoreBinding>? = null): PatternMatchingOnIdpResult {
    val equality = expr.toEquality() ?: return PatternMatchingOnIdpResult.INAPPLICABLE
    val leftBinding = (equality.defCallArguments[1] as? CoreReferenceExpression)?.binding
    val rightBinding = (equality.defCallArguments[2] as? CoreReferenceExpression)?.binding
    val leftNotEliminated = eliminatedBindings == null || !eliminatedBindings.contains(leftBinding)
    val rightNotEliminated = eliminatedBindings == null || !eliminatedBindings.contains(rightBinding)
    var leftSideOk = caseParameters == null && leftBinding != null && leftNotEliminated
    var rightSideOk = caseParameters == null && rightBinding != null && rightNotEliminated
    var caseP = caseParameters
    while (caseP?.hasNext() == true) {
        if (caseP == leftBinding && leftNotEliminated) leftSideOk = true
        if (caseP == rightBinding && rightNotEliminated) rightSideOk = true
        caseP = caseP.next
    }
    return if ((leftSideOk || rightSideOk) && leftBinding != rightBinding)
        PatternMatchingOnIdpResult.IDP else PatternMatchingOnIdpResult.DO_NOT_ELIMINATE
}

// Binop util method plus auxiliary stuff

fun getFirstExplicitParameter(ref: Referable?, defaultName: String): String {
    val definition = ref?.underlyingReferable
    if (definition is Abstract.ParametersHolder) {
        val firstParameter = definition.parameters.firstOrNull { it.isExplicit }
        val firstReferable = firstParameter?.referableList?.firstOrNull()
        if (firstReferable is ArendDefIdentifier) return firstReferable.name
    }
    return defaultName
}

fun addImplicitArgAfter(psiFactory: ArendPsiFactory, anchor: PsiElement, argument: String, infixMode: Boolean) {
    val thisArgument = psiFactory.createExpression("foo {$argument}").descendantOfType<ArendImplicitArgument>()
    if (thisArgument != null) {
        if (anchor is ArendAtomFieldsAcc || infixMode) {
            anchor.parent?.addAfter(thisArgument, anchor)
            anchor.parent?.addAfter(psiFactory.createWhitespace(" "), anchor)
        } else if (anchor is ArendAtomArgument) {
            val oldLiteral = anchor.atomFieldsAcc.atom.literal
            val tuple = psiFactory.createExpression("(${anchor.text} {$argument})").descendantOfType<ArendTuple>()
            if (oldLiteral != null && tuple != null) oldLiteral.replace(tuple)
        }
    }
}

fun getTele(tele: PsiElement): List<ArendCompositeElement>? = when (tele) {
    is ArendNameTele -> tele.identifierOrUnknownList
    is ArendTypeTele -> tele.typedExpr?.identifierOrUnknownList
    is ArendFieldTele -> tele.referableList
    else -> null
}

fun splitTele(tele: PsiElement/* Name or Type tele */, index : Int) {
    var teleSize = getTele(tele)?.size
    if (teleSize != null) {
        if (index > 0) {
            val copy = tele.parent.addBefore(tele.copy(), tele)
            getTele(tele)?.let { it.first().parent.deleteChildRange(it.first(), it[index-1]) }
            getTele(copy)?.let { it.first().parent.deleteChildRange(it[index], it.last()) }
        }
        teleSize -= index
        if (teleSize > 1) {
            val copy = tele.parent.addAfter(tele.copy(), tele)
            getTele(tele)?.let { it.first().parent.deleteChildRange(it[1], it.last()) }
            getTele(copy)?.let { it[0].delete() }
        }
    }
}

fun surroundingTupleExpr(baseExpr: ArendExpr): ArendTupleExpr? =
        (baseExpr.parent as? ArendNewExpr)?.let { newExpr ->
            if (newExpr.appPrefix == null && newExpr.lbrace == null && newExpr.argumentList.isEmpty())
                newExpr.parent as? ArendTupleExpr else null
        }

fun unwrapParens(tuple: PsiElement?): ArendExpr? =
    when (tuple) {
        is ArendTuple -> tuple.tupleExprList.singleOrNull()?.exprIfSingle
        is ArendTypeTele -> tuple.type
        else -> throw IllegalArgumentException()
    }


fun transformPostfixToPrefix1(argumentOrFieldsAcc: PsiElement,
                              ipName: ArendIPName,
                              operatorConcrete: Concrete.Expression,
                              psiElements: List<PsiElement>,
                              textGetter: (PsiElement) -> String = { it -> it.text }): Pair<String, Boolean> {
    var resultingExpr = "${LongName(ipName.longName)} "
    val leadingElements = java.util.ArrayList<PsiElement>()
    val trailingElements = java.util.ArrayList<PsiElement>()
    var beforeLiteral = true
    for (element in psiElements) when {
        element == argumentOrFieldsAcc -> beforeLiteral = false
        beforeLiteral -> leadingElements.add(element)
        else -> trailingElements.add(element)
    }

    fun getElementsText(elements: ArrayList<PsiElement>): String = elements.fold("") { acc, element -> acc + textGetter.invoke(element) }.trim().let { text ->
        if (elements.filter { it !is PsiComment && it !is PsiWhiteSpace }.size > 1) "($text)" else text
    }

    val leadingText = getElementsText(leadingElements)
    val trailingText = getElementsText(trailingElements)

    val isLambda = leadingElements.size == 0

    if (isLambda) {
        val defaultLambdaName = "_x"
        val baseName = if (operatorConcrete is Concrete.AppExpression) {
            val function = operatorConcrete.function as? Concrete.ReferenceExpression
            getFirstExplicitParameter(function?.referent, defaultLambdaName)
        } else defaultLambdaName

        val operatorBindings = HashSet<String>()
        for (psi in psiElements) operatorBindings.addAll(getAllBindings(psi))
        val lambdaVarName = StringRenamer().generateFreshName(VariableImpl(baseName), operatorBindings.map { VariableImpl(it) }.toList())
        resultingExpr = "(\\lam $lambdaVarName => $resultingExpr$lambdaVarName $trailingText)"
    } else resultingExpr += "$leadingText $trailingText"

    return Pair(resultingExpr, isLambda)
}

fun transformPostfixToPrefix(psiFactory: ArendPsiFactory,
                             argumentOrFieldsAcc: PsiElement,
                             ipName: ArendIPName,
                             operatorConcrete: Concrete.Expression,
                             rangeData: HashMap<Concrete.SourceNode, TextRange>? = null,
                             rangeCallback: ((TextRange, Int) -> Unit)? = null): ArendArgumentAppExpr? {
    val argumentAppExpr = argumentOrFieldsAcc.parent as ArendArgumentAppExpr
    val nodes = argumentAppExpr.firstChild.siblings().map { it.node }.toList()
    val operatorRange = getBounds(operatorConcrete, nodes, rangeData)!!
    val psiElements = nodes.filter { operatorRange.contains(it.textRange) }.map { it.psi }
    val psiElementsRange = TextRange(psiElements.first().textRange.startOffset, psiElements.last().textRange.endOffset)
    val (resultingExpr, isLambda) = transformPostfixToPrefix1(argumentOrFieldsAcc, ipName, operatorConcrete, psiElements)

    val result: ArendExpr? = when {
        psiElements.size == nodes.size -> {
            val tupleExpr = surroundingTupleExpr(argumentAppExpr)
            val appExpr = psiFactory.createExpression(resultingExpr.trim()).descendantOfType<ArendArgumentAppExpr>()!!
            if (tupleExpr != null && tupleExpr.colon == null && isLambda) {
                val newPsi = appExpr.descendantOfType<ArendLamExpr>()!!
                rangeCallback?.invoke(argumentAppExpr.textRange, newPsi.textLength)
                argumentAppExpr.parent.replace(newPsi) as? ArendExpr
            } else {
                rangeCallback?.invoke(argumentAppExpr.textRange, appExpr.textLength)
                argumentAppExpr.replace(appExpr) as ArendArgumentAppExpr
            }
        }
        operatorRange.contains(nodes.first().textRange) -> {
            val atomFieldsAcc = psiFactory.createExpression("(${resultingExpr.trim()}) foo").descendantOfType<ArendAtomFieldsAcc>()!!
            rangeCallback?.invoke(psiElementsRange, atomFieldsAcc.textLength)
            val insertedExpr = argumentAppExpr.addAfter(atomFieldsAcc, psiElements.last())
            argumentAppExpr.deleteChildRange(psiElements.first(), psiElements.last())
            insertedExpr.descendantOfType<ArendArgumentAppExpr>()
        }
        else -> {
            val atom = psiFactory.createExpression("foo (${resultingExpr.trim()})").descendantOfType<ArendAtomArgument>()!!
            rangeCallback?.invoke(TextRange(psiElements.first().textRange.startOffset, psiElements.last().textRange.endOffset), atom.textLength)
            val insertedExpr = argumentAppExpr.addBefore(atom, psiElements.first())
            argumentAppExpr.deleteChildRange(psiElements.first(), psiElements.last())
            insertedExpr.descendantOfType<ArendArgumentAppExpr>()
        }
    }
    return if (isLambda) result?.descendantOfType(true) else result as ArendArgumentAppExpr
}

fun getPrec(psiElement: PsiElement?): ArendPrec? = when (psiElement) {
    is ArendFunctionDefinition<*> -> psiElement.prec
    is ArendDefData -> psiElement.prec
    is ArendDefClass -> psiElement.prec
    is ArendConstructor -> psiElement.prec
    is ArendNsId -> psiElement.prec
    is ArendClassField -> psiElement.prec
    else -> null
}

fun isInfix(prec: ArendPrec?): Boolean = ReferableBase.calcPrecedence(prec).isInfix

fun calculateOccupiedNames(occupiedNames: Collection<Variable>, parameterName: String?, nRecursiveBindings: Int) =
        if (nRecursiveBindings > 1 && !parameterName.isNullOrEmpty() && !Character.isDigit(parameterName.last()))
            occupiedNames.plus(VariableImpl(parameterName)) else occupiedNames

fun collectDefinedVariables(startElement: ArendCompositeElement): List<Variable> {
    val elementScope = startElement.scope

    val excluded: Set<Referable> = if (startElement is ArendCaseExpr) {
        startElement.caseArguments.mapNotNullTo(HashSet()) { caseArg ->
            caseArg.takeIf { it.elimKw != null }?.eliminatedReference?.let { ExpressionResolveNameVisitor.resolve(it.referent, elementScope) }
        }
    } else emptySet()

    val added: List<Referable?> = if ((startElement as? ArendDefFunction)?.body?.elim?.elimKw != null) {
        val eliminated = startElement.eliminatedExpressions.mapTo(HashSet()) { it.referenceName }
        startElement.parameters.flatMap { it.referableList }.filter { it != null && !eliminated.contains(it.refName) }
    } else emptyList()

    val scope = when {
        excluded.isNotEmpty() -> ElimScope(elementScope, excluded)
        added.isNotEmpty() -> LocalListScope(elementScope, added)
        else -> elementScope
    }
    return scope.elements.mapNotNull { if (it is GlobalReferable) null else VariableImpl(it.refName) }
}

/**
 * The purpose of this function is to insert a pair of parenthesis
 * on-demand when replacing expression.
 * @param deletedPsi one of the [PsiElement] that is going to be deleted
 * @param deletedConcrete the concrete expression that is going to be deleted
 * @param deleting the full range of everything needs to be deleted
 * @return the replaced expression, w/ or w/o the parenthesis
 */
fun replaceExprSmart(document: Document, deletedPsi: ArendCompositeElement, deletedConcrete: Concrete.Expression?, deleting: TextRange, aExpr: Abstract.Expression?, cExpr: Concrete.Expression?, inserting: String, reformatAfter : Boolean = true): String {
    assert(document.isWritable)
    document.deleteString(deleting.startOffset, deleting.endOffset)
    val psiFile = deletedPsi.containingFile
    val project = psiFile.project

    val correctDeletedPsi =
        if (deletedConcrete is Concrete.AppExpression || deletedConcrete is Concrete.BinOpSequenceExpression) {
            val argumentAppExpr = (deletedConcrete.data as? ArendCompositeElement)?.ancestor<ArendArgumentAppExpr>()
            if (argumentAppExpr != null && (deletedConcrete as? Concrete.BinOpSequenceExpression)?.clauses != null) {
                val newExpr = argumentAppExpr.ancestor<ArendNewExpr>()
                if (newExpr?.withBody != null) newExpr else argumentAppExpr
            } else argumentAppExpr
        } else deletedConcrete?.data as? ArendExpr
    val str = if (needParentheses(correctDeletedPsi ?: deletedPsi, deleting, aExpr, cExpr)) "($inserting)" else inserting
    document.insertString(deleting.startOffset, str)
    if (reformatAfter) {
        CodeStyleManager.getInstance(project).reformatText(psiFile, deleting.startOffset, deleting.startOffset + str.length)
    }
    return str
}

fun needParentheses(deletedPsi: ArendCompositeElement?, deletedRange: TextRange, aExpr: Abstract.Expression?, cExpr: Concrete.Expression?): Boolean {
    val insertedPrec = when {
        aExpr != null -> aExpr.accept(PrecVisitor, null)
        cExpr != null -> cExpr.accept(ConcretePrecVisitor, null)
        else -> MIN_PREC
    }

    // Expressions with the maximum precedence may be inserted anywhere without parentheses
    if (insertedPrec == MAX_PREC) {
        return false
    }

    // if the range differs, then we do not know where the expression will be inserted,
    // so we add parentheses to be sure the result is correct
    if (deletedPsi == null || (insertedPrec < APP_PREC && deletedRange != deletedPsi.textRange)) {
        return true
    }

    val deletedExpr = deletedPsi.ancestor<ArendExpr>()
    val deletedPrec = deletedExpr?.accept(PrecVisitor, null) ?: MAX_PREC

    // if the precedence of the inserted element equals to or larger than the precedence of the original element,
    // we do not need parentheses
    if (deletedPrec <= insertedPrec) {
        return false
    }

    // if parents of the deleted element do not contain any additional elements,
    // we can replace it with any expression without parentheses
    val topmostDeletedExpr = deletedExpr?.topmostEquivalentSourceNode
    if (topmostDeletedExpr is ArendNewExpr) {
        return false
    }

    // if the inserted element is application, then we can sometimes insert it without parentheses
    // so if it is not, we return true immediately
    if (insertedPrec < APP_PREC) {
        return true
    }

    // if the parent of the deleted element is a new expression, we can insert an application without parentheses
    if (topmostDeletedExpr?.parent is ArendNewExpr) {
        return false
    }

    // otherwise we need parentheses
    return true
}

fun argNeedsParentheses(it : Concrete.Expression): Boolean {
    val prec = it.accept(ConcretePrecVisitor, null)
    return prec <= APP_PREC
}

private const val MAX_PREC = 100
private const val APP_PREC = 10
private const val MIN_PREC = 0

private object PrecVisitor : AbstractExpressionVisitor<Void?, Int> {
    override fun visitReference(data: Any?, referent: Referable, fixity: Fixity?, pLevels: Collection<Abstract.LevelExpression>?, hLevels: Collection<Abstract.LevelExpression>?, params: Void?) =
        if (pLevels != null || hLevels != null) APP_PREC else MAX_PREC

    override fun visitUniverse(data: Any?, pLevelNum: Int?, hLevelNum: Int?, pLevel: Abstract.LevelExpression?, hLevel: Abstract.LevelExpression?, params: Void?) =
        if (pLevel != null || hLevel != null) APP_PREC else MAX_PREC

    override fun visitReference(data: Any?, referent: Referable, lp: Int, lh: Int, params: Void?) = APP_PREC
    override fun visitThis(data: Any?, params: Void?) = MAX_PREC
    override fun visitLam(data: Any?, parameters: Collection<Abstract.LamParameter>, body: Abstract.Expression?, params: Void?) = MIN_PREC
    override fun visitPi(data: Any?, parameters: Collection<Abstract.Parameter>, codomain: Abstract.Expression?, params: Void?) = MIN_PREC
    override fun visitApplyHole(data: Any?, params: Void?) = MAX_PREC
    override fun visitInferHole(data: Any?, params: Void?) = MAX_PREC
    override fun visitGoal(data: Any?, name: String?, expression: Abstract.Expression?, params: Void?) = MAX_PREC
    override fun visitTuple(data: Any?, fields: Collection<Abstract.Expression>, trailingComma: Any?, params: Void?) = MAX_PREC
    override fun visitSigma(data: Any?, parameters: Collection<Abstract.Parameter>, params: Void?) = MIN_PREC
    override fun visitBinOpSequence(data: Any?, left: Abstract.Expression, sequence: Collection<Abstract.BinOpSequenceElem>, params: Void?) = APP_PREC
    override fun visitCase(data: Any?, isSFunc: Boolean, evalKind: Abstract.EvalKind?, arguments: Collection<Abstract.CaseArgument>, resultType: Abstract.Expression?, resultTypeLevel: Abstract.Expression?, clauses: Collection<Abstract.FunctionClause>, params: Void?) = MIN_PREC
    override fun visitFieldAccs(data: Any?, expression: Abstract.Expression, fieldAccs: Collection<Int>, params: Void?) = MAX_PREC
    override fun visitClassExt(data: Any?, isNew: Boolean, evalKind: Abstract.EvalKind?, baseClass: Abstract.Expression?, coclausesData: Any?, implementations: MutableCollection<out Abstract.ClassFieldImpl>?, sequence: MutableCollection<out Abstract.BinOpSequenceElem>, clauses: Abstract.FunctionClauses?, params: Void?) = MIN_PREC
    override fun visitLet(data: Any?, isHave: Boolean, isStrict: Boolean, clauses: Collection<Abstract.LetClause>, expression: Abstract.Expression?, params: Void?) = MIN_PREC
    override fun visitNumericLiteral(data: Any?, number: BigInteger, params: Void?) = MAX_PREC
    override fun visitStringLiteral(data: Any?, unescapedString: String, params: Void?) = MAX_PREC
    override fun visitTyped(data: Any?, expr: Abstract.Expression, type: Abstract.Expression, params: Void?) = MIN_PREC
}

private object ConcretePrecVisitor : ConcreteExpressionVisitor<Void?, Int> {
    override fun visitReference(expr: Concrete.ReferenceExpression, params: Void?) =
        if (expr.pLevels != null || expr.hLevels != null) APP_PREC else MAX_PREC

    override fun visitUniverse(expr: Concrete.UniverseExpression, params: Void?) =
        if ((expr.pLevel == null || expr.pLevel is Concrete.NumberLevelExpression) && (expr.hLevel == null || expr.hLevel is Concrete.NumberLevelExpression || expr.hLevel is Concrete.InfLevelExpression)) MAX_PREC else APP_PREC

    override fun visitApp(expr: Concrete.AppExpression, params: Void?) = APP_PREC
    override fun visitFieldCall(expr: Concrete.FieldCallExpression, params: Void?) = MAX_PREC
    override fun visitThis(expr: Concrete.ThisExpression, params: Void?) = MAX_PREC
    override fun visitLam(expr: Concrete.LamExpression, params: Void?) = MIN_PREC
    override fun visitPi(expr: Concrete.PiExpression, params: Void?) = MIN_PREC
    override fun visitHole(expr: Concrete.HoleExpression, params: Void?) = MAX_PREC
    override fun visitGoal(expr: Concrete.GoalExpression, params: Void?) = MAX_PREC
    override fun visitTuple(expr: Concrete.TupleExpression, params: Void?) = MAX_PREC
    override fun visitSigma(expr: Concrete.SigmaExpression, params: Void?) = MIN_PREC
    override fun visitBinOpSequence(expr: Concrete.BinOpSequenceExpression, params: Void?) = APP_PREC
    override fun visitCase(expr: Concrete.CaseExpression, params: Void?) = MIN_PREC
    override fun visitEval(expr: Concrete.EvalExpression, params: Void?) = APP_PREC
    override fun visitProj(expr: Concrete.ProjExpression, params: Void?) = MAX_PREC
    override fun visitClassExt(expr: Concrete.ClassExtExpression, params: Void?) = MIN_PREC
    override fun visitNew(expr: Concrete.NewExpression, params: Void?) = MIN_PREC
    override fun visitLet(expr: Concrete.LetExpression, params: Void?) = MIN_PREC
    override fun visitNumericLiteral(expr: Concrete.NumericLiteral, params: Void?) = MAX_PREC
    override fun visitStringLiteral(expr: Concrete.StringLiteral?, params: Void?) = MAX_PREC
    override fun visitTyped(expr: Concrete.TypedExpression, params: Void?) = MIN_PREC
    override fun visitApplyHole(expr: Concrete.ApplyHoleExpression, params: Void?) = MAX_PREC
    override fun visitBox(expr: Concrete.BoxExpression?, params: Void?) = APP_PREC
}
