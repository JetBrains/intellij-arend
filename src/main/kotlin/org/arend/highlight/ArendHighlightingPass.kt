package org.arend.highlight

// TODO[server2]: Remove obsolete stuff

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.IArendFile
import org.arend.naming.reference.*
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.quickfix.implementCoClause.IntentionBackEndVisitor
import org.arend.psi.ArendExpressionCodeFragment
import org.arend.resolving.*
import org.arend.server.ArendServerService
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.ConcreteCompareVisitor
import org.arend.term.group.Group
import org.arend.toolWindow.errors.ArendMessagesService
import org.arend.typechecking.*
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.order.Ordering
import org.arend.typechecking.order.listener.CollectingOrderingListener
import org.arend.util.ComputationInterruptedException

class ArendHighlightingPass(file: IArendFile, editor: Editor, textRange: TextRange)
    : BasePass(file, editor, "Arend resolver annotator", textRange) {

    /*
    init {
        myProject.service<TypeCheckingService>().initialize()
    }
    */

    public override fun collectInformationWithProgress(progress: ProgressIndicator) {
        setProgressLimit(numberOfDefinitions(file as? Group).toLong())
        collectInfo(progress)
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
        val resolveListener = object : ArendResolverListener(myProject.service<ArendResolveCache>()) {
            override fun resolveReference(data: Any?, referent: Referable?, list: List<ArendReferenceElement>, resolvedRefs: List<Referable?>) {
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
                        val lastParent = last.parent
                        val nextToLast = last.extendLeft.prevSibling
                        if (lastParent is ArendAtomFieldsAcc && nextToLast != null) {
                            TextRange(lastParent.textRange.startOffset, nextToLast.textRange.endOffset)
                        } else null
                    } else when (val lastParent = last.parent) {
                        is ArendFieldAcc -> (lastParent.parent as? ArendAtomFieldsAcc)?.let { fieldsAcc ->
                            lastParent.extendLeft.prevSibling?.let { nextToLast ->
                                TextRange(fieldsAcc.textRange.startOffset, nextToLast.textRange.endOffset)
                            }
                        }
                        is ArendLongName -> last.extendLeft.prevSibling?.let { nextToLast ->
                            TextRange(lastParent.textRange.startOffset, nextToLast.textRange.endOffset)
                        }
                        else -> null
                    }

                    if (textRange != null) {
                        addHighlightInfo(textRange, ArendHighlightingColors.LONG_NAME)
                    }
                }

                if (data is ArendPattern && (referent as? GlobalReferable?)?.kind == GlobalReferable.Kind.CONSTRUCTOR) {
                    addHighlightInfo(data.textRange, ArendHighlightingColors.CONSTRUCTOR_PATTERN)
                }
            }

            override fun patternParsed(pattern: Concrete.ConstructorPattern) {
                val data = pattern.constructorData
                if (data is PsiElement) {
                    addHighlightInfo(data.textRange, ArendHighlightingColors.CONSTRUCTOR_PATTERN)
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

                (definition.data.data as? PsiLocatedReferable)?.let { ref ->
                    ref.nameIdentifier?.let {
                        addHighlightInfo(it.textRange, ArendHighlightingColors.DECLARATION)
                    }
                    (ref as? ReferableBase<*>)?.alias?.aliasIdentifier?.let {
                        addHighlightInfo(it.textRange, ArendHighlightingColors.DECLARATION)
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

                advanceProgress(1)
            }
        }

        if (file is ArendFile) {
            val module = file.moduleLocation
            if (module != null) {
                myProject.service<ArendServerService>().server.resolveModules(listOf(module), this, ProgressCancellationIndicator(progress), resolveListener)
            }
        }
    }

    override fun applyInformationWithProgress() {
        super.applyInformationWithProgress()
        file.project.service<ArendMessagesService>().update((file as? ArendFile)?.moduleLocation)
    }

    /*
        override fun applyInformationWithProgress() {
            println("applyInformationWithProgress: ${Thread.currentThread()}")
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
                service<TypecheckingTaskQueue>().addTask(lastDefinitionModification) {
                    typeChecker.runTypechecker(file, lastModifiedDefinition, collector1, collector2, true)
                }
            }
        }
        */
}