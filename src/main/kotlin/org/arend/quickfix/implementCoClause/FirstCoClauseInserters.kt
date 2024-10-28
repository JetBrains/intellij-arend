package org.arend.quickfix.implementCoClause

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.LBRACE
import org.arend.psi.ext.*
import org.arend.refactoring.moveCaretToEndOffset

fun makeFirstCoClauseInserter(element: PsiElement?): AbstractCoClauseInserter?  = when (element) {
            is ArendNewExpr -> element.argumentAppExpr?.let { NewExprInserter(element, it) }
            is ArendDefInstance -> ArendInstanceInserter(element)
            is ArendDefFunction -> FunctionDefinitionInserter(element)
            is CoClauseBase -> CoClauseInserter(element)
            is ArendCoClauseDef -> ArendFunctionalInserter(element)
            is ArendLongName -> element.ancestor<ArendNewExpr>()?.let { makeFirstCoClauseInserter(it)}
            is LeafPsiElement -> if (element.elementType == LBRACE) element.ancestor<ArendNewExpr>()?.let { makeFirstCoClauseInserter(it)} else null
            else -> null
        }

abstract class AbstractCoClauseInserter {
    abstract val coClausesList: List<CoClauseBase>
    abstract fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?)
}

class CoClauseInserter(private val coClause: CoClauseBase) : AbstractCoClauseInserter() {
    override val coClausesList get(): List<ArendLocalCoClause> = coClause.localCoClauseList

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        coClause.fatArrow?.delete()
        coClause.expr?.delete()

        val anchor = coClause.lbrace ?: run {
            val longName = coClause.longName!!
            val braces = factory.createPairOfBraces()
            longName.parent.addAfter(braces.second, longName)
            longName.parent.addAfter(braces.first, longName)
            longName.parent.addAfter(factory.createWhitespace(" "), longName) //separator between lBrace and coClause name
            longName.nextSibling.nextSibling
        }

        val sampleCoClause = factory.createLocalCoClause(name)
        anchor.parent.addAfter(sampleCoClause, anchor)
        moveCaretToEndOffset(editor, anchor.nextSibling)

        anchor.parent.addAfter(factory.createWhitespace("\n "), anchor)
    }
}

open class ArendFunctionalInserter(private val definition: ArendFunctionDefinition<*>) : AbstractCoClauseInserter() {
    override val coClausesList get(): List<ArendCoClause> = definition.body?.coClauseList ?: emptyList()

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        val body = definition.body
        if (body != null) {
            val sampleCoClause = factory.createCoClause(name)
            val samplePipe = sampleCoClause.findPrevSibling()!!
            val anchor = body.coClauseList.lastOrNull() ?: body.lbrace ?: body.cowithKw
            val insertedClause = if (anchor != null) body.addAfter(sampleCoClause, anchor) else body.add(sampleCoClause)
            body.addBefore(factory.createWhitespace("\n "), insertedClause)
            body.addBefore(samplePipe, insertedClause)

            if (insertedClause != null) moveCaretToEndOffset(editor, insertedClause)
        }
    }
}

class FunctionDefinitionInserter(private val functionDefinition: ArendDefFunction) : ArendFunctionalInserter(functionDefinition) {
    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        var functionBody = functionDefinition.body
        if (functionBody == null) {
            val functionBodySample = factory.createCoClauseInFunction(name).parent as ArendFunctionBody
            functionBody = functionDefinition.addAfter(functionBodySample, functionDefinition.children.last()) as ArendFunctionBody
            functionDefinition.addBefore(factory.createWhitespace("\n "), functionBody)
            moveCaretToEndOffset(editor, functionBody.lastChild)
        } else super.insertFirstCoClause(name, factory, editor)
    }
}

class ArendInstanceInserter(private val instance: ArendDefInstance) : ArendFunctionalInserter(instance) {
    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        var instanceBody = instance.body
        if (instanceBody == null) {
            val instanceBodySample = factory.createCoClause(name).parent as ArendFunctionBody
            val anchor = instance.returnExpr ?: instance.defIdentifier
            instanceBody = instance.addAfter(instanceBodySample, anchor) as ArendFunctionBody
            instance.addBefore(factory.createWhitespace("\n  "), instanceBody)
            moveCaretToEndOffset(editor, instanceBody.lastChild)
        } else super.insertFirstCoClause(name, factory, editor)
    }
}

class NewExprInserter(private val newExpr: ArendNewExpr, private val argumentAppExpr: ArendArgumentAppExpr) : AbstractCoClauseInserter() {
    override val coClausesList get(): List<ArendLocalCoClause> = newExpr.localCoClauseList

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        val anchor = newExpr.lbrace ?: run {
            val braces = factory.createPairOfBraces()
            argumentAppExpr.parent.addAfter(braces.second, argumentAppExpr)
            argumentAppExpr.parent.addAfter(braces.first, argumentAppExpr)
            argumentAppExpr.parent.addAfter(factory.createWhitespace(" "), argumentAppExpr) //separator between name and lbrace
            argumentAppExpr.nextSibling.nextSibling
        }

        val sampleCoClause = factory.createLocalCoClause(name)
        anchor.parent.addAfter(sampleCoClause, anchor)

        moveCaretToEndOffset(editor, anchor.nextSibling)
        anchor.parent.addAfter(factory.createWhitespace("\n"), anchor)
    }
}