package org.vclang.resolving

import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.error.ReferenceError
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferableImpl
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.DefinitionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.CachingScope
import com.jetbrains.jetpad.vclang.naming.scope.ConvertingScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.term.concrete.ConcreteDefinitionVisitor
import com.jetbrains.jetpad.vclang.typechecking.error.ProxyError
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import org.vclang.psi.ancestors
import org.vclang.psi.ext.PsiConcreteReferable
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.typechecking.execution.TypecheckingEventsProcessor


private object NullDefinition : Concrete.Definition(LocatedReferableImpl(Precedence.DEFAULT, "_", null, true)) {
    override fun <P : Any?, R : Any?> accept(visitor: ConcreteDefinitionVisitor<in P, out R>?, params: P): R? = null
}

class PsiConcreteProvider(private val referableConverter: ReferableConverter, private val errorReporter: ErrorReporter, private val eventsProcessor: TypecheckingEventsProcessor?) : ConcreteProvider {
    private val cache: MutableMap<PsiLocatedReferable, Concrete.ReferableDefinition> = HashMap()

    private fun getConcreteDefinition(psiReferable: PsiConcreteReferable): Concrete.ReferableDefinition? {
        var cached = true
        var scope: Scope? = null
        val result = cache.computeIfAbsent(psiReferable, {
            cached = false
            if (eventsProcessor != null) {
                eventsProcessor.onTestStarted(psiReferable)
                eventsProcessor.startTimer(psiReferable)
            }

            val def = psiReferable.computeConcrete(referableConverter, errorReporter)
            if (def == null) {
                if (eventsProcessor != null) {
                    eventsProcessor.stopTimer(psiReferable)
                    eventsProcessor.onTestFailure(psiReferable)
                    eventsProcessor.onTestFinished(psiReferable)
                }
                return@computeIfAbsent NullDefinition
            } else {
                if (def.resolved == Concrete.Resolved.NOT_RESOLVED) {
                    scope = CachingScope.make(ConvertingScope(referableConverter, psiReferable.scope))
                    def.relatedDefinition.accept(DefinitionResolveNameVisitor(this, true, errorReporter), scope)
                }
                eventsProcessor?.stopTimer(psiReferable)
                return@computeIfAbsent def
            }
        })

        if (result === NullDefinition) {
            return null
        }
        if (cached) {
            return result
        }

        if (result.relatedDefinition.resolved != Concrete.Resolved.RESOLVED) {
            if (scope == null) {
                scope = CachingScope.make(ConvertingScope(referableConverter, psiReferable.scope))
            }
            result.relatedDefinition.accept(DefinitionResolveNameVisitor(this, errorReporter), scope)
        }

        when (result) {
            is Concrete.DataDefinition -> for (clause in result.constructorClauses) {
                for (constructor in clause.constructors) {
                    PsiLocatedReferable.fromReferable(constructor.data)?.let { cache[it] = constructor }
                }
            }
            is Concrete.ClassDefinition -> for (field in result.fields) {
                PsiLocatedReferable.fromReferable(field.data)?.let { cache[it] = field }
            }
        }

        return result
    }

    override fun getConcrete(referable: GlobalReferable): Concrete.ReferableDefinition? {
        val psiReferable = PsiLocatedReferable.fromReferable(referable)
        if (psiReferable == null) {
            errorReporter.report(ProxyError(referable, ReferenceError("Unknown type of reference", referable)))
            return null
        }

        if (psiReferable is PsiConcreteReferable) {
            return getConcreteDefinition(psiReferable)
        }

        cache[psiReferable]?.let { return it }
        psiReferable.ancestors.filterIsInstance<PsiConcreteReferable>().firstOrNull()?.let { getConcreteDefinition(it) } ?: return null
        return cache[psiReferable]
    }
}