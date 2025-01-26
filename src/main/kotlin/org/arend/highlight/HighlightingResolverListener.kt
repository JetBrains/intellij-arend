package org.arend.highlight

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.naming.reference.*
import org.arend.naming.resolving.ResolverListener
import org.arend.psi.ext.*
import org.arend.psi.extendLeft
import org.arend.quickfix.implementCoClause.IntentionBackEndVisitor
import org.arend.term.concrete.Concrete

class HighlightingResolverListener(private val pass: BasePass, private val progress: ProgressIndicator) : ResolverListener {
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

        val lastReference = list.lastOrNull() ?: return
        if (data !is ArendPattern && (lastReference is ArendRefIdentifier || lastReference is ArendDefIdentifier)) {
            when {
                referent is GlobalReferable && referent.precedence.isInfix ->
                    pass.addHighlightInfo(lastReference.textRange, ArendHighlightingColors.OPERATORS)

                (((referent as? RedirectingReferable)?.originalReferable
                    ?: referent) as? MetaReferable)?.resolver != null ->
                    pass.addHighlightInfo(lastReference.textRange, ArendHighlightingColors.META_RESOLVER)
            }
        }

        var index = 0
        while (index < resolvedRefs.size - 1 && index < list.size - 1 && resolvedRefs[index] !is ErrorReference) {
            index++
        }

        if (index > 0) {
            val last = list[index]
            val textRange = if (last is ArendIPName) {
                val lastParent = last.parent
                val nextToLast = last.extendLeft.prevSibling
                if (lastParent is ArendAtomFieldsAcc && nextToLast != null) {
                    TextRange(lastParent.textRange.startOffset, nextToLast.textRange.endOffset)
                } else null
            } else when (val lastParent = last.parent) {
                is ArendFieldAcc -> (lastParent.parent as? ArendAtomFieldsAcc)?.let { fieldsAcc ->
                    lastParent.extendLeft.prevSibling?.let { nextToLast ->
                        TextRange(fieldsAcc.textRange.startOffset, nextToLast.textRange.endOffset)
                    }
                }

                is ArendLongName -> last.extendLeft.prevSibling?.let { nextToLast ->
                    TextRange(lastParent.textRange.startOffset, nextToLast.textRange.endOffset)
                }

                else -> null
            }

            if (textRange != null) {
                pass.addHighlightInfo(textRange, ArendHighlightingColors.LONG_NAME)
            }
        }

        if (data is ArendPattern && (referent as? GlobalReferable?)?.kind == GlobalReferable.Kind.CONSTRUCTOR) {
            pass.addHighlightInfo(data.textRange, if (referent.precedence.isInfix) ArendHighlightingColors.OPERATORS else ArendHighlightingColors.CONSTRUCTOR_PATTERN)
        }
    }

    private fun highlightParameters(definition: Concrete.GeneralDefinition) {
        for (parameter in Concrete.getParameters(definition, true) ?: emptyList()) {
            if (((parameter.type?.underlyingReferable as? GlobalReferable)?.underlyingReferable as? ArendDefClass)?.isRecord == false) {
                val list: List<ArendCompositeElement>? = when (val param = parameter.data) {
                    is ArendFieldTele -> param.referableList
                    is ArendNameTele -> param.identifierOrUnknownList
                    is ArendTypeTele -> param.typedExpr?.identifierOrUnknownList
                    else -> null
                }
                if (list != null) for (id in list) {
                    pass.addHighlightInfo(id.textRange, ArendHighlightingColors.CLASS_PARAMETER)
                }
            }
        }
    }

    override fun definitionResolved(definition: Concrete.ResolvableDefinition) {
        progress.checkCanceled()

        (definition.data.data as? PsiLocatedReferable)?.let { ref ->
            ref.nameIdentifier?.let {
                pass.addHighlightInfo(it.textRange, ArendHighlightingColors.DECLARATION)
            }
            (ref as? ReferableBase<*>)?.alias?.aliasIdentifier?.let {
                pass.addHighlightInfo(it.textRange, ArendHighlightingColors.DECLARATION)
            }
        }

        highlightParameters(definition)
        if (definition is Concrete.DataDefinition) {
            for (constructorClause in definition.constructorClauses) {
                for (constructor in constructorClause.constructors) {
                    highlightParameters(constructor)
                }
            }
        }

        definition.accept(IntentionBackEndVisitor(), null)
    }

    override fun referenceResolved(expr: Concrete.Expression?, originalRef: Referable, refExpr: Concrete.ReferenceExpression, resolvedRefs: List<Referable?>) {
        resolveReference(refExpr.data, refExpr.referent, resolvedRefs)
    }

    override fun fieldCallResolved(expr: Concrete.FieldCallExpression, originalRef: Referable?, resolvedRef: Referable) {
        resolveReference(expr.data, resolvedRef, listOf(resolvedRef))
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
}