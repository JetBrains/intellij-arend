package org.arend.resolving

import org.arend.ext.reference.DataContainer
import org.arend.naming.reference.ErrorReference
import org.arend.naming.reference.LocalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.ResolverListener
import org.arend.psi.ext.*
import org.arend.term.NameRenaming
import org.arend.term.NamespaceCommand
import org.arend.term.concrete.Concrete

open class ArendResolverListener(private val resolverCache: ArendResolveCache) : ResolverListener {
    private fun replaceCache(reference: ArendReferenceElement, resolvedRef: Referable?) {
        val newRef = if (resolvedRef is ErrorReference) null else resolvedRef?.underlyingReferable
        if (newRef is LocalReferable) return
        resolverCache.replaceCache(newRef, reference)
    }

    private fun replaceCache(list: List<ArendReferenceElement>, resolvedRefs: List<Referable?>) {
        var i = 0
        for (reference in list) {
            replaceCache(reference, if (i < resolvedRefs.size) resolvedRefs[i++] else null)
        }
    }

    protected open fun resolveReference(data: Any?, referent: Referable?, list: List<ArendReferenceElement>, resolvedRefs: List<Referable?>) {}

    private fun resolveReference(data: Any?, referent: Referable, resolvedRefs: List<Referable?>) {
        val list = when (data) {
            is ArendLongName -> data.refIdentifierList
            is ArendAtomFieldsAcc -> listOfNotNull(data.atom.literal?.refIdentifier) + data.fieldAccList.mapNotNull { it.refIdentifier } + listOfNotNull(data.ipName)
            is ArendReferenceElement -> listOf(data)
            is ArendPattern -> {
                val defId = data.singleReferable
                if (defId != null) {
                    listOf(defId)
                } else {
                    when (val ref = data.referenceElement) {
                        is ArendLongName -> ref.refIdentifierList
                        is ArendIPName -> listOf(ref)
                        else -> return
                    }
                }
            }
            is ArendAtomLevelExpr -> data.refIdentifier?.let { listOf(it) } ?: return
            else -> return
        }

        val resolved = if (list.size < resolvedRefs.size) resolvedRefs.subList(0, list.size) else resolvedRefs
        resolveReference(data, referent, list, resolved)
        replaceCache(list, resolved)
    }

    override fun bindingResolved(binding: Referable) {
        val ref = (binding as? DataContainer)?.data as? ArendRefIdentifier ?: return
        resolverCache.replaceCache(ref, ref)
    }

    override fun referenceResolved(expr: Concrete.Expression?, originalRef: Referable, refExpr: Concrete.ReferenceExpression, resolvedRefs: List<Referable?>) {
        resolveReference(refExpr.data, refExpr.referent, resolvedRefs)
    }

    override fun levelResolved(originalRef: Referable?, refExpr: Concrete.VarLevelExpression, resolvedRef: Referable, availableRefs: MutableCollection<Referable>?) {
        resolveReference(refExpr.data, refExpr.referent, listOf(resolvedRef))
    }

    override fun patternResolved(originalRef: Referable?, newRef: Referable, pattern: Concrete.Pattern, resolvedRefs: List<Referable?>) {
        resolveReference(pattern.data, newRef, resolvedRefs)
    }

    override fun coPatternResolved(element: Concrete.CoClauseElement, originalRef: Referable?, referable: Referable, resolvedRefs: List<Referable?>) {
        val data = element.data
        (((data as? ArendCoClauseDef)?.parent ?: data) as? CoClauseBase)?.longName?.let {
            resolveReference(it, referable, resolvedRefs)
        }
    }

    override fun overriddenFieldResolved(overriddenField: Concrete.OverriddenField, originalRef: Referable?, referable: Referable, resolvedRefs: List<Referable?>) {
        (overriddenField.data as? ArendOverriddenField)?.overriddenField?.let {
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

    private fun levelParametersResolved(params: Concrete.LevelParameters?) {
        if (params == null) return
        for (ref in params.referables) {
            val refId = (ref as? DataContainer)?.data as? ArendRefIdentifier ?: continue
            resolverCache.replaceCache(refId, refId)
        }
    }

    override fun definitionResolved(definition: Concrete.ResolvableDefinition) {
        if (definition !is Concrete.Definition) return
        levelParametersResolved(definition.pLevelParameters)
        levelParametersResolved(definition.hLevelParameters)
    }
}