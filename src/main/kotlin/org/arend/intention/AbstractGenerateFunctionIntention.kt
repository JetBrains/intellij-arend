package org.arend.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.SmartList
import org.arend.core.context.binding.Binding
import org.arend.core.context.binding.TypedBinding
import org.arend.core.context.param.TypedSingleDependentLink
import org.arend.core.expr.Expression
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.extImpl.ConcreteFactoryImpl
import org.arend.extImpl.definitionRenamer.CachingDefinitionRenamer
import org.arend.extImpl.definitionRenamer.ScopeDefinitionRenamer
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ConvertingScope
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.TCDefinition
import org.arend.psi.ext.impl.ArendGroup
import org.arend.refactoring.addToWhere
import org.arend.refactoring.rename.ArendGlobalReferableRenameHandler
import org.arend.refactoring.replaceExprSmart
import org.arend.resolving.ArendReferableConverter
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.MinimizedRepresentation
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.util.ArendBundle
import org.arend.util.FreeVariablesWithDependenciesCollector
import org.arend.util.ParameterExplicitnessState

abstract class AbstractGenerateFunctionIntention : BaseIntentionAction() {
    companion object {
        val log = logger<AbstractGenerateFunctionIntention>()
    }

    override fun getFamilyName() = ArendBundle.message("arend.generate.function")

    internal abstract fun extractSelectionData(file: PsiFile, editor: Editor, project: Project): SelectionResult?

    internal data class SelectionResult(
            val expectedType: Expression?,
            val contextPsi: ArendCompositeElement,
            val rangeOfReplacement: TextRange,
            val selectedConcrete : Concrete.Expression?,
            val identifier: String?,
            val body: Expression?,
            val additionalArguments: List<TypedSingleDependentLink> = emptyList()
    )

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        editor ?: return
        file ?: return
        val selectionResult = extractSelectionData(file, editor, project) ?: return
        val expressions = listOfNotNull(selectionResult.expectedType, selectionResult.body, *selectionResult.additionalArguments.map { it.typeExpr }.toTypedArray())
        val freeVariables = FreeVariablesWithDependenciesCollector.collectFreeVariables(expressions)
                .filter { freeArg -> freeArg.first.name !in selectionResult.additionalArguments.map { it.name } }
        performRefactoring(freeVariables, selectionResult, editor, project)
    }

    internal open fun performRefactoring(
            freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
            selection: SelectionResult,
            editor: Editor, project: Project
    ) {
        val (enclosingDefinition, name) = getEnclosingDefinitionWithName(selection.contextPsi) ?: return
        val enclosingDefinitionReferable = selection.contextPsi.parentOfType<TCDefinition>()!!
        val (newFunctionCall, newFunctionDefinition) = buildRepresentations(
                enclosingDefinitionReferable, selection,
                freeVariables
        ) { "$name-lemma" } ?: return

        val globalOffsetOfNewDefinition =
                modifyDocument(editor,
                        newFunctionCall,
                        selection.rangeOfReplacement,
                        selection.selectedConcrete,
                        selection.contextPsi,
                        newFunctionDefinition,
                        enclosingDefinition,
                        project)

        invokeRenamer(editor, globalOffsetOfNewDefinition, project)
    }

    private fun getEnclosingDefinitionWithName(context: PsiElement): Pair<ArendGroup, String>? {
        val parentGroup = context.parentOfType<ArendGroup>()
        if (parentGroup != null) {
            val name = parentGroup.defIdentifier?.name ?: return null
            return parentGroup to name
        }
        return null
    }


    internal open fun buildRepresentations(
            enclosingDefinitionReferable: TCDefinition,
            selection: SelectionResult,
            freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
            nameProducer : () -> String
    ): Pair<Concrete.Expression, String>? {
        val baseName = selection.identifier ?: nameProducer()
        val newFunctionName = generateFreeName(baseName, selection.contextPsi.scope)

        val prettyPrinter: (Expression, Boolean) -> Concrete.Expression = run {
            val ip = PsiInstanceProviderSet().get(ArendReferableConverter.toDataLocatedReferable(enclosingDefinitionReferable)!!)
            val renamer = CachingDefinitionRenamer(ScopeDefinitionRenamer(selection.contextPsi.parentOfType<ArendStatement>()!!.scope.let { CachingScope.make(ConvertingScope(ArendReferableConverter, it)) }));

            { expr, useReturnType ->
                try {
                    MinimizedRepresentation.generateMinimizedRepresentation(expr, ip, renamer, useReturnType)
                } catch (e: Exception) {
                    log.error(e)
                    ToAbstractVisitor.convert(expr, object : PrettyPrinterConfig {
                        override fun getDefinitionRenamer() = renamer
                    })
                }
            }
        }

        val mappedAdditionalArguments = selection.additionalArguments.map { TypedBinding(it.name, it.typeExpr) to it.isExplicit.toExplicitnessState() }
        val parameters = (freeVariables + mappedAdditionalArguments)
                .collapseTelescopes()
                .joinToString("") { (bindings, explicitness) ->
                    " ${explicitness.openingBrace}${bindings.joinToString(" ") { it.name }} : ${prettyPrinter(bindings.first().typeExpr, false)}${explicitness.closingBrace}"
                }

        val actualBody = selection.body?.let { prettyPrinter(it, true) } ?: "{?}"
        val newFunctionCall = with(ConcreteFactoryImpl(null)) {
            app(ref(TypedBinding(newFunctionName, null)), freeVariables.filter { it.second == ParameterExplicitnessState.EXPLICIT }.map { arg(ref(TypedBinding(it.first.name, null)), true) })
        } as Concrete.Expression
        val newFunctionDefinitionType = if (selection.expectedType != null) " : ${prettyPrinter(selection.expectedType, false)}" else ""
        val newFunctionDefinitionTail = "$newFunctionName$parameters$newFunctionDefinitionType => $actualBody"
        return newFunctionCall to newFunctionDefinitionTail
    }

    private fun Boolean.toExplicitnessState(): ParameterExplicitnessState = if (this) ParameterExplicitnessState.EXPLICIT else ParameterExplicitnessState.IMPLICIT

    private tailrec fun generateFreeName(baseName: String, scope: Scope): String =
            if (scope.resolveName(baseName) == null) {
                baseName
            } else {
                generateFreeName("$baseName'", scope)
            }

    private fun List<Pair<Binding, ParameterExplicitnessState>>.collapseTelescopes(): List<Pair<List<Binding>, ParameterExplicitnessState>> =
            fold(mutableListOf<Pair<MutableList<Binding>, ParameterExplicitnessState>>()) { collector, (binding, explicitness) ->
            if (collector.isEmpty() || collector.last().second == ParameterExplicitnessState.IMPLICIT) {
                collector.add(SmartList(binding) to explicitness)
            } else {
                val lastEntry = collector.last()
                if (lastEntry.first.first().type == binding.type) {
                    lastEntry.first.add(binding)
                } else {
                    collector.add(SmartList(binding) to explicitness)
                }
            }
            collector
        }

    private fun modifyDocument(
            editor: Editor,
            newCall: Concrete.Expression,
            rangeOfReplacement: TextRange,
            replacedConcrete: Concrete.Expression?,
            replaceablePsi: ArendCompositeElement,
            newFunctionDefinition: String,
            oldFunction: ArendGroup,
            project: Project
    ): Int {
        val document = editor.document
        val startOffset = replaceablePsi.startOffset
        val newCallRepresentation = newCall.toString()
        val newFunction = ArendPsiFactory(replaceablePsi.project).createFromText("\\func $newFunctionDefinition")!!.statements[0].definition as ArendDefFunction
        val newDefinition = oldFunction.addToWhere(newFunction)
        val newDefPointer = SmartPointerManager.createPointer(newDefinition)
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
        val parenthesizedNewCall = replaceExprSmart(document, replaceablePsi, replacedConcrete, rangeOfReplacement, null, newCall, newCallRepresentation, false)
        val parenShift = if (parenthesizedNewCall.startsWith('(')) 1 else 0
        PsiDocumentManager.getInstance(project).commitDocument(document)
        oldFunction.where?.let { CodeStyleManager.getInstance(project).reformat(it) }

        editor.caretModel.moveToOffset(startOffset + parenShift)
        return newDefPointer.element!!.startOffset
    }

    protected fun invokeRenamer(editor: Editor, functionOffset: Int, project: Project) {
        val newFunctionDefinition =
                PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.findElementAt(functionOffset)
                        ?.parentOfType<PsiNameIdentifierOwner>() ?: return
        ArendGlobalReferableRenameHandler().doRename(newFunctionDefinition, editor, null)
    }
}
