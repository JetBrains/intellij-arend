package org.arend.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.components.service
import com.intellij.psi.search.ProjectAndLibrariesScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Consumer
import org.arend.naming.scope.ScopeFactory.isGlobalScopeVisible
import org.arend.psi.*
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.psi.stubs.index.ArendGotoClassIndex
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.resolving.ArendReferenceImpl
import org.arend.typechecking.TypeCheckingService

class ArendNoVariantsDelegator : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val tracker = object : Consumer<CompletionResult> {
            var hasVariant: Boolean = false
            override fun consume(plainResult: CompletionResult) {
                result.passResult(plainResult)
                val element: LookupElement? = plainResult.lookupElement
                hasVariant = hasVariant || element != null
            }
        }
        result.runRemainingContributors(parameters, tracker)
        val file = parameters.originalFile
        val refElementAtCaret = file.findElementAt(parameters.offset - 1)?.parent
        val parent = refElementAtCaret?.parent
        val allowedPosition = refElementAtCaret is ArendRefIdentifier && parent is ArendLongName && refElementAtCaret.prevSibling == null && isGlobalScopeVisible(refElementAtCaret)
        val classExtension = parent?.parent is ArendDefClass

        val editor = parameters.editor
        val project = editor.project

        if (!tracker.hasVariant && project != null && allowedPosition) {
            val scope = ProjectAndLibrariesScope(project)
            val tcService = project.service<TypeCheckingService>()

            val consumer = { name: String, refs: List<PsiLocatedReferable>? ->
                if (result.prefixMatcher.prefixMatches(name)) {
                    val locatedReferables = refs ?: StubIndex.getElements(if (classExtension) ArendGotoClassIndex.KEY else ArendDefinitionIndex.KEY, name, project, scope, PsiReferable::class.java).filterIsInstance<PsiLocatedReferable>()
                    locatedReferables.forEach {
                        if (it !is ArendFile) ArendReferenceImpl.createArendLookUpElement(it, parameters.originalFile, true, null, it !is ArendDefClass || !it.isRecord)?.let {
                            result.addElement(
                                    run {
                                        val oldHandler = it.insertHandler
                                        it.withInsertHandler { context, item ->
                                            oldHandler?.handleInsert(context, item)
                                            val refIdentifier = context.file.findElementAt(context.tailOffset - 1)?.parent
                                            val locatedReferable = item.`object`
                                            if (refIdentifier is ArendReferenceElement && locatedReferable is PsiLocatedReferable) ResolveReferenceAction.getProposedFix(locatedReferable, refIdentifier)?.execute(editor)
                                        }
                                    })
                        }
                    }
                }
            }

            (file as? ArendFile)?.libraryConfig?.forAvailableConfigs {
                for (entry in it.additionalNames.entries) {
                    consumer.invoke(entry.key, entry.value)
                }
                null
            }

            for (entry in tcService.additionalReferables.entries) {
                consumer.invoke(entry.key, entry.value)
            }

            StubIndex.getInstance().processAllKeys(ArendDefinitionIndex.KEY, project) { name ->
                consumer.invoke(name, null)
                true // If only a limited number (say N) of variants is needed, return false after N added lookUpElements
            }
        } else {
            result.restartCompletionWhenNothingMatches()
        }

        super.fillCompletionVariants(parameters, result)
    }
}