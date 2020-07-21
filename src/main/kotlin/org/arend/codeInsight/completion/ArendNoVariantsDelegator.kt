package org.arend.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.search.ProjectAndLibrariesScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Consumer
import org.arend.psi.ArendLongName
import org.arend.psi.ArendRefIdentifier
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.resolving.ArendReferenceImpl

class ArendNoVariantsDelegator: CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val tracker = object: Consumer<CompletionResult> {
            var noLookup : Boolean = true
            override fun consume(plainResult: CompletionResult) {
                result.passResult(plainResult)
                val element: LookupElement? = plainResult.lookupElement
                noLookup = element == null
            }
        }
        result.runRemainingContributors(parameters, tracker)
        val refElementAtCaret = parameters.originalFile.findElementAt(parameters.offset-1)?.parent
        val parent = refElementAtCaret?.parent
        val refElementIsLeftmost = refElementAtCaret is ArendRefIdentifier && parent is ArendLongName && refElementAtCaret.prevSibling == null

        val editor = parameters.editor
        val project = editor.project
        if (tracker.noLookup && project != null && refElementIsLeftmost) {
            val scope = ProjectAndLibrariesScope(project)
            StubIndex.getInstance().processAllKeys(ArendDefinitionIndex.KEY, project) { name ->
                if (result.prefixMatcher.prefixMatches(name)) {
                    val locatedReferables = StubIndex.getElements<String, PsiReferable>(ArendDefinitionIndex.KEY, name, project, scope, PsiReferable::class.java).filterIsInstance<PsiLocatedReferable>()
                    locatedReferables.forEach {
                        ArendReferenceImpl.createArendLookUpElement(it, parameters.originalFile, null, true)?.let{ result.addElement(it.withInsertHandler { context, item ->
                            val refIdentifier = context.file.findElementAt(context.tailOffset-1)?.parent
                            val locatedReferable = item.`object`
                            if (refIdentifier is ArendReferenceElement && locatedReferable is PsiLocatedReferable) ResolveReferenceAction.getProposedFix(locatedReferable, refIdentifier)?.execute(editor)
                        }) }
                    }
                }
                true // If only a limited number (say N) of variants is needed, return false after N added lookUpElements
            }
        }

        super.fillCompletionVariants(parameters, result)
    }
}