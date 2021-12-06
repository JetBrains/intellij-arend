package org.arend.search.proof

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.SmartList
import com.intellij.util.castSafelyTo
import org.arend.core.expr.Expression
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.resolving.DataLocatedReferable
import org.arend.settings.ArendProjectSettings

data class ProofSearchEntry(val def: ReferableAdapter<*>, val finalCodomain: Expression, val tree: PatternTree)

fun generateProofSearchResults(
    project: Project,
    pattern: String,
): Sequence<ProofSearchEntry> = sequence {
    val settings = ProofSearchUISettings(project)
    val matcher = runReadAction {
        val parsedExpression = ArendPsiFactory(project).createExpressionMaybe(pattern) ?: return@runReadAction null
        ArendExpressionMatcher(deconstructArendExpr(parsedExpression))
    } ?: return@sequence

    val keys = runReadAction { StubIndex.getInstance().getAllKeys(ArendDefinitionIndex.KEY, project) }

    for (definitionName in keys) {
        val list = SmartList<Pair<ReferableAdapter<*>, Expression>>()
        runReadAction {
            StubIndex.getInstance().processElements(
                ArendDefinitionIndex.KEY,
                definitionName,
                project,
                GlobalSearchScope.allScope(project),
                PsiReferable::class.java
            ) { def ->
                if (!settings.checkAllowed(def)) return@processElements true
                val adapter = def.castSafelyTo<ReferableAdapter<*>>() ?: return@processElements true
                val coreDefinition =
                    adapter.tcReferable?.takeIf { it.isTypechecked }?.castSafelyTo<DataLocatedReferable>()?.typechecked
                if (coreDefinition != null) {
                    val matchResult = matcher.match(coreDefinition, def.scope)
                    if (matchResult != null) {
                        list.add(adapter to matchResult)
                    }
                }
                true
            }
        }

        for (def in list) {
            yield(ProofSearchEntry(def.first, def.second, matcher.tree))
        }
    }
}

sealed interface ProofSearchUIEntry

@JvmInline
value class MoreElement(val sequence: Sequence<ProofSearchEntry>) : ProofSearchUIEntry

@JvmInline
value class DefElement(val entry: ProofSearchEntry) : ProofSearchUIEntry

class ProofSearchUISettings(private val project: Project) {

    private val includeTestLocations: Boolean = project.service<ArendProjectSettings>().data.includeTestLocations

    private val includeNonProjectLocations: Boolean = project.service<ArendProjectSettings>().data.includeNonProjectLocations

    fun checkAllowed(element: PsiElement): Boolean {
        if (includeNonProjectLocations && includeTestLocations) {
            return true
        }
        val file = PsiUtilCore.getVirtualFile(element) ?: return true
        return (includeTestLocations || !TestSourcesFilter.isTestSources(file, project))
                && (includeNonProjectLocations || ProjectScope.getProjectScope(project).contains(file))
    }
}
