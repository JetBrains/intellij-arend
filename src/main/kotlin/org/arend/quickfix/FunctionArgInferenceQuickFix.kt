package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.moveCaretToEndOffset
import org.arend.resolving.ArendReferenceBase
import org.arend.typechecking.error.local.inference.FunctionArgInferenceError
import org.arend.util.ArendBundle

class FunctionArgInferenceQuickFix(
        private val cause: SmartPsiElementPointer<PsiElement>,
        private val error: FunctionArgInferenceError
) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.argument.inference.parameter")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        var element: PsiElement? = cause.element
        while (element !is ArendArgumentAppExpr) {
            element = element?.parent
            if (element == null) {
                return
            }
        }

        val arendDefFunction = ArendReferenceBase.createArendLookUpElement(error.definition.referable, null, false, null, false, "")!!.`object` as ArendDefFunction
        val universes = mutableListOf<String>()

        val functionSignature = getFunctionSignature(arendDefFunction, universes)

        val arguments = element.children.toMutableList().apply {
            removeFirst()
        }

        val missedIndexed = mutableListOf<Int>()
        val definedTypes = mutableSetOf<String>()

        findMissedIndexesAndDefinedTypes(functionSignature, arguments, universes, missedIndexed, definedTypes)

        val psiFactory = ArendPsiFactory(project)
        val undefinedType = psiFactory.createExpression("foo {{?}}").childOfType<ArendImplicitArgument>()!!
        val definedType = psiFactory.createExpression("foo {_}").childOfType<ArendImplicitArgument>()!!
        val whiteSpace = psiFactory.createWhitespace(" ")

        var firstMissedUndefinedIndex: Int? = null
        missedIndexed.forEach {
            val curChildrenSize = element.children.size
            if (definedTypes.contains(functionSignature[it].first)) {
                element.addAfter(definedType, element.children[it].nextElement)
            } else {
                element.addAfter(undefinedType, element.children[it].nextElement)
                if (firstMissedUndefinedIndex == null) {
                    firstMissedUndefinedIndex = it
                }
            }
            if (it + 1 < curChildrenSize) {
                element.addAfter(whiteSpace, element.children[it + 1])
            }
        }

        if (editor != null && firstMissedUndefinedIndex != null) {
            moveCaretToEndOffset(editor, element.children[firstMissedUndefinedIndex!! + 1])
        }
    }

    private fun getFunctionSignature(arendDefFunction: ArendDefFunction, universes: MutableList<String>): List<Pair<String, Expression>> =
            arendDefFunction.parameters.map {
                val arguments = it.getChildrenOfType<ArendIdentifierOrUnknown>()
                val type = it.childOfType<ArendNewExpr>()?.getChildOfType<ArendArgumentAppExpr>()
                if (type == null) {
                    arguments.map { argument ->
                        universes.add(argument.text)
                        Pair(argument.text, Expression.UNIVERSE)
                    }
                } else {
                    arguments.map { Pair(type.text, Expression.ARGUMENT) }
                }
            }.flatten()

    private fun findMissedIndexesAndDefinedTypes(
            functionSignature: List<Pair<String, Expression>>,
            arguments: List<PsiElement>,
            universes: MutableList<String>,
            missedIndexed: MutableList<Int>,
            definedTypes: MutableSet<String>
    ) {
        var index = 0
        repeat(functionSignature.size) {
            if (it == error.index - 1) {
                missedIndexed.add(it)
                return@repeat
            }
            val (type, expression) = functionSignature[it]
            if (expression == Expression.ARGUMENT && index < arguments.size && arguments[index] is ArendAtomArgument && universes.contains(type)) {
                definedTypes.add(type)
            }
            if (expression == Expression.UNIVERSE && index < arguments.size && arguments[index] is ArendAtomArgument) {
                missedIndexed.add(it)
            } else {
                index++
            }
        }
    }

    private enum class Expression {
        UNIVERSE,
        ARGUMENT
    }
}
