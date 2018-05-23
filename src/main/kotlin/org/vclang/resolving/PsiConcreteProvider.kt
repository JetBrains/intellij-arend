package org.vclang.resolving

import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.error.ReferenceError
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.DefinitionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.CachingScope
import com.jetbrains.jetpad.vclang.naming.scope.ConvertingScope
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.typechecking.error.ProxyError
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.CachingConcreteProvider
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import org.vclang.psi.ancestors
import org.vclang.psi.ext.PsiConcreteReferable
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.typechecking.execution.TypecheckingEventsProcessor


class PsiConcreteProvider(private val referableConverter: ReferableConverter, private val errorReporter: ErrorReporter, private val eventsProcessor: TypecheckingEventsProcessor?) : ConcreteProvider {
    var isResolving = false
    private val cache: MutableMap<PsiLocatedReferable, Concrete.ReferableDefinition> = HashMap()

    private fun getConcreteDefinition(psiReferable: PsiConcreteReferable): Concrete.ReferableDefinition? {
        val result = cache.computeIfAbsent(psiReferable, {
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
                return@computeIfAbsent CachingConcreteProvider.NULL_DEFINITION
            } else {
                if (isResolving) {
                    def.relatedDefinition.accept(DefinitionResolveNameVisitor(this, errorReporter), CachingScope.make(ConvertingScope(referableConverter, psiReferable.scope)))
                }
                eventsProcessor?.stopTimer(psiReferable)
                return@computeIfAbsent def
            }
        })

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

        return if (result == CachingConcreteProvider.NULL_DEFINITION) null else result
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