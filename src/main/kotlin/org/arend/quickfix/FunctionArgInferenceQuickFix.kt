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
        while (element !is ArendArgumentAppExpr && element !is ArendAtomArgument) {
            element = element?.parent
            if (element == null) {
                return
            }
        }

        val arendDefFunction = ArendReferenceBase.createArendLookUpElement(error.definition.referable, null, false, null, false, "")?.`object` as? ArendDefFunction
        val arendConstructor = ArendReferenceBase.createArendLookUpElement(error.definition.referable, null, false, null, false, "")?.`object` as? ArendConstructor

        val universes = mutableListOf<String>()

        val functionSignature = if (arendDefFunction != null) {
            getFunctionSignature(arendDefFunction, universes)
        } else if (arendConstructor != null) {
            getConstructorSignature(arendConstructor, universes)
        } else {
            emptyList()
        }

        val arguments = element.children.toMutableList().apply {
            removeFirst()
        }

        val missedIndexed = mutableListOf<Int>()
        val definedTypes = mutableSetOf<String>()

        findMissedIndexesAndDefinedTypes(functionSignature, arguments, universes, missedIndexed, definedTypes, element)

        val psiFactory = ArendPsiFactory(project)

        if (element is ArendAtomArgument) {
            val arendArgumentAppExpr = psiFactory.createExpression(element.text).childOfType<ArendArgumentAppExpr>()!!
            addMissedArguments(psiFactory, arendArgumentAppExpr, functionSignature, missedIndexed, definedTypes)

            val newArendAtomArgument = psiFactory.createExpression("foo (${arendArgumentAppExpr.text})").childOfType<ArendAtomArgument>()!!

            element.replace(newArendAtomArgument)
        } else {
            val firstMissedUndefinedIndex = addMissedArguments(psiFactory, element, functionSignature, missedIndexed, definedTypes)

            if (editor != null && firstMissedUndefinedIndex != null) {
                moveCaretToEndOffset(editor, element.children[firstMissedUndefinedIndex + 1])
            }
        }
    }

    private fun addMissedArguments(psiFactory: ArendPsiFactory, element: PsiElement, functionSignature: List<Triple<String, Expression, Boolean>>, missedIndexed: List<Int>, definedTypes: Set<String>): Int? {
        val undefinedImplicitType = psiFactory.createUndefinedImplicitType()
        val definedImplicitType = psiFactory.createDefinedImplicitType()
        val undefinedExplicitType = psiFactory.createUndefinedExplicitType()
        val definedExplicitType = psiFactory.createDefinedExplicitType()
        val whiteSpace = psiFactory.createWhitespace(" ")

        var firstMissedUndefinedIndex: Int? = null
        missedIndexed.forEach {
            val curChildrenSize = element.children.size

            if (element.children[it].nextSibling == null) {
                element.addAfter(whiteSpace, element.children[it])
            }

            val nextElement = element.children[it].nextElement
            val (type, _, isExplicit) = functionSignature[it]
            if (definedTypes.contains(type)) {
                if (isExplicit) {
                    element.addAfter(definedExplicitType, nextElement)
                } else {
                    element.addAfter(definedImplicitType, nextElement)
                }
            } else {
                if (isExplicit) {
                    element.addAfter(undefinedExplicitType, nextElement)
                } else {
                    element.addAfter(undefinedImplicitType, nextElement)
                }
                if (firstMissedUndefinedIndex == null) {
                    firstMissedUndefinedIndex = it
                }
            }
            if (it + 1 < curChildrenSize) {
                element.addAfter(whiteSpace, element.children[it + 1])
            }
        }
        return firstMissedUndefinedIndex
    }

    private fun getConstructorSignature(arendConstructor: ArendConstructor, universes: MutableList<String>): List<Triple<String, Expression, Boolean>> {
        val arendDefData = arendConstructor.parent.parent as ArendDefData
        return arendDefData.parameters.map { parameter ->
            val typedExpr = parameter.getChildOfType<ArendTypedExpr>() ?: return emptyList()

            val arguments = typedExpr.getChildrenOfType<ArendIdentifierOrUnknown>()
            val type = parameter.childOfType<ArendNewExpr>()?.getChildOfType<ArendArgumentAppExpr>()
            if (type == null) {
                arguments.map { argument ->
                    universes.add(argument.text)
                    Triple(argument.text, Expression.UNIVERSE, false)
                }
            } else {
                arguments.map { Triple(type.text, Expression.ARGUMENT, false) }
            }
        }.flatten()
    }

    private fun getFunctionSignature(arendDefFunction: ArendDefFunction, universes: MutableList<String>): List<Triple<String, Expression, Boolean>> =
            arendDefFunction.parameters.map { parameter ->
                val arguments = parameter.getChildrenOfType<ArendIdentifierOrUnknown>()
                val type = parameter.childOfType<ArendNewExpr>()?.getChildOfType<ArendArgumentAppExpr>()
                if (type == null) {
                    arguments.map { argument ->
                        universes.add(argument.text)
                        Triple(argument.text, Expression.UNIVERSE, parameter.isExplicit)
                    }
                } else {
                    arguments.map { Triple(type.text, Expression.ARGUMENT, parameter.isExplicit) }
                }
            }.flatten()

    private fun findMissedIndexesAndDefinedTypes(
            functionSignature: List<Triple<String, Expression, Boolean>>,
            arguments: List<PsiElement>,
            universes: MutableList<String>,
            missedIndexed: MutableList<Int>,
            definedTypes: MutableSet<String>,
            element: PsiElement
    ) {
        var index = 0
        val errorIndex = error.index - 1
        repeat(errorIndex) {
            if (index >= arguments.size) {
                missedIndexed.add(it)
                return@repeat
            }

            val argument = arguments[index]
            val (type, expression, isExplicit) = functionSignature[it]

            addDefinedType(expression, arguments[index], type, definedTypes, universes)

            if (expression == Expression.UNIVERSE && UNIVERSE_LITERALS.contains(argument.text)) {
                missedIndexed.add(it)
                removeArgument(argument, element)
                index++
            } else if (expression == Expression.UNIVERSE &&
                    ((isExplicit && argument is ArendImplicitArgument) || (!isExplicit && argument !is ArendImplicitArgument))) {
                missedIndexed.add(it)
            } else {
                index++
            }
        }

        missedIndexed.add(errorIndex)
        if (index < arguments.size && UNIVERSE_LITERALS.contains(arguments[index].text)) {
            removeArgument(arguments[index], element)
        }

        (errorIndex + 1 until functionSignature.size).forEach {
            if (index >= arguments.size) {
                return@forEach
            }
            val (type, expression, _) = functionSignature[it]
            addDefinedType(expression, arguments[index], type, definedTypes, universes)
        }
    }

    private fun removeArgument(argument: PsiElement, element: PsiElement) {
        if (argument.nextSibling != null) {
            element.deleteChildRange(argument, argument.nextElement)
        } else {
            element.deleteChildRange(argument, argument)
        }
    }

    private fun addDefinedType(expression: Expression, argument: PsiElement, type: String, definedTypes: MutableSet<String>, universes: List<String>) {
        if (expression == Expression.ARGUMENT && !UNIVERSE_LITERALS.contains(argument.text) && universes.contains(type)) {
            definedTypes.add(type)
        }
    }

    private enum class Expression {
        UNIVERSE,
        ARGUMENT
    }

    companion object {
        private val UNIVERSE_LITERALS = listOf("{?}", "{{?}}", "_", "{_}")
    }
}
