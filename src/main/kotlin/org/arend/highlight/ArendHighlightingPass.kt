package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.IArendFile
import org.arend.naming.reference.*
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.quickfix.implementCoClause.IntentionBackEndVisitor
import org.arend.refactoring.changeSignature.ArendChangeSignatureDialogCodeFragment
import org.arend.refactoring.changeSignature.ArendChangeSignatureDialogParameter
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.ArendResolverListener
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group
import org.arend.typechecking.BackgroundTypechecker
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.TypecheckingTaskQueue
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.order.Ordering
import org.arend.typechecking.order.listener.CollectingOrderingListener

class ArendHighlightingPass(file: IArendFile, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BasePass(file, editor, "Arend resolver annotator", textRange, highlightInfoProcessor) {

    private val psiListenerService = myProject.service<ArendPsiChangeService>()
    private val concreteProvider = PsiConcreteProvider(myProject, this, null, false)
    private val instanceProviderSet = PsiInstanceProviderSet()
    private val collector1 = CollectingOrderingListener()
    private val collector2 = CollectingOrderingListener()
    private var lastModifiedDefinition: TCDefReferable? = null
    private val lastDefinitionModification = psiListenerService.definitionModificationTracker.modificationCount
    var lastModification: Long = 0

    init {
        myProject.service<TypeCheckingService>().initialize()
    }

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        if (myProject.service<TypeCheckingService>().isLoaded) {
            setProgressLimit(numberOfDefinitions(file as? Group).toLong())
            collectInfo(progress)
        }
    }

    private fun numberOfDefinitions(group: Group?): Int {
        if (group == null) return 0
        val def = group.referable
        var res = if (def is TCDefinition) 1 else 0

        for (statement in group.statements) {
            res += numberOfDefinitions(statement.group)
        }
        for (subgroup in group.dynamicSubgroups) {
            res += numberOfDefinitions(subgroup)
        }
        return res
    }

    private fun collectInfo(progress: ProgressIndicator) {
        val definitions = ArrayList<Concrete.Definition>()
        val resolveListener = object : ArendResolverListener(myProject.service()) {
            override fun resolveReference(data: Any?, referent: Referable?, list: List<ArendReferenceElement>, resolvedRefs: List<Referable?>) {
                val containingFile = (data as? PsiElement)?.containingFile
                if (containingFile is ArendChangeSignatureDialogCodeFragment)
                    for ((referable, refElement) in resolvedRefs.zip(list))
                        if (referable is ArendChangeSignatureDialogParameter)
                            containingFile.addDependency(referable, refElement)

                val lastReference = list.lastOrNull() ?: return
                if (data !is ArendPattern && (lastReference is ArendRefIdentifier || lastReference is ArendDefIdentifier)) {
                    when {
                        referent is GlobalReferable && referent.precedence.isInfix ->
                            addHighlightInfo(lastReference.textRange, ArendHighlightingColors.OPERATORS)
                        (((referent as? RedirectingReferable)?.originalReferable ?: referent) as? MetaReferable)?.resolver != null ->
                            addHighlightInfo(lastReference.textRange, ArendHighlightingColors.META_RESOLVER)
                    }
                }

                var index = 0
                while (index < resolvedRefs.size - 1 && resolvedRefs[index] !is ErrorReference) {
                    index++
                }

                if (index > 0) {
                    val last = list[index]
                    val textRange = if (last is ArendIPName) {
                        last.parentLiteral?.let { literal ->
                            literal.longName?.let { longName ->
                                TextRange(longName.textRange.startOffset, (literal.dot ?: longName).textRange.endOffset)
                            }
                        }
                    } else {
                        (last.parent as? ArendLongName)?.let { longName ->
                            last.extendLeft.prevSibling?.let { nextToLast ->
                                TextRange(longName.textRange.startOffset, nextToLast.textRange.endOffset)
                            }
                        }
                    }

                    if (textRange != null) {
                        addHighlightInfo(textRange, ArendHighlightingColors.LONG_NAME)
                    }
                }
            }

            override fun patternResolved(originalRef: Referable?, pattern: Concrete.ConstructorPattern, resolvedRefs: List<Referable?>) {
                super.patternResolved(originalRef, pattern, resolvedRefs)
                val dataPattern = pattern.data as? ArendPattern ?: return
                val constructors =
                    dataPattern.sequence.takeIf { it.isNotEmpty() }?.mapNotNull { it.referenceElement?.referenceNameElement?.takeIf { el -> el.text == pattern.constructor.refName } }
                        ?: dataPattern.takeIf { it.singleReferable != null }?.let { listOfNotNull(it.referenceElement?.referenceNameElement) }
                        ?: return
                for (psi in constructors) {
                    addHighlightInfo(psi.textRange, ArendHighlightingColors.CONSTRUCTOR_PATTERN)
                }
            }

            private fun highlightParameters(definition: Concrete.GeneralDefinition) {
                for (parameter in Concrete.getParameters(definition, true) ?: emptyList()) {
                    if (((parameter.type?.underlyingReferable as? GlobalReferable)?.underlyingReferable as? ArendDefClass)?.isRecord == false) {
                        val list: List<ArendCompositeElement>? = when (val param = parameter.data) {
                            is ArendFieldTele -> param.referableList
                            is ArendNameTele -> param.identifierOrUnknownList
                            is ArendTypeTele -> param.typedExpr?.identifierOrUnknownList
                            else -> null
                        }
                        if (list != null) for (id in list) {
                            addHighlightInfo(id.textRange, ArendHighlightingColors.CLASS_PARAMETER)
                        }
                    }
                }
            }

            override fun definitionResolved(definition: Concrete.ResolvableDefinition) {
                if (file !is ArendFile) return
                progress.checkCanceled()

                if (resetDefinition) {
                    (definition.data.underlyingReferable as? TCDefinition)?.let {
                        psiListenerService.updateDefinition(it, file, true)
                    }
                }

                (definition.data.underlyingReferable as? PsiLocatedReferable)?.let { ref ->
                    if (ref.containingFile == myFile) {
                        ref.nameIdentifier?.let {
                            addHighlightInfo(it.textRange, ArendHighlightingColors.DECLARATION)
                        }
                        (ref as? ReferableBase<*>)?.alias?.aliasIdentifier?.let {
                            addHighlightInfo(it.textRange, ArendHighlightingColors.DECLARATION)
                        }
                    }
                }

                highlightParameters(definition)
                if (definition is Concrete.DataDefinition) {
                    for (constructorClause in definition.constructorClauses) {
                        for (constructor in constructorClause.constructors) {
                            highlightParameters(constructor)
                        }
                    }
                }

                definition.accept(IntentionBackEndVisitor(), null)
                if (definition is Concrete.Definition) {
                    definitions.add(definition)
                }

                advanceProgress(1)
            }
        }
        when (file) {
            is ArendFile -> DefinitionResolveNameVisitor(concreteProvider, ArendReferableConverter, this, resolveListener).resolveGroup(file, file.scope)
            is ArendChangeSignatureDialogCodeFragment -> {
                file.resetDependencies()
                val firstChild = file.firstChild as ArendExpr
                if (firstChild.elementType != ArendElementTypes.EXPR) {
                    val concrete = ConcreteBuilder.convertExpression(firstChild)
                    ExpressionResolveNameVisitor(ArendReferableConverter, file.scope, ArrayList(), this, resolveListener).resolve(concrete)
                }
            }
        }

        concreteProvider.resolve = true

        val dependencyListener = myProject.service<TypeCheckingService>().dependencyListener
        val ordering = Ordering(instanceProviderSet, concreteProvider, collector1, dependencyListener, ArendReferableConverter, PsiElementComparator)
        val lastModified = (file as? ArendFile)?.lastModifiedDefinition?.let { concreteProvider.getConcrete(it) as? Concrete.Definition }
        if (lastModified != null) {
            lastModifiedDefinition = lastModified.data
            ordering.order(lastModified)
        }
        ordering.listener = collector2
        for (definition in definitions) {
            if (definition.data != lastModified?.data) {
                ordering.order(definition)
            }
        }
    }

    override fun applyInformationWithProgress() {
        file.lastModification.updateAndGet { maxOf(it, lastModification) }
        if (file is ArendFile) myProject.service<ErrorService>().clearNameResolverErrors(file)
        super.applyInformationWithProgress()
        if (file !is ArendFile) return

        if (collector1.isEmpty && collector2.isEmpty) {
            return
        }

        val typeChecker = BackgroundTypechecker(myProject, instanceProviderSet, concreteProvider,
            maxOf(lastDefinitionModification, psiListenerService.definitionModificationTracker.modificationCount))
        if (ApplicationManager.getApplication().isUnitTestMode) {
            // DaemonCodeAnalyzer.restart does not work in tests
            typeChecker.runTypechecker(file, lastModifiedDefinition, collector1, collector2, false)
        } else {
            myProject.service<TypecheckingTaskQueue>().addTask(lastDefinitionModification) {
                typeChecker.runTypechecker(file, lastModifiedDefinition, collector1, collector2, true)
            }
        }
    }
}