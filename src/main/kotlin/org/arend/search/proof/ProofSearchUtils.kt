package org.arend.search.proof

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.SmartList
import com.intellij.util.castSafelyTo
import org.arend.error.DummyErrorReporter
import org.arend.psi.AREND_KEYWORDS
import org.arend.psi.ArendDefClass
import org.arend.psi.ArendDefData
import org.arend.psi.ArendExpr
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.*
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.resolving.DataLocatedReferable
import org.arend.resolving.PsiConcreteProvider
import org.arend.search.collectSearchScopes
import org.arend.settings.ArendProjectSettings
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete

data class HighlightedCodomain(val typeRep: String, val keywords: List<TextRange>, val match: List<TextRange>)
data class ProofSearchEntry(val def: ReferableAdapter<*>, val codomain: HighlightedCodomain)

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
        val list = SmartList<Pair<ReferableAdapter<*>, HighlightedCodomain>>()
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
                val (parameters, codomain, info) = getSignature(concreteProvider, def, query.shouldConsiderParameters())
                    ?: return@processElements true
                val matchResults = matcher.match(parameters, codomain, def.scope)
                if (matchResults != null) {
                    val startOffset =
                        codomain.data?.castSafelyTo<PsiElement>()?.startOffset ?: return@processElements true
                    val textRange = matchResults.mapNotNull { concrete ->
                        concrete.data?.castSafelyTo<PsiElement>()?.textRange?.takeIf { it.startOffset >= startOffset }?.shiftLeft(startOffset)
                    }
                    list.add(def to info.value.copy(match = textRange))
                }
                true
            }
        }

        for (def in list) {
            yield(ProofSearchEntry(def.first, def.second))
        }
    }
}

private data class SignatureWithHighlighting(
    val parameters: List<Concrete.Expression>,
    val resultType: Concrete.Expression,
    val info: Lazy<HighlightedCodomain>
)

private fun getSignature(
    provider: PsiConcreteProvider,
    referable: PsiReferable,
    shouldConsiderParameters: Boolean
): SignatureWithHighlighting? {
    if (referable is ClassFieldAdapter) {
        val concrete = referable
            .parentOfType<ArendDefClass>()
            ?.let(provider::getConcrete)
            ?.castSafelyTo<Concrete.ClassDefinition>()
            ?.elements
            ?.find { it is Concrete.ClassField && it.data.castSafelyTo<DataLocatedReferable>()?.underlyingReferable == referable }
            ?.castSafelyTo<Concrete.ClassField>()
            ?: return null
        return SignatureWithHighlighting(
            concrete.parameters.mapNotNull { it.type },
            concrete.resultType,
            lazy(LazyThreadSafetyMode.NONE) {
                referable.resultType?.let(::getHighlightedCodomain) ?: basicHighlightedCodomain(concrete.resultType)
            })
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
        val codomain = Concrete.ReferenceExpression(
            concrete.relatedDefinition.data,
            relatedDefinition
        )
        return SignatureWithHighlighting(
            concrete.parameters.mapNotNull { it.type },
            codomain,
            lazy(LazyThreadSafetyMode.NONE) { basicHighlightedCodomain(codomain) })
    }
    if (referable !is PsiConcreteReferable) return null
    if (referable !is CoClauseDefAdapter && referable !is FunctionDefinitionAdapter) return null
    return when (val concrete = provider.getConcrete(referable)) {
        is Concrete.FunctionDefinition -> {
            val resultType = concrete.resultType ?: return null
            val (parameters, codomain) = if (shouldConsiderParameters) {
                val parameters = concrete.parameters.mapNotNull { it.type }
                deconstructPi(
                    Concrete.PiExpression(
                        null,
                        parameters.map { Concrete.TypeParameter(true, it) },
                        resultType
                    )
                )
            } else {
                emptyList<Concrete.Expression>() to resultType
            }
            val psiType = (referable as Abstract.FunctionDefinition).resultType as ArendExpr
            return SignatureWithHighlighting(
                parameters,
                codomain,
                lazy(LazyThreadSafetyMode.NONE) { getHighlightedCodomain(psiType) })
        }
        else -> null
    }
}

private fun basicHighlightedCodomain(concrete: Concrete.Expression): HighlightedCodomain =
    HighlightedCodomain(concrete.toString(), emptyList(), emptyList())

private fun getHighlightedCodomain(psiType: ArendExpr): HighlightedCodomain {
    val keywords = mutableListOf<TextRange>()
    psiType.accept(object : PsiRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (AREND_KEYWORDS.contains(element.elementType)) {
                keywords.add(element.textRange)
            }
            super.visitElement(element)
        }
    })
    val baseTextOffset = psiType.textOffset
    return HighlightedCodomain(
        psiType.text,
        keywords.map { TextRange(it.startOffset - baseTextOffset, it.endOffset - baseTextOffset) },
        emptyList()
    )
}

private fun deconstructPi(expr: Concrete.Expression): Pair<List<Concrete.Expression>, Concrete.Expression> {
    return if (expr is Concrete.PiExpression) {
        val (piDomain, piCodomain) = deconstructPi(expr.codomain)
        (expr.parameters.mapNotNull { it.type } + piDomain) to piCodomain
    } else {
        emptyList<Concrete.Expression>() to expr
    }
}

sealed interface ProofSearchUIEntry

data class MoreElement(val alreadyProcessed: Int, val sequence: Sequence<ProofSearchEntry>) : ProofSearchUIEntry

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
