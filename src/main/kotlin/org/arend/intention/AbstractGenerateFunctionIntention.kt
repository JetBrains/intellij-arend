package org.arend.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import com.intellij.util.SmartList
import org.arend.core.context.binding.Binding
import org.arend.core.context.binding.TypedBinding
import org.arend.core.context.param.TypedSingleDependentLink
import org.arend.core.expr.ClassCallExpression
import org.arend.core.expr.Expression
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.prettyprinting.DefinitionRenamer
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.ext.variable.Variable
import org.arend.extImpl.ConcreteFactoryImpl
import org.arend.extImpl.definitionRenamer.CachingDefinitionRenamer
import org.arend.extImpl.definitionRenamer.ScopeDefinitionRenamer
import org.arend.naming.reference.DataLocalReferable
import org.arend.naming.reference.LocalReferable
import org.arend.naming.renamer.MapReferableRenamer
import org.arend.naming.renamer.ReferableRenamer
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ConvertingScope
import org.arend.naming.scope.Scope
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.*
import org.arend.refactoring.addToWhere
import org.arend.refactoring.rename.ArendGlobalReferableRenameHandler
import org.arend.refactoring.replaceExprSmart
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.ArendResolveCache
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.MinimizedRepresentation
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.instance.provider.InstanceProvider
import org.arend.util.*
import org.arend.util.ParameterExplicitnessState.*
import java.util.*
import java.util.function.Supplier

abstract class AbstractGenerateFunctionIntention : BaseIntentionAction() {
    companion object {
        val log = logger<AbstractGenerateFunctionIntention>()
    }

    override fun getFamilyName() = ArendBundle.message("arend.generate.function")

    internal abstract fun extractSelectionData(file: PsiFile, editor: Editor, project: Project): SelectionResult?

    private fun getName(context: PsiElement): String {
        return context.parentOfType<ArendGroup>()?.defIdentifier?.name?.let { "$it-lemma" } ?: "lemma"
    }

    internal data class SelectionResult(
        val expectedType: Expression?,
        val contextPsi: ArendCompositeElement,
        val rangeOfReplacement: TextRange,
        val selectedConcrete: Concrete.Expression?,
        val identifier: String?,
        val bodyRepresentation: String?,
        val bodyCore: Expression?,
        val additionalArguments: List<TypedSingleDependentLink> = emptyList()
    )

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        editor ?: return
        file ?: return
        val selectionResult = extractSelectionData(file, editor, project) ?: return
        val expressions = listOfNotNull(
            selectionResult.expectedType,
            selectionResult.bodyCore,
            *selectionResult.additionalArguments.map { it.typeExpr }.toTypedArray()
        )
        val freeVariables = FreeVariablesWithDependenciesCollector.collectFreeVariables(expressions)
            .filter { freeArg -> freeArg.first.name !in selectionResult.additionalArguments.map { it.name } }
        performRefactoring(freeVariables, selectionResult, editor, project)
    }

    internal open fun performRefactoring(
            freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
            selection: SelectionResult,
            editor: Editor, project: Project
    ) {
        val baseIdentifier = selection.identifier ?: getName(selection.contextPsi)
        val newFunctionName = generateFreeName(baseIdentifier, selection.contextPsi.scope)
        val newCallConcrete = buildNewCallConcrete(freeVariables, newFunctionName)
        val definitionRepresentation = buildNewFunctionRepresentation(selection, freeVariables, newFunctionName)
        val cacheService = project.service<ArendResolveCache>()

        selection.contextPsi.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                if (element is ArendReferenceElement && selection.rangeOfReplacement.contains(element.textRange)) {
                    cacheService.dropCache(element)
                }
            }
        })

        val globalOffsetOfNewDefinition = modifyDocument(
            editor,
            newCallConcrete,
            selection.rangeOfReplacement,
            selection.selectedConcrete,
            selection.contextPsi,
            definitionRepresentation,
            project
        )
        // drop cache before...

        invokeRenamer(editor, globalOffsetOfNewDefinition, project)
    }

    internal open fun buildNewFunctionRepresentation(
        selection: SelectionResult,
        freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
        name: String
    ): String {
        val allParameters = getAllParameters(freeVariables, selection)

        val prettyPrinter = getMinimizationPrettyPrinter(selection, allParameters)

        val parametersRepresentation =
            allParameters
            .collapseTelescopes()
            .joinToString(" ") { (bindings, explicitness) ->
                val bindingsRepresentation = bindings.joinToString(" ") { it.name }
                val typeRepresentation = prettyPrinter(bindings.first().typeExpr)
                " " + "$bindingsRepresentation : $typeRepresentation".let(explicitness::surround)
            }

        val bodyRepresentation = selection.bodyRepresentation?.replace("\\this", "this") ?: "{?}"

        val newFunctionDefinitionType =
            if (selection.expectedType != null) " : ${prettyPrinter(selection.expectedType)}" else ""
        return buildString {
            append(name)
            append(parametersRepresentation)
            append(newFunctionDefinitionType)
            append(" => ")
            append(bodyRepresentation)
        }
    }

    internal fun buildNewCallConcrete(
        freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
        newFunctionName: String
    ) = with(ConcreteFactoryImpl(null)) {
        app(ref(TypedBinding(newFunctionName, null)),
            freeVariables.filter { it.second == EXPLICIT }.map { arg(getArgumentConcrete(it), true) })
    }

    private fun getMinimizationPrettyPrinter(
        selection: SelectionResult,
        allParameters: List<Pair<Binding, ParameterExplicitnessState>>
    ): (Expression) -> Concrete.Expression {
        val enclosingDefinitionReferable = selection.contextPsi.parentOfType<PsiLocatedReferable>()!!

        val ip = getInstanceProvider(enclosingDefinitionReferable)

        val definitionRenamer = getDefinitionRenamer(selection)
        val referableRenamer = getReferableRenamer(allParameters.mapToSet(Pair<Binding, ParameterExplicitnessState>::first))

        val config = object : PrettyPrinterConfig {
            override fun getExpressionFlags(): EnumSet<PrettyPrinterFlag> =
                EnumSet.of(PrettyPrinterFlag.SHOW_PROOFS, PrettyPrinterFlag.SHOW_BIN_OP_IMPLICIT_ARGS, *super.getExpressionFlags().toTypedArray())

            override fun getNormalizationMode(): NormalizationMode? = null

            override fun getDefinitionRenamer() = definitionRenamer
        }

        return { expr ->
            val concrete = try {
                MinimizedRepresentation.generateMinimizedRepresentation(expr, ip, definitionRenamer, referableRenamer)
            } catch (e: Exception) {
                if (ApplicationManager.getApplication().isInternal) {
                    log.error(e)
                }
                ToAbstractVisitor.convert(expr, config)
            }
            contractFields(concrete)
        }
    }

    private fun getArgumentConcrete(binding: Pair<Binding, ParameterExplicitnessState>): Concrete.Expression {
        if (binding.first is ClassCallExpression.ClassCallBinding) {
            val def = binding.first.typeExpr as ClassCallExpression
            return Concrete.TypedExpression(null,
                Concrete.ThisExpression(null, null),
                Concrete.ReferenceExpression(null, LocalReferable(def.definition.name))
            )
        }
        return ConcreteFactoryImpl(null).ref(TypedBinding(binding.first.name, null)) as Concrete.ReferenceExpression
    }

    private fun getReferableRenamer(bindings: Set<Binding>): Supplier<ReferableRenamer>? {
        val classBindings = bindings.filterIsInstance<ClassCallExpression.ClassCallBinding>()
        if (classBindings.isEmpty()) {
            return null
        }
        val thisMapping: Map<Variable, Concrete.Expression> = classBindings.associateWithWellTyped {
            Concrete.ReferenceExpression(null, DataLocalReferable(it.typeExpr, "this"))
        }
        return Supplier { MapReferableRenamer(thisMapping) }
    }

    private fun getInstanceProvider(enclosingDefinitionReferable: PsiLocatedReferable): InstanceProvider? =
        ArendReferableConverter.toDataLocatedReferable(enclosingDefinitionReferable)
            ?.let { PsiInstanceProviderSet().get(it) }

    private fun getAllParameters(
        freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
        selection: SelectionResult
    ): List<Pair<Binding, ParameterExplicitnessState>> {
        val mappedAdditionalArguments = selection.additionalArguments.map {
            TypedBinding(it.name, it.typeExpr) to it.isExplicit.toExplicitnessState()
        }
        return freeVariables + mappedAdditionalArguments
    }

    private fun getDefinitionRenamer(selection: SelectionResult): DefinitionRenamer =
        CachingDefinitionRenamer(ScopeDefinitionRenamer(selection.contextPsi.scope.let { CachingScope.make(ConvertingScope(ArendReferableConverter, it)) }))

    private fun Boolean.toExplicitnessState(): ParameterExplicitnessState = if (this) EXPLICIT else IMPLICIT

    internal tailrec fun generateFreeName(baseName: String, scope: Scope): String =
            if (scope.resolveName(baseName) == null) {
                baseName
            } else {
                generateFreeName("$baseName'", scope)
            }

    private fun List<Pair<Binding, ParameterExplicitnessState>>.collapseTelescopes(): List<Pair<List<Binding>, ParameterExplicitnessState>> =
        fold(mutableListOf<Pair<MutableList<Binding>, ParameterExplicitnessState>>()) { collector, (binding, explicitness) ->
            if (collector.isEmpty() || collector.last().second == IMPLICIT) {
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
            project: Project
    ): Int {
        val document = editor.document
        val startOffset = replaceablePsi.startOffset
        val newCallRepresentation = newCall.toString()
        val newFunction =
            ArendPsiFactory(replaceablePsi.project).createFromText("\\func $newFunctionDefinition")!!.statements[0].group as ArendDefFunction
        val oldFunction = replaceablePsi.parentOfType<ArendGroup>()!!
        val newDefinition = oldFunction.addToWhere(newFunction)
        val newDefPointer = SmartPointerManager.createPointer(newDefinition)
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
        replaceExprSmart(document, replaceablePsi, replacedConcrete, rangeOfReplacement, null, newCall, newCallRepresentation, false)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        oldFunction.where?.let { CodeStyleManager.getInstance(project).reformat(it) }

        editor.caretModel.moveToOffset(startOffset + 1)
        return newDefPointer.element!!.startOffset
    }

    protected fun invokeRenamer(editor: Editor, functionOffset: Int, project: Project) {
        val newFunctionDefinition = PsiDocumentManager
            .getInstance(project)
            .getPsiFile(editor.document)
            ?.findElementAt(functionOffset)
            ?.parentOfType<PsiNameIdentifierOwner>() ?: return
        ArendGlobalReferableRenameHandler().doRename(newFunctionDefinition, editor, null)
    }

    private fun contractFields(expandedConcrete: Concrete.Expression): Concrete.Expression = when (expandedConcrete) {
        is Concrete.ClassExtExpression -> expandedConcrete.baseClassExpression
        else -> expandedConcrete
    }
}
