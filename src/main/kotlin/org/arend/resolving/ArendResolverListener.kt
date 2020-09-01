package org.arend.resolving

import org.arend.ext.reference.DataContainer
import org.arend.naming.reference.ErrorReference
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.resolving.ResolverListener
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ext.ArendIPNameImplMixin
import org.arend.psi.ext.ArendReferenceElement
import org.arend.term.NameRenaming
import org.arend.term.NamespaceCommand
import org.arend.term.concrete.Concrete

open class ArendResolverListener(private val resolverCache: ArendResolveCache) : ResolverListener {
    private fun replaceCache(reference: ArendReferenceElement, resolvedRef: Referable?) {
        val newRef = if (resolvedRef is ErrorReference) null else resolvedRef?.underlyingReferable
        val oldRef = resolverCache.replaceCache(newRef, reference)
        if (oldRef != null && oldRef != newRef && !(newRef == null && oldRef == TCDefReferable.NULL_REFERABLE)) {
            resetDefinition = true
        }
    }

    private fun replaceCache(list: List<ArendReferenceElement>, resolvedRefs: List<Referable?>) {
        var i = 0
        for (reference in list) {
            replaceCache(reference, if (i < resolvedRefs.size) resolvedRefs[i++] else null)
        }
    }

    protected open fun resolveReference(data: Any?, referent: Referable, list: List<ArendReferenceElement>, resolvedRefs: List<Referable?>) {}

    private fun resolveReference(data: Any?, referent: Referable, resolvedRefs: List<Referable?>) {
        val list = when (data) {
            is ArendLongName -> data.refIdentifierList
            is ArendIPNameImplMixin -> {
                val last: List<ArendReferenceElement> = listOf(data)
                data.parentLongName?.let { it.refIdentifierList + last } ?: last
            }
            is ArendReferenceElement -> listOf(data)
            is ArendPattern -> data.defIdentifier?.let { listOf<ArendReferenceElement>(it) } ?: data.longName?.refIdentifierList ?: return
            is ArendAtomPatternOrPrefix -> data.defIdentifier?.let { listOf(it) } ?: return
            else -> return
        }

        resolveReference(data, referent, list, resolvedRefs)
        replaceCache(list, resolvedRefs)
    }

    override fun bindingResolved(binding: Referable) {
        val ref = (binding as? DataContainer)?.data as? ArendRefIdentifier ?: return
        resolverCache.replaceCache(ref, ref)
    }

    override fun referenceResolved(argument: Concrete.Expression?, originalRef: Referable, refExpr: Concrete.ReferenceExpression, resolvedRefs: List<Referable?>, scope: Scope) {
        resolveReference(refExpr.data, refExpr.referent, resolvedRefs)
    }

    override fun patternResolved(originalRef: Referable, pattern: Concrete.ConstructorPattern, resolvedRefs: List<Referable?>) {
        resolveReference(pattern.data, pattern.constructor, resolvedRefs)
    }

    override fun patternResolved(pattern: Concrete.NamePattern) {
        pattern.referable?.let {
            resolveReference(pattern.data, it, listOf(it))
        }
    }

    override fun coPatternResolved(element: Concrete.CoClauseElement, originalRef: Referable?, referable: Referable, resolvedRefs: List<Referable?>) {
        val data = element.data
        (((data as? ArendCoClauseDef)?.parent ?: data) as? CoClauseBase)?.longName?.let {
            resolveReference(it, referable, resolvedRefs)
        }
    }

    override fun overriddenFieldResolved(overriddenField: Concrete.OverriddenField, originalRef: Referable?, referable: Referable, resolvedRefs: List<Referable?>) {
        (overriddenField.data as? ArendOverriddenField)?.longName?.let {
            resolveReference(it, referable, resolvedRefs)
        }
    }

    override fun namespaceResolved(namespaceCommand: NamespaceCommand, resolvedRefs: List<Referable?>) {
        (namespaceCommand as? ArendStatCmd)?.longName?.let {
            replaceCache(it.refIdentifierList, resolvedRefs)
        }
    }

    override fun renamingResolved(renaming: NameRenaming, originalRef: Referable?, resolvedRef: Referable?) {
        (renaming as? ArendNsId)?.refIdentifier?.let {
            replaceCache(it, resolvedRef)
        }
    }

    protected var resetDefinition = false

    override fun beforeDefinitionResolved(definition: Concrete.ResolvableDefinition?) {
        resetDefinition = false
    }
}