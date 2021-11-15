package org.arend.search.proof

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Processor
import com.intellij.util.castSafelyTo
import org.arend.psi.ArendExpr
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.search.structural.ArendExpressionMatcher
import org.arend.search.structural.deconstructArendExpr
import org.arend.term.abs.Abstract

fun fetchWeightedElements(project : Project,
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<ProofSearchEntry>>
) {
    runReadAction {
        val parsedExpression = ArendPsiFactory(project).createExpressionMaybe(pattern) ?: return@runReadAction
        val matcher = ArendExpressionMatcher(deconstructArendExpr(parsedExpression))
        val keys = StubIndex.getInstance().getAllKeys(ArendDefinitionIndex.KEY, project)
        for (definitionName in keys) {
            if (progressIndicator.isCanceled) {
                break
            }
            StubIndex.getInstance().processElements(
                ArendDefinitionIndex.KEY,
                definitionName,
                project,
                GlobalSearchScope.allScope(project),
                PsiReferable::class.java
            ) { def ->
                if (progressIndicator.isCanceled) {
                    return@processElements false
                }
                val type = def.castSafelyTo<Abstract.FunctionDefinition>()?.resultType?.castSafelyTo<ArendExpr>()
                    ?: return@processElements true
                if (matcher.match(type)) {
                    // todo: weight
                    consumer.process(FoundItemDescriptor(ProofSearchEntry(def, matcher.tree), 1))
                }
                true
            }
        }
    }
}