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
import com.intellij.util.SmartList
import com.intellij.util.castSafelyTo
import org.arend.core.definition.ClassField
import org.arend.core.definition.Constructor
import org.arend.core.definition.Definition
import org.arend.core.definition.FunctionDefinition
import org.arend.core.expr.Expression
import org.arend.core.subst.LevelPair
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.CoClauseDefAdapter
import org.arend.psi.ext.impl.ConstructorAdapter
import org.arend.psi.ext.impl.FunctionDefinitionAdapter
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.resolving.DataLocatedReferable
import org.arend.search.collectSearchScopes
import org.arend.settings.ArendProjectSettings
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.util.appExprToConcrete

data class ProofSearchEntry(val def: ReferableAdapter<*>, val finalCodomain: Concrete.Expression)

fun generateProofSearchResults(
    project: Project,
    pattern: String,
): Sequence<ProofSearchEntry> = sequence {
    val settings = ProofSearchUISettings(project)
    val patternTree = PatternTree.fromRawPattern(pattern) ?: return@sequence
    val matcher = ArendExpressionMatcher(patternTree)

    val listedIdentifiers = patternTree.getAllIdentifiers()

    val keys = DumbService.getInstance(project).runReadActionInSmartMode(Computable {
        StubIndex.getInstance().getAllKeys(ArendDefinitionIndex.KEY, project)
    })

    val searchScope = if (listedIdentifiers.isNotEmpty()) {
        val scopes = collectSearchScopes(listedIdentifiers, GlobalSearchScope.allScope(project), project)
        scopes.map { GlobalSearchScope.fileScope(project, it) }.reduce(GlobalSearchScope::union)
    } else {
        GlobalSearchScope.allScope(project)
    }

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
                val type = getConcreteType(def) ?: return@processElements true
                if (matcher.match(type, def.scope)) {
                    list.add(def to type)
                }
                true
            }
        }

        for (def in list) {
            yield(ProofSearchEntry(def.first, def.second))
        }
    }
}

fun getConcreteType(referable: PsiReferable): Concrete.Expression? {
    val adapter = referable.castSafelyTo<ReferableAdapter<*>>() ?: return null
    val coreDefinition =
        adapter.tcReferable?.takeIf { it.isTypechecked }?.castSafelyTo<DataLocatedReferable>()?.typechecked
    val coreType = coreDefinition?.let { getType(it) }
    return if (coreType != null) {
        ToAbstractVisitor.convert(coreType, PrettyPrinterConfig.DEFAULT)
    } else {
        when (referable) {
            is CoClauseDefAdapter -> referable.resultType?.let(::appExprToConcrete)
            is FunctionDefinitionAdapter -> referable.resultType?.let(::appExprToConcrete)
            is ConstructorAdapter -> referable.typeOf?.let(::appExprToConcrete)
            else -> null
        }
    }
}

private fun getType(def: Definition): Expression? = when (def) {
    is FunctionDefinition -> def.resultType
    is Constructor -> def.getDataTypeExpression(LevelPair.STD)
    is ClassField -> def.resultType
    else -> null
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
