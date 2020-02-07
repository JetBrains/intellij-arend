package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.naming.reference.Referable
import org.arend.naming.reference.UnresolvedReference
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.psi.ArendFile
import org.arend.psi.ArendIPName
import org.arend.psi.ArendLiteral
import org.arend.psi.ArendLongName
import org.arend.resolving.ArendReference
import org.arend.resolving.ArendReferenceImpl
import org.arend.term.Fixity

abstract class ArendIPNameImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), ArendIPName {
    override val referenceNameElement
        get() = this

    override fun getReference(): ArendReference = ArendReferenceImpl(this)

    override val unresolvedReference: UnresolvedReference?
        get() = referent

    override val resolvedInScope: Referable?
        get() = referenceNameElement.resolvedInScope

    override val resolve
        get() = referenceNameElement.reference.resolve()

    override val scope: Scope
        get() {
            val literal = parentLiteral ?: return (containingFile as? ArendFile)?.scope ?: EmptyScope.INSTANCE
            val longName = literal.longName ?: return literal.scope
            return LongUnresolvedReference(this, longName.refIdentifierList.map { it.referenceName }).resolveNamespaceWithArgument(literal.scope)
        }

    override val referenceName: String
        get() = infix?.text?.removeSurrounding("`")
            ?: postfix?.text?.removePrefix("`")
            ?: error("ArendIPName (referenceName): incorrect expression")

    override val longName: List<String>
        get() = parentLongName?.let { it.refIdentifierList.map { ref -> ref.referenceName } + listOf(referenceName) } ?: listOf(referenceName)

    override val rangeInElement: TextRange
        get() {
            infix?.text?.let { text ->
                val len = text.length
                val start = if (len > 0 && text[0] == '`') 1 else 0
                val end = if (len > 0 && text[len-1] == '`') len-1 else len
                return if (start > end) TextRange(0, 1) else TextRange(start, end)
            }
            postfix?.text?.let { text ->
                val len = text.length
                return if (len > 0 && text[0] == '`') TextRange(1, len) else TextRange(0, 0)
            }
            error("ArendIPName (rangeInElement): incorrect expression")
        }

    val referent: UnresolvedReference
        get() {
            val longName = parentLongName
            return if (longName == null) {
                NamedUnresolvedReference(this, referenceName)
            } else {
                LongUnresolvedReference.make(this, longName.refIdentifierList.map { it.referenceName } + listOf(referenceName))
            }
        }

    val fixity: Fixity
        get() = when {
            infix != null -> Fixity.INFIX
            postfix != null -> Fixity.POSTFIX
            else -> Fixity.UNKNOWN
        }

    val parentLiteral: ArendLiteral?
        get() = parent as? ArendLiteral

    val parentLongName: ArendLongName?
        get() = (parent as? ArendLiteral)?.longName
}