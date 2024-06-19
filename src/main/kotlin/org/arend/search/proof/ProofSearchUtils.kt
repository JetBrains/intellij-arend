package org.arend.search.proof

import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.psi.util.parentsOfType
import com.intellij.util.SmartList
import org.arend.documentation.ArendKeyword.Companion.AREND_KEYWORDS
import org.arend.error.DummyErrorReporter
import org.arend.psi.ext.*
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.refactoring.rangeOfConcrete
import org.arend.resolving.DataLocatedReferable
import org.arend.resolving.PsiConcreteProvider
import org.arend.search.collectSearchScopes
import org.arend.settings.ArendProjectSettings
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete
import org.arend.util.caching

data class ProofSearchEntry(val def: ReferableBase<*>, val signature: RenderingInfo)

/**
 * @return null as an element of the sequence, if the search couldn't find any matching result for a long time.
 * It can be used for an interruption check, so nulls can be safely skipped while retrieving the results if you don't care
 * about performance.
 */
fun generateProofSearchResults(
    project: Project,
    pattern: String,
): Sequence<ProofSearchEntry?> = sequence {
    val settings = ProofSearchUISettings(project)
    val query = (ProofSearchQuery.fromString(pattern) as? ParsingResult.OK<ProofSearchQuery>)?.value
        ?: return@sequence
    val matcher = ArendExpressionMatcher(query)

    val listedIdentifiers = query.getAllIdentifiers()

    val keys = DumbService.getInstance(project).runReadActionInSmartMode(Computable {
        StubIndex.getInstance().getAllKeys(ArendDefinitionIndex.KEY, project)
    })

    val searchScope = if (listedIdentifiers.isNotEmpty()) {
        val scopes = collectSearchScopes(listedIdentifiers, GlobalSearchScope.allScope(project), project)
        runReadAction {
            scopes.map { GlobalSearchScope.fileScope(project, it) }.reduce(GlobalSearchScope::union)
        }
    } else {
        GlobalSearchScope.allScope(project)
    }

    val concreteProvider = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null)

    var idleCounter = 0

    for (definitionName in keys) {
        val list = SmartList<ProofSearchEntry>()
        runReadAction {
            StubIndex.getInstance().processElements(
                ArendDefinitionIndex.KEY,
                definitionName,
                project,
                searchScope,
                PsiReferable::class.java
            ) { def ->
                if (!settings.checkAllowed(def)) return@processElements true
                if (def !is ReferableBase<*>) return@processElements true
                val (parameters, codomain, info) = getSignature(concreteProvider, def, query.shouldConsiderParameters())
                    ?: return@processElements true
                val (parameterResults, codomainResults) = matcher.match(parameters, codomain, def.scope) ?: return@processElements true
                val parameterRangesRegistry = mutableMapOf<Int, List<TextRange>>()
                val rangeComputer = caching { e : Concrete.Expression -> if (e is Concrete.ReferenceExpression && e.referent is ArendDefData) (e.referent as ArendDefData).nameIdentifier!!.textRange else rangeOfConcrete(e) }
                for ((parameterConcrete, ranges) in parameterResults) {
                    val index = parameters.indexOf(parameterConcrete)
                    val existing = parameterRangesRegistry.getOrDefault(index, emptyList())
                    parameterRangesRegistry[index] = existing + ranges.map { rangeComputer(it).shiftLeft(rangeComputer(parameterConcrete).startOffset) }
                }
                val codomainRange = codomainResults.map { rangeComputer(it).shiftLeft(rangeComputer(codomain).startOffset) }
                list.add(ProofSearchEntry(def,
                    info.value.copy(
                        parameters = info.value.parameters.mapIndexedNotNull { index, data -> data.takeIf { index in parameterRangesRegistry }?.copy(match = parameterRangesRegistry[index]!!) },
                        codomain = info.value.codomain.copy(match = codomainRange))))
                true
            }
        }
        if (list.isNotEmpty()) {
            for (def in list) {
                yield(def)
            }
        } else {
            idleCounter += 1
            if (idleCounter >= 50) {
                idleCounter = 0
                yield(null)
            }
        }
    }
}

private fun Any?.getPsi() : PsiElement? {
    if (this is PsiElement) return this
    if (this is DataLocatedReferable) return this.data?.element
    return null
}

private data class SignatureWithHighlighting(
    val parameters: List<Concrete.Expression>,
    val resultType: Concrete.Expression,
    val info: Lazy<RenderingInfo>
)

data class RenderingInfo(val parameters: List<ProofSearchHighlightingData>, val codomain: ProofSearchHighlightingData)
data class ProofSearchHighlightingData(val typeRep: String, val keywords: List<TextRange>, val match: List<TextRange>)

private fun getSignature(
    provider: PsiConcreteProvider,
    referable: PsiReferable,
    shouldConsiderParameters: Boolean
): SignatureWithHighlighting? {
    if (referable is ArendClassField) {
        val concrete = (referable
            .parentOfType<ArendDefClass>()
            ?.let(provider::getConcrete)
            as? Concrete.ClassDefinition)
            ?.elements
            ?.find { it is Concrete.ClassField && (it.data as? DataLocatedReferable)?.underlyingReferable == referable }
            as? Concrete.ClassField
            ?: return null
        val parameters = concrete.parameters.mapNotNull { it.type }
        return SignatureWithHighlighting(
            concrete.parameters.mapNotNull { it.type },
            concrete.resultType,
            lazy(LazyThreadSafetyMode.NONE) {
                RenderingInfo(parameters.map(::gatherHighlightingData), gatherHighlightingData(concrete.resultType))
            })
    }
    if (referable is ArendConstructor) {
        val relatedDefinition = referable
            .parentOfType<ArendDefData>()
        val concrete = (relatedDefinition
            ?.let(provider::getConcrete)
            as? Concrete.DataDefinition)
            ?.constructorClauses?.flatMap { it.constructors }
            ?.find { (it.data as? DataLocatedReferable)?.underlyingReferable == referable }
            ?: return null
        val codomain = Concrete.ReferenceExpression(
            concrete.relatedDefinition.data,
            relatedDefinition
        )
        val parameters = concrete.parameters.mapNotNull { it.type }
        return SignatureWithHighlighting(
            parameters,
            codomain,
            lazy(LazyThreadSafetyMode.NONE) {
                RenderingInfo(parameters.map(::gatherHighlightingData), gatherHighlightingData(codomain))
            })
    }
    if (referable !is PsiConcreteReferable) return null
    if (referable !is ArendCoClauseDef && referable !is ArendDefFunction) return null
    return when (val concrete = provider.getConcrete(referable)) {
        is Concrete.FunctionDefinition -> {
            val resultType = concrete.resultType ?: return null
            val (parameters, codomain) = if (shouldConsiderParameters) {
                val parameters = concrete.parameters.mapNotNull { it.type }
                deconstructPi(
                    Concrete.PiExpression(
                        null,
                        parameters.map { Concrete.TypeParameter(true, it, false) },
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
                lazy(LazyThreadSafetyMode.NONE) {
                    RenderingInfo(parameters.map(::gatherHighlightingData), getHighlightingData(psiType))
                })
        }
        else -> null
    }
}

private fun gatherHighlightingData(expr: Concrete.Expression) : ProofSearchHighlightingData {
    return (expr.getPsi() as? ArendExpr)?.let(::getHighlightingData) ?: basicHighlightingData(expr)
}

private fun basicHighlightingData(concrete: Concrete.Expression): ProofSearchHighlightingData =
    ProofSearchHighlightingData(concrete.toString(), emptyList(), emptyList())

private fun getHighlightingData(psiType: ArendExpr): ProofSearchHighlightingData {
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
    return ProofSearchHighlightingData(
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

data class MoreElement(val alreadyProcessed: Int, val sequence: Sequence<ProofSearchEntry?>) : ProofSearchUIEntry

@JvmInline
value class DefElement(val entry: ProofSearchEntry): ProofSearchUIEntry

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

fun getCompleteModuleLocation(def: ReferableBase<*>): String? {
    var file: String? = null
    ApplicationManager.getApplication().run {
        executeOnPooledThread {
            runReadAction {
                file = def.location?.toString()
            }
        }.get()
    }
    if (file == null) {
        return null
    }

    val module = def.parentsOfType<ArendGroup>(false).toList().reversed().drop(1).map { it.name }
    return (listOf(file) + module).joinToString(".")
}