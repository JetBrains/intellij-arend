package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.naming.reference.UnresolvedReference
import org.arend.psi.*
import org.arend.psi.doc.ArendDocComment
import org.arend.resolving.ArendDefReferenceImpl
import org.arend.resolving.ArendPatternDefReferenceImpl
import org.arend.resolving.ArendReference
import org.arend.resolving.ArendReferenceImpl
import org.arend.term.abs.Abstract
import org.arend.resolving.util.ReferableExtractVisitor
import org.arend.resolving.util.getTypeOf
import org.arend.util.mapUntilNotNull

abstract class ArendIdentifierBase(node: ASTNode) : PsiReferableImpl(node), ArendReferenceElement {
    override fun getNameIdentifier(): PsiElement? = firstChild

    override val referenceNameElement
        get() = this

    override fun getName(): String = referenceName

    override val rangeInElement: TextRange
        get() = TextRange(0, text.length)

    private fun getTopmostExpression(element: PsiElement): ArendCompositeElement? {
        var expr: ArendExpr? = null
        var cur = element
        while (cur !is PsiLocatedReferable && cur !is PsiFile) {
            if (cur is ArendExpr) {
                expr = cur
            }
            cur = cur.parent
        }
        return expr ?: cur as? PsiLocatedReferable
    }

    override fun getUseScope(): SearchScope {
        val parent = parent
        val pParent = parent?.parent
        if (pParent is ArendNameTele) {
            val function = parent.parent.parent
            var prevSibling = function.parent.prevSibling
            if (prevSibling is PsiWhiteSpace) prevSibling = prevSibling.prevSibling
            val docComment = prevSibling as? ArendDocComment
            return if (docComment != null) LocalSearchScope(arrayOf(function, docComment)) else LocalSearchScope(function)
        }

        if (parent is ArendLetClause ||
            (pParent as? ArendTypedExpr)?.parent is ArendTypeTele ||
            pParent is ArendNameTele ||
            pParent is ArendLamTele ||
            parent is ArendAtomPatternOrPrefix && pParent != null ||
            parent is ArendPattern ||
            parent is ArendCaseArg || parent is ArendCaseArgExprAs ||
            parent is ArendLongName ||
            parent is ArendLevelParam) {

            getTopmostExpression(parent)?.let {
                return LocalSearchScope(it)
            }
        }

        return super.getUseScope()
    }
}

abstract class ArendDefIdentifierImplMixin(node: ASTNode) : ArendIdentifierBase(node), ArendDefIdentifier {
    override val referenceName: String
        get() = id.text

    override val longName: List<String>
        get() = listOf(referenceName)

    override val resolve: PsiElement?
        get() = this

    override val unresolvedReference: UnresolvedReference?
        get() = null

    override fun setName(name: String): PsiElement? =
            this.replaceWithNotification(ArendPsiFactory(project).createDefIdentifier(name))

    override fun textRepresentation(): String = referenceName

    override fun getReference(): ArendReference {
        return when (parent) {
            is ArendPattern, is ArendAtomPatternOrPrefix -> ArendPatternDefReferenceImpl<ArendDefIdentifier>(this)
            else -> ArendDefReferenceImpl<ArendDefIdentifier>(this)
        }
    }

    override fun getTypeClassReference(): ClassReferable? {
        val parent = parent
        return if (parent is ArendLetClauseImplMixin) {
            parent.typeClassReference
        } else {
            (typeOf as? ArendExpr)?.let { ReferableExtractVisitor().findClassReferable(it) }
        }
    }

    override val typeOf: Abstract.Expression?
        get() = when (val parent = parent) {
            is ArendIdentifierOrUnknown -> getTeleType(parent.parent)
            is ArendFieldDefIdentifier -> (parent.parent as? ArendFieldTele)?.expr
            is ArendLetClause -> getTypeOf(parent.parameters, parent.resultType)
            is ArendPatternImplMixin -> parent.expr
            is ArendAsPattern -> parent.expr
            else -> null
        }

    override val psiElementType: PsiElement?
        get() {
            return when (val parent = parent) {
                is ArendLetClause -> parent.typeAnnotation?.expr
                is ArendIdentifierOrUnknown -> getTeleType(parent.parent)
                else -> null
            }
        }
}

abstract class ArendRefIdentifierImplMixin(node: ASTNode) : ArendIdentifierBase(node), ArendRefIdentifier {
    override val referenceName: String
        get() = id.text

    override val longName: List<String>
        get() = (parent as? ArendLongName)?.refIdentifierList?.mapUntilNotNull { if (it == this) null else it.referenceName }?.apply { add(referenceName) }
            ?: listOf(referenceName)

    override val resolve: PsiElement?
        get() = reference.resolve()

    override val unresolvedReference: UnresolvedReference?
        get() = referent

    override fun setName(name: String): PsiElement? =
        this.replaceWithNotification(ArendPsiFactory(project).createRefIdentifier(name))

    override fun getData() = this

    override fun getReferent() = NamedUnresolvedReference(this, referenceName)

    override fun getReference(): ArendReference {
        val parent = parent as? ArendLongName
        val isImport = (parent?.parent as? ArendStatCmd)?.importKw != null
        val last = if (isImport) parent?.refIdentifierList?.lastOrNull() else null
        return ArendReferenceImpl<ArendRefIdentifier>(this, last != null && last != this)
    }

    override fun getTopmostEquivalentSourceNode() = getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = getParentSourceNode(this)
}

abstract class ArendAliasIdentifierImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), PsiNameIdentifierOwner {
    override fun getNameIdentifier(): PsiElement? = firstChild

    override fun setName(name: String): PsiElement? =
            this.replaceWithNotification(ArendPsiFactory(project).createAliasIdentifier(name))
}
