package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.*
import org.arend.naming.scope.Scope.ScopeContext
import org.arend.psi.*
import org.arend.psi.doc.ArendDocComment
import org.arend.psi.doc.ArendDocReference
import org.arend.resolving.*
import org.arend.term.abs.Abstract
import org.arend.util.mapUntilNotNull

abstract class ArendIdentifierBase(node: ASTNode) : PsiReferableImpl(node), ArendReferenceElement {
    override fun getNameIdentifier(): PsiElement? = firstChild

    override val referenceNameElement
        get() = this

    override fun getName(): String = referenceName

    override val rangeInElement: TextRange
        get() = TextRange(0, text.length)

    override fun getReferenceText(): String = referenceName

    override fun getReferenceModule() = runReadAction {
        (containingFile as? ArendFile)?.moduleLocation
    }

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
            val function = pParent.parent
            var prevSibling = function.parent.prevSibling
            if (prevSibling is PsiWhiteSpace) prevSibling = prevSibling.prevSibling
            val docComment = prevSibling as? ArendDocComment
            return if (docComment != null) LocalSearchScope(arrayOf(function, docComment)) else LocalSearchScope(function)
        }

        if (parent is ArendLetClause ||
            (pParent as? ArendTypedExpr)?.parent is ArendTypeTele ||
            parent is ArendPattern ||
            parent is ArendCaseArg ||
            parent is ArendLongName) {

            getTopmostExpression(parent)?.let {
                return LocalSearchScope(it)
            }
        }

         if (parent is ArendNsId && pParent is ArendNsUsing) {
            pParent.ancestor<ArendGroup>()?.let { return@getUseScope LocalSearchScope(it) }
        }

        return super.getUseScope()
    }
}

abstract class ArendDefIdentifierBase(node: ASTNode) : ArendIdentifierBase(node) {
    override val longName: List<String>
        get() = listOf(referenceName)

    override val resolve: PsiElement?
        get() = this

    override val unresolvedReference: UnresolvedReference?
        get() = null

    override fun setName(name: String): PsiElement? =
            this.replace(ArendPsiFactory(project).createDefIdentifier(name))

    override fun getRefName(): String = referenceName

    override fun getReference() = when (parent) {
        is PsiLocatedReferable -> null
        else -> ArendDefReferenceImpl<ArendReferenceElement>(this)
    }

    override val psiElementType: PsiElement?
        get() {
            return when (val parent = parent) {
                is ArendLetClause -> parent.resultType
                is ArendIdentifierOrUnknown -> getTeleType(parent.parent)
                else -> null
            }
        }
}

class ArendDefIdentifier(node: ASTNode) : ArendDefIdentifierBase(node) {
    val id: PsiElement
        get() = childOfTypeStrict(ArendElementTypes.ID)

    override val referenceName: String
        get() = id.text
}

class ArendLevelIdentifier(node: ASTNode) : ArendDefIdentifierBase(node), PsiLocatedReferable {
    val id: PsiElement
        get() = childOfTypeStrict(ArendElementTypes.ID)

    override val referenceName: String
        get() = id.text

    override val defIdentifier: ArendDefIdentifier? = null

    override fun getKind() = GlobalReferable.Kind.LEVEL

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getAliasPrecedence(): Precedence = Precedence.DEFAULT

    override fun getAliasName() = null
}

class ArendRefIdentifier(node: ASTNode) : ArendIdentifierBase(node), ArendSourceNode, Abstract.Reference {
    val id: PsiElement
        get() = childOfTypeStrict(ArendElementTypes.ID)

    override val referenceName: String
        get() = id.text

    override val longName: List<String>
        get() = (parent as? ArendLongName)?.refIdentifierList?.mapUntilNotNull { if (it == this) null else it.referenceName }?.apply { add(referenceName) }
            ?: listOf(referenceName)

    override val resolve: PsiElement?
        get() = reference?.resolve()

    override val unresolvedReference: UnresolvedReference
        get() = referent

    override fun setName(name: String): PsiElement =
        this.replace(ArendPsiFactory(project).createRefIdentifier(name))

    override fun getData() = this

    override fun getReferent() = NamedUnresolvedReference(this, referenceName)

    override fun getReference(): ArendReference? {
        val parent = parent
        if (parent is ArendLongName) {
            val pParent = parent.parent
            if (pParent is ArendDocReference) {
                val ppParent = pParent.parent
                if (ppParent !is ArendDocComment || ppParent.owner == null) {
                    return null
                }
            }
        }
        return ArendReferenceImpl(this, if (parent is ArendAtomLevelExpr) (if (ancestors.filterIsInstance<ArendTopLevelLevelExpr>().firstOrNull()?.isPLevels() != false) ScopeContext.PLEVEL else ScopeContext.HLEVEL) else ScopeContext.STATIC)
    }

    override fun getTopmostEquivalentSourceNode() = getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = getParentSourceNode(this)
}

class ArendAliasIdentifier(node: ASTNode) : ArendCompositeElementImpl(node), PsiNameIdentifierOwner {
    val id: PsiElement
        get() = childOfTypeStrict(ArendElementTypes.ID)

    override fun getNameIdentifier(): PsiElement? = firstChild

    override fun setName(name: String): PsiElement =
        replace(ArendPsiFactory(project).createAliasIdentifier(name))
}
