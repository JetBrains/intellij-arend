package org.arend.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.impl.BetterPrefixMatcher
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.Consumer
import org.arend.module.ModuleLocation
import org.arend.naming.scope.ScopeFactory.isGlobalScopeVisible
import org.arend.psi.ArendFile
import org.arend.psi.ArendFileScope
import org.arend.psi.ancestors
import org.arend.psi.ext.*
import org.arend.psi.libraryConfig
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.psi.stubs.index.ArendGotoClassIndex
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.resolving.ArendReferenceImpl
import org.arend.typechecking.TypeCheckingService

class ArendNoVariantsDelegator : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val tracker = object : Consumer<CompletionResult> {
            val variants = HashSet<PsiElement>()
            val nullPsiVariants = HashSet<String>()
            override fun consume(plainResult: CompletionResult) {
                result.passResult(plainResult)
                val elementPsi: PsiElement? = plainResult.lookupElement.psiElement
                val str = plainResult.lookupElement.lookupString
                if (elementPsi != null) {
                    val elementIsWithinFileBeingEdited = elementPsi.containingFile == parameters.position.containingFile
                    if (!elementIsWithinFileBeingEdited) variants.add(elementPsi) else {
                        val originalPosition = parameters.originalPosition
                        if (originalPosition != null) parameters.originalFile.findElementAt(elementPsi.startOffset - parameters.position.startOffset + originalPosition.startOffset)?.ancestors?.
                        firstOrNull {it.elementType == elementPsi.elementType }?.let { variants.add(it) }
                    }
                } else {
                    nullPsiVariants.add(str)
                }
            }
        }
        result.runRemainingContributors(parameters, tracker)
        val file = parameters.originalFile
        val isTestFile = (file as? ArendFile)?.moduleLocation?.locationKind == ModuleLocation.LocationKind.TEST
        val refElementAtCaret = file.findElementAt(parameters.offset - 1)?.parent
        val parent = refElementAtCaret?.parent
        val allowedPosition = refElementAtCaret is ArendRefIdentifier && parent is ArendLongName && refElementAtCaret.prevSibling == null && isGlobalScopeVisible(refElementAtCaret.topmostEquivalentSourceNode)
        val classExtension = parent?.parent is ArendDefClass

        val editor = parameters.editor
        val project = editor.project

        if (project != null && allowedPosition && (tracker.variants.isEmpty() && tracker.nullPsiVariants.isEmpty() || !parameters.isAutoPopup)) {
            val consumer = { name: String, refs: List<PsiLocatedReferable>? ->
                if (BetterPrefixMatcher(result.prefixMatcher, Int.MIN_VALUE).prefixMatches(name)) {
                    val locatedReferables = refs ?: StubIndex.getElements(if (classExtension) ArendGotoClassIndex.KEY else ArendDefinitionIndex.KEY, name, project, ArendFileScope(project), PsiReferable::class.java).filterIsInstance<PsiLocatedReferable>()
                    locatedReferables.forEach {
                        val isInsideTest = (it.containingFile as? ArendFile)?.moduleLocation?.locationKind == ModuleLocation.LocationKind.TEST
                        if (!tracker.variants.contains(it) && (isTestFile || !isInsideTest)) ArendReferenceImpl.createArendLookUpElement(it, parameters.originalFile, true, null, it !is ArendDefClass || !it.isRecord)?.let {
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

            for (entry in project.service<TypeCheckingService>().additionalReferables.entries) {
                consumer.invoke(entry.key, entry.value)
            }

            StubIndex.getInstance().processAllKeys(ArendDefinitionIndex.KEY, project) { name ->
                consumer.invoke(name, null)
                true // If only a limited number (say N) of variants is needed, return false after N added lookUpElements
            }
        } else {
            result.restartCompletionOnAnyPrefixChange()
        }

        super.fillCompletionVariants(parameters, result)
    }
}