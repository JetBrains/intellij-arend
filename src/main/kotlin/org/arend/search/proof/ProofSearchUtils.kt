package org.arend.search.proof

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parentOfType
import com.intellij.util.SmartList
import com.intellij.util.castSafelyTo
import org.arend.error.DummyErrorReporter
import org.arend.psi.ArendDefClass
import org.arend.psi.ArendDefData
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.*
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.resolving.DataLocatedReferable
import org.arend.resolving.PsiConcreteProvider
import org.arend.search.collectSearchScopes
import org.arend.settings.ArendProjectSettings
import org.arend.term.concrete.Concrete

data class ProofSearchEntry(val def: ReferableAdapter<*>, val finalCodomain: Concrete.Expression)

fun generateProofSearchResults(
    project: Project,
    pattern: String,
): Sequence<ProofSearchEntry> = sequence {
    val settings = ProofSearchUISettings(project)
    val query = ProofSearchQuery.fromString(pattern).castSafelyTo<ParsingResult.OK<ProofSearchQuery>>()?.value
        ?: return@sequence
    val matcher = ArendExpressionMatcher(query)

    val listedIdentifiers = query.getAllIdentifiers()

    val keys = DumbService.getInstance(project).runReadActionInSmartMode(Computable {
        StubIndex.getInstance().getAllKeys(ArendDefinitionIndex.KEY, project)
    })

    val searchScope = if (listedIdentifiers.isNotEmpty()) {
        val scopes = collectSearchScopes(listedIdentifiers, GlobalSearchScope.allScope(project), project)
        scopes.map { GlobalSearchScope.fileScope(project, it) }.reduce(GlobalSearchScope::union)
    } else {
        GlobalSearchScope.allScope(project)
    }

    val concreteProvider = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null)

    for (definitionName in keys) {
        val list = SmartList<Pair<ReferableAdapter<*>, Concrete.Expression>>()
        runReadAction {
            StubIndex.getInstance().processElements(
                ArendDefinitionIndex.KEY,
                definitionName,
                project,
                searchScope,
                PsiReferable::class.java
            ) { def ->
                if (!settings.checkAllowed(def)) return@processElements true
                if (def !is ReferableAdapter<*>) return@processElements true
                val (parameters, codomain) = getSignature(concreteProvider, def, query.shouldConsiderParameters()) ?: return@processElements true
                if (matcher.match(parameters, codomain, def.scope)) {
                    list.add(def to codomain)
                }
                true
            }
        }

        for (def in list) {
            yield(ProofSearchEntry(def.first, def.second))
        }
    }
}

private fun getSignature(
    provider: PsiConcreteProvider,
    referable: PsiReferable,
    shouldConsiderParameters: Boolean
): Pair<List<Concrete.Expression>, Concrete.Expression>? {
    if (referable is ClassFieldAdapter) {
        val concrete = referable
            .parentOfType<ArendDefClass>()
            ?.let(provider::getConcrete)
            ?.castSafelyTo<Concrete.ClassDefinition>()
            ?.elements
            ?.find { it is Concrete.ClassField && it.data.castSafelyTo<DataLocatedReferable>()?.underlyingReferable == referable }
            ?.castSafelyTo<Concrete.ClassField>()
            ?: return null
        return concrete.parameters.mapNotNull { it.type } to concrete.resultType
    }
    if (referable is ConstructorAdapter) {
        val relatedDefinition = referable
            .parentOfType<ArendDefData>()
        val concrete = relatedDefinition
            ?.let(provider::getConcrete)
            ?.castSafelyTo<Concrete.DataDefinition>()
            ?.constructorClauses?.flatMap { it.constructors }
            ?.find { it.data.castSafelyTo<DataLocatedReferable>()?.underlyingReferable == referable }
            ?: return null
        return concrete.parameters.mapNotNull { it.type } to Concrete.ReferenceExpression(
            concrete.relatedDefinition.data,
            relatedDefinition
        )
    }
    if (referable !is PsiConcreteReferable) return null
    if (referable !is CoClauseDefAdapter && referable !is FunctionDefinitionAdapter) return null
    return when (val concrete = provider.getConcrete(referable)) {
        is Concrete.FunctionDefinition -> {
            val resultType = concrete.resultType ?: return null
            return if (shouldConsiderParameters) {
                val parameters = concrete.parameters.mapNotNull { it.type }
                deconstructPi(Concrete.PiExpression(null, parameters.map { Concrete.TypeParameter(true, it) }, resultType))
            } else {
                emptyList<Concrete.Expression>() to resultType
            }
        }
        else -> null
    }
}

private fun deconstructPi(expr : Concrete.Expression) : Pair<List<Concrete.Expression>, Concrete.Expression> {
    return if (expr is Concrete.PiExpression) {
        val (piDomain, piCodomain) = deconstructPi(expr.codomain)
        (expr.parameters.mapNotNull { it.type } + piDomain) to piCodomain
    } else {
        emptyList<Concrete.Expression>() to expr
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

    private val truncateResults: Boolean = project.service<ArendProjectSettings>().data.truncateSearchResults

    fun checkAllowed(element: PsiElement): Boolean {
        if (includeNonProjectLocations && includeTestLocations) {
            return true
        }
        val file = PsiUtilCore.getVirtualFile(element) ?: return true
        return (includeTestLocations || !TestSourcesFilter.isTestSources(file, project))
                && (includeNonProjectLocations || ProjectScope.getProjectScope(project).contains(file))
    }

    fun shouldLimitSearch() : Boolean = truncateResults
}
