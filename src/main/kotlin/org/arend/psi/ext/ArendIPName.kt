package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.naming.reference.UnresolvedReference
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.psi.ArendElementTypes.INFIX
import org.arend.psi.ArendElementTypes.POSTFIX
import org.arend.psi.ArendFile
import org.arend.psi.firstRelevantChild
import org.arend.resolving.ArendReference
import org.arend.resolving.ArendReferenceImpl
import org.arend.term.Fixity

class ArendIPName(node: ASTNode) : ArendCompositeElementImpl(node), ArendReferenceElement {
    val infix: PsiElement?
        get() = findChildByType(INFIX)

    val postfix: PsiElement?
        get() = findChildByType(POSTFIX)

    override val referenceNameElement
        get() = this

    override fun getReference(): ArendReference = ArendReferenceImpl(this)

    override val unresolvedReference: UnresolvedReference?
        get() = referent

    override val resolve
        get() = referenceNameElement.reference.resolve()

    override val scope: Scope
        get() {
            val literal = parentLiteral ?: return (containingFile as? ArendFile)?.scope ?: EmptyScope.INSTANCE
            val longName = literal.longName ?: return literal.scope
            return LongUnresolvedReference(this, longName.refIdentifierList.map { it.referenceName }).resolveNamespaceWithArgument(literal.scope)
        }

    override val referenceName: String
        get() {
            val child = firstRelevantChild
            return when (child.elementType) {
                INFIX -> child!!.text.removeSurrounding("`")
                POSTFIX -> child!!.text.removePrefix("`")
                else -> error("ArendIPName (referenceName): incorrect expression")
            }
        }

    override val longName: List<String>
        get() = parentLongName?.let { it.refIdentifierList.map { ref -> ref.referenceName } + listOf(referenceName) } ?: listOf(referenceName)

    override val rangeInElement: TextRange
        get() {
            val child = firstRelevantChild!!
            val text = child.text
            val len = text.length
            return when (child.elementType) {
                INFIX -> {
                    val start = if (len > 0 && text[0] == '`') 1 else 0
                    val end = if (len > 0 && text[len-1] == '`') len-1 else len
                    if (start > end) TextRange(0, 1) else TextRange(start, end)
                }
                POSTFIX -> {
                    if (len > 0 && text[0] == '`') TextRange(1, len) else TextRange(0, 0)
                }
                else -> error("ArendIPName (rangeInElement): incorrect expression")
            }
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
        get() = when (firstRelevantChild.elementType) {
            INFIX -> Fixity.INFIX
            POSTFIX -> Fixity.POSTFIX
            else -> Fixity.UNKNOWN
        }

    val parentLiteral: ArendLiteral?
        get() = parent as? ArendLiteral

    val parentLongName: ArendLongName?
        get() = (parent as? ArendLiteral)?.longName
}