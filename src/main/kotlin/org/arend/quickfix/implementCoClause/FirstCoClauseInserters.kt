package org.arend.quickfix.implementCoClause

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.arend.psi.*
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.ArendNewExprImplMixin
import org.arend.refactoring.moveCaretToEndOffset

fun makeFirstCoClauseInserter(element: PsiElement?) = when (element) {
            is ArendNewExprImplMixin -> element.argumentAppExpr?.let { NewExprInserter(element, it) }
            is ArendDefInstance -> ArendInstanceInserter(element)
            is ArendDefFunction -> FunctionDefinitionInserter(element)
            is CoClauseBase -> CoClauseInserter(element)
            else -> null
        }

abstract class AbstractCoClauseInserter {
    abstract val coClausesList: List<ArendCoClause>
    abstract fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?)
}

class CoClauseInserter(private val coClause: CoClauseBase) : AbstractCoClauseInserter() {
    override val coClausesList get(): List<ArendCoClause> = coClause.coClauseList

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        coClause.fatArrow?.deleteWithNotification()
        coClause.expr?.deleteWithNotification()

        val anchor = coClause.lbrace ?: run {
            val longName = coClause.longName!!
            val braces = factory.createPairOfBraces()
            longName.parent.addAfter(braces.second, longName)
            longName.parent.addAfter(braces.first, longName)
            longName.parent.addAfter(factory.createWhitespace(" "), longName) //separator between lBrace and coClause name
            longName.nextSibling.nextSibling
        }

        val sampleCoClause = factory.createCoClause(name)
        anchor.parent.addAfterWithNotification(sampleCoClause, anchor)
        moveCaretToEndOffset(editor, anchor.nextSibling)

        anchor.parent.addAfter(factory.createWhitespace("\n"), anchor)
    }
}

abstract class ArendFunctionalInserter(private val definition: ArendFunctionalDefinition) : AbstractCoClauseInserter() {
    override val coClausesList get(): List<ArendCoClause> = definition.body?.coClauseList ?: emptyList()

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        val body = definition.body
        if (body != null) {
            val sampleCoClause = factory.createCoClause(name)
            val anchor = when {
                body.coClauseList.isNotEmpty() -> body.coClauseList.last()
                body.lbrace != null -> body.lbrace
                body.cowithKw != null -> body.cowithKw
                else -> null
            }
            val insertedClause = if (anchor != null) body.addAfterWithNotification(sampleCoClause, anchor) else body.add(sampleCoClause)
            body.addBefore(factory.createWhitespace("\n"), insertedClause)
            if (insertedClause != null) moveCaretToEndOffset(editor, insertedClause)
        }
    }
}

class FunctionDefinitionInserter(private val functionDefinition: ArendDefFunction) : ArendFunctionalInserter(functionDefinition) {
    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        var functionBody = functionDefinition.functionBody
        if (functionBody == null) {
            val functionBodySample = factory.createCoClauseInFunction(name).parent as ArendFunctionBody
            functionBody = functionDefinition.addAfterWithNotification(functionBodySample, functionDefinition.children.last()) as ArendFunctionBody
            functionDefinition.addBefore(factory.createWhitespace("\n"), functionBody)
            moveCaretToEndOffset(editor, functionBody.lastChild)
        } else super.insertFirstCoClause(name, factory, editor)
    }
}

class ArendInstanceInserter(private val instance: ArendDefInstance) : ArendFunctionalInserter(instance) {
    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        var instanceBody = instance.instanceBody
        if (instanceBody == null) {
            val instanceBodySample = factory.createCoClause(name).parent as ArendInstanceBody
            val anchor = if (instance.returnExpr != null) instance.returnExpr else instance.defIdentifier
            instanceBody = instance.addAfterWithNotification(instanceBodySample, anchor) as ArendInstanceBody
            instance.addBefore(factory.createWhitespace("\n"), instanceBody)
            moveCaretToEndOffset(editor, instanceBody.lastChild)
        } else super.insertFirstCoClause(name, factory, editor)
    }
}

class NewExprInserter(private val newExpr: ArendNewExprImplMixin, private val argumentAppExpr: ArendArgumentAppExpr) : AbstractCoClauseInserter() {
    override val coClausesList get(): List<ArendCoClause> = newExpr.coClauseList

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        val anchor = newExpr.lbrace ?: run {
            val braces = factory.createPairOfBraces()
            argumentAppExpr.parent.addAfter(braces.second, argumentAppExpr)
            argumentAppExpr.parent.addAfter(braces.first, argumentAppExpr)
            argumentAppExpr.parent.addAfter(factory.createWhitespace(" "), argumentAppExpr) //separator between name and lbrace
            argumentAppExpr.nextSibling.nextSibling
        }

        val sampleCoClause = factory.createCoClause(name)
        anchor.parent.addAfterWithNotification(sampleCoClause, anchor)

        moveCaretToEndOffset(editor, anchor.nextSibling)
        anchor.parent.addAfter(factory.createWhitespace("\n"), anchor)
    }
}