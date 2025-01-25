package org.arend.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.reference.Precedence
import org.arend.ext.util.Pair
import org.arend.naming.error.ReferenceError
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferableImpl
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.resolving.ResolverListener
import org.arend.naming.resolving.typing.TypingInfo
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.psi.ancestor
import org.arend.psi.ext.*
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.ConcreteDefinitionVisitor
import org.arend.term.group.AccessModifier
import org.arend.typechecking.execution.TypecheckingEventsProcessor
import org.arend.typechecking.provider.ConcreteProvider


private object NullDefinition : Concrete.Definition(LocatedReferableImpl(AccessModifier.PUBLIC, Precedence.DEFAULT, "_", null, GlobalReferable.Kind.OTHER)) {
    override fun getParameters() = emptyList<Concrete.Parameter>()

    override fun addParameters(parameters: List<Concrete.Parameter>?, parametersOriginalDefinitions: List<Pair<TCDefReferable, Int>>?) {
        throw IllegalStateException()
    }

    override fun <P : Any?, R : Any?> accept(visitor: ConcreteDefinitionVisitor<in P, out R>?, params: P): R? = null
}

// TODO[server2]: Delete this
class PsiConcreteProvider(private val project: Project, private val errorReporter: ErrorReporter, private val eventsProcessor: TypecheckingEventsProcessor?, var resolve: Boolean = true, private val resolverListener: ResolverListener? = null, private val referableConverter: ReferableConverter = ArendReferableConverter) : ConcreteProvider {
    private val cache: MutableMap<PsiLocatedReferable, Concrete.GeneralDefinition> = HashMap()

    private fun getConcreteDefinition(psiReferable: PsiConcreteReferable): Concrete.GeneralDefinition? {
        var cached = true
        var scope: Scope? = null
        val result = cache[psiReferable] ?: runReadAction {
            cached = false
            if (eventsProcessor != null) {
                eventsProcessor.onTestStarted(psiReferable)
                eventsProcessor.startTimer(psiReferable)
            }

            val def = psiReferable.computeConcrete(referableConverter, errorReporter)
            if (resolve && def.relatedDefinition.stage == Concrete.Stage.NOT_RESOLVED) {
                scope = CachingScope.make(psiReferable.scope)
                def.relatedDefinition.accept(DefinitionResolveNameVisitor(this, true, TypingInfo.EMPTY, errorReporter, resolverListener), scope)
            }
            eventsProcessor?.stopTimer(psiReferable)
            return@runReadAction cache.putIfAbsent(psiReferable, def) ?: def
        }

        if (result === NullDefinition) {
            return null
        }
        if (cached) {
            return result
        }

        if (resolve && result.relatedDefinition.stage < Concrete.Stage.RESOLVED) {
            runReadAction {
                if (scope == null) {
                    scope = CachingScope.make(psiReferable.scope)
                }
                DefinitionResolveNameVisitor(this, TypingInfo.EMPTY, errorReporter, resolverListener).resolveDefinition(result.relatedDefinition, scope, psiReferable.ancestor<ArendGroup>())
            }
        }

        when (result) {
            is Concrete.DataDefinition -> for (clause in result.constructorClauses) {
                for (constructor in clause.constructors) {
                    PsiLocatedReferable.fromReferable(constructor.data)?.let { cache[it] = constructor }
                }
            }
            is Concrete.ClassDefinition -> for (element in result.elements) {
                if (element is Concrete.ClassField) {
                    PsiLocatedReferable.fromReferable(element.data)?.let { cache[it] = element }
                }
            }
        }

        return result
    }

    private fun convertReferable(referable: GlobalReferable): PsiLocatedReferable? {
        var psiReferable = PsiLocatedReferable.fromReferable(referable)
        if (psiReferable == null) {
            if (referable is DataLocatedReferable) {
                psiReferable = referable.fixPointer(project)
                if (psiReferable == null) {
                    errorReporter.report(ReferenceError(GeneralError.Stage.OTHER, "Reference is invalid. Try to typecheck the definition again", referable))
                    return null
                }
            } else {
                errorReporter.report(ReferenceError(GeneralError.Stage.OTHER, "Unknown type of reference", referable))
                return null
            }
        }
        return psiReferable
    }

    override fun getConcrete(referable: GlobalReferable): Concrete.GeneralDefinition? {
        val def = referable.defaultConcrete
        if (def != null) return def
        val psiReferable = convertReferable(referable) ?: return null

        if (psiReferable is PsiConcreteReferable) {
            return getConcreteDefinition(psiReferable)
        }

        cache[psiReferable]?.let { return it }
        val concreteRef = runReadAction { psiReferable.ancestor<PsiConcreteReferable>() } ?: return null
        getConcreteDefinition(concreteRef) ?: return null
        return cache[psiReferable]
    }

    fun getConcreteFunction(referable: GlobalReferable): Concrete.FunctionDefinition? {
        val psiReferable = referable.underlyingReferable
        return if (psiReferable is PsiConcreteReferable && (psiReferable is ArendDefFunction || psiReferable is ArendDefInstance)) getConcreteDefinition(psiReferable) as? Concrete.FunctionDefinition else null
    }
}