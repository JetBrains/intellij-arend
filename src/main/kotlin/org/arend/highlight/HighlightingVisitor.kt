package org.arend.highlight

import com.intellij.openapi.util.TextRange
import org.arend.naming.reference.*
import org.arend.naming.resolving.typing.TypingInfo
import org.arend.psi.ext.*
import org.arend.psi.extendLeft
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.DefinableMetaDefinition
import org.arend.typechecking.visitor.VoidConcreteVisitor

class HighlightingVisitor(private val collector: HighlightingCollector, private val typingInfo: TypingInfo) : VoidConcreteVisitor<Void>() {
    private fun resolveReference(data: Any?, referent: Referable) {
        if (referent is ErrorReference) return

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
                referent is GlobalReferable && typingInfo.getRefPrecedence(referent).isInfix ->
                    collector.addHighlightInfo(lastReference.textRange, ArendHighlightingColors.OPERATORS)

                (((referent as? RedirectingReferable)?.originalReferable
                    ?: referent) as? MetaReferable)?.resolver != null ->
                    collector.addHighlightInfo(lastReference.textRange, ArendHighlightingColors.META_RESOLVER)
            }
        }

        val index = list.size - 1
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
                collector.addHighlightInfo(textRange, ArendHighlightingColors.LONG_NAME)
            }
        }

        if (data is ArendPattern && (referent as? GlobalReferable?)?.kind == GlobalReferable.Kind.CONSTRUCTOR) {
            collector.addHighlightInfo(data.textRange, if (referent.precedence.isInfix) ArendHighlightingColors.OPERATORS else ArendHighlightingColors.CONSTRUCTOR_PATTERN)
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
                    collector.addHighlightInfo(id.textRange, ArendHighlightingColors.CLASS_PARAMETER)
                }
            }
        }
    }

    private fun processDefinition(definition: Concrete.ResolvableDefinition) {
        (definition.data.data as? PsiLocatedReferable)?.let { ref ->
            if (ref.isValid) {
                ref.nameIdentifier?.let {
                    collector.addHighlightInfo(it.textRange, ArendHighlightingColors.DECLARATION)
                }
                (ref as? ReferableBase<*>)?.alias?.aliasIdentifier?.let {
                    collector.addHighlightInfo(it.textRange, ArendHighlightingColors.DECLARATION)
                }
            }
        }
        highlightParameters(definition)
    }

    override fun visitFunction(def: Concrete.BaseFunctionDefinition, params: Void?): Void? {
        processDefinition(def)
        return super.visitFunction(def, params)
    }

    override fun visitMeta(def: DefinableMetaDefinition, params: Void?): Void? {
        processDefinition(def)
        return super.visitMeta(def, params)
    }

    override fun visitData(def: Concrete.DataDefinition, params: Void?): Void? {
        processDefinition(def)
        return super.visitData(def, params)
    }

    override fun visitConstructor(def: Concrete.Constructor, params: Void?) {
        highlightParameters(def)
        super.visitConstructor(def, params)
    }

    override fun visitClass(def: Concrete.ClassDefinition, params: Void?): Void? {
        processDefinition(def)
        return super.visitClass(def, params)
    }

    override fun visitReference(expr: Concrete.ReferenceExpression, params: Void?): Void? {
        resolveReference(expr.data, expr.referent)
        return null
    }

    override fun visitFieldCall(expr: Concrete.FieldCallExpression, params: Void?): Void? {
        resolveReference(expr.data, expr.field)
        return super.visitFieldCall(expr, params)
    }

    override fun visitVar(expr: Concrete.VarLevelExpression, param: Void?): Void {
        resolveReference(expr.data, expr.referent)
        return super.visitVar(expr, param)
    }

    override fun visitPattern(pattern: Concrete.Pattern, params: Void?) {
        if (pattern is Concrete.ConstructorPattern) {
            val referent = pattern.constructor
            if (referent != null) resolveReference(pattern.data, referent)
        }
        super.visitPattern(pattern, params)
    }

    override fun visitClassElement(element: Concrete.ClassElement, params: Void?) {
        val field = element.field
        if (field != null) resolveReference(element.data, field)
        super.visitClassElement(element, params)
    }
}