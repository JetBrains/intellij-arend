package org.arend.psi.ext

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.error.DummyErrorReporter
import org.arend.naming.reference.*
import org.arend.naming.resolving.ResolverListener
import org.arend.server.ArendServerService
import org.arend.term.abs.AbstractReference
import org.arend.typechecking.computation.UnstoppableCancellationIndicator

interface ArendReferenceElement : ArendReferenceContainer, AbstractReference {
    val rangeInElement: TextRange

    private object ArendReferenceKey : Key<Referable>("AREND_REFERENCE_KEY")

    val cachedReferable: Referable?
        get() = getUserData(ArendReferenceKey)

    val cachedOrNull: Referable?
        get() {
            val cached = getUserData(ArendReferenceKey)
            return if (cached == TCDefReferable.NULL_REFERABLE) null else cached
        }

    val isCachedErrorReference: Boolean
        get() = cachedReferable == TCDefReferable.NULL_REFERABLE

    fun putResolved(referable: Referable?) {
        putUserData(ArendReferenceKey, referable ?: TCDefReferable.NULL_REFERABLE)
    }

    fun resolve(): Referable? {
        val cached = cachedReferable
        if (cached != null) {
            return if (cached == TCDefReferable.NULL_REFERABLE) null else cached
        }

        val module = referenceModule ?: return null
        project.service<ArendServerService>().server.getCheckerFor(listOf(module)).resolveModules(DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ResolverListener.EMPTY, false)
        return cachedOrNull
    }

    fun resolvePsi(): PsiElement? =
        when (val ref = resolve()?.abstractReferable) {
            is PsiElement -> ref
            is PsiModuleReferable -> ref.modules.firstOrNull()
            else -> null
        }

    companion object {
        fun cacheResolved(reference: UnresolvedReference, resolved: Referable) {
            val references = reference.getReferenceList()
            var referable = resolved
            for (i in reference.getPath().indices.reversed()) {
                if (i < references.size && references[i] != null) {
                    (references[i] as? ArendReferenceElement)?.putResolved(referable)
                }
                referable = (referable as? LocatedReferable)?.locatedReferableParent ?: break
            }
        }
    }
}
