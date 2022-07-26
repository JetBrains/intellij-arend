package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.*
import org.arend.psi.*
import org.arend.psi.parser.api.ArendPattern
import org.arend.psi.doc.ArendDocComment
import org.arend.resolving.ArendDefReferenceImpl
import org.arend.resolving.ArendPatternDefReferenceImpl
import org.arend.resolving.ArendReference
import org.arend.resolving.ArendReferenceImpl
import org.arend.resolving.util.ReferableExtractVisitor
import org.arend.resolving.util.getTypeOf
import org.arend.term.abs.Abstract
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
            pParent is ArendLamTele ||
            parent is ArendPattern ||
            parent is ArendCaseArg || parent is ArendCaseArgExprAs ||
            parent is ArendLongName) {

            getTopmostExpression(parent)?.let {
                return LocalSearchScope(it)
            }
        }

        return super.getUseScope()
    }
}

abstract class ArendDefIdentifierBase(node: ASTNode, private val refKind: Referable.RefKind) : ArendIdentifierBase(node) {
    override val longName: List<String>
        get() = listOf(referenceName)

    override val resolve: PsiElement?
        get() = this

    override val unresolvedReference: UnresolvedReference?
        get() = null

    override fun setName(name: String): PsiElement? =
            this.replaceWithNotification(ArendPsiFactory(project).createDefIdentifier(name))

    override fun textRepresentation(): String = referenceName

    override fun getReference(): ArendReference = when (parent) {
        is ArendPattern -> ArendPatternDefReferenceImpl<ArendReferenceElement>(this)
        else -> ArendDefReferenceImpl<ArendReferenceElement>(this)
    }

    override fun getTypeClassReference(): ClassReferable? {
        val parent = parent
        return if (parent is ArendLetClauseImplMixin) {
            parent.typeClassReference
        } else {
            (typeOf as? ArendExpr)?.let { ReferableExtractVisitor().findClassReferable(it) }
        }
    }

    override fun getRefKind() = refKind

    override val typeOf: Abstract.Expression?
        get() = when (val parent = parent) {
            is ArendIdentifierOrUnknown -> getTeleType(parent.parent)
            is ArendFieldDefIdentifier -> (parent.parent as? ArendFieldTele)?.expr
            is ArendLetClause -> getTypeOf(parent.parameters, parent.resultType)
//            is ArendPattern -> parent.expr
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

abstract class ArendDefIdentifierImplMixin(node: ASTNode) : ArendDefIdentifierBase(node, Referable.RefKind.EXPR), ArendDefIdentifier {
    override val referenceName: String
        get() = id.text
}

abstract class ArendLevelIdentifierBase(node: ASTNode, refKind: Referable.RefKind) : ArendDefIdentifierBase(node, refKind), PsiLocatedReferable, LevelReferable {
    override val defIdentifier: ArendDefIdentifier? = null

    override fun getData() = this

    override fun getKind() = GlobalReferable.Kind.LEVEL

    override fun getLocation() = if (isValid) (containingFile as? ArendFile)?.moduleLocation else null

    override fun getTypecheckable() = this

    override fun getLocatedReferableParent() = parent?.ancestor<PsiLocatedReferable>()

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    private var tcReferableCache: TCLevelReferable? = null

    override val tcReferable: TCReferable?
        get() = tcReferableCache ?: runReadAction {
            val parent = parent
            synchronized(parent) {
                tcReferableCache ?: run {
                    val file = (if (parent.isValid) parent.containingFile as? ArendFile else null) ?: return@run null
                    val list: List<PsiLocatedReferable> = PsiTreeUtil.getChildrenOfTypeAsList(parent, if (refKind == Referable.RefKind.PLEVEL) ArendPLevelIdentifier::class.java else ArendHLevelIdentifier::class.java)
                            .let { if (it.isEmpty()) listOf(this) else it }
                    val index = list.indexOf(this).let { if (it == -1) 0 else it }
                    val longName = list.first().refLongName
                    val tcRefMap = file.getTCRefMap(refKind)
                    (tcRefMap[longName] as? TCLevelReferable)?.let {
                        val refs = it.defParent.referables
                        if (index < refs.size) {
                            tcReferableCache = refs[index]
                            return@run it
                        }
                    }
                    val locatedParent = locatedReferableParent
                    val actualParent = if (locatedParent is ArendFile) locatedParent.moduleLocation?.let { FullModuleReferable(it) } else locatedParent?.tcReferable
                    val tcList = ArrayList<TCLevelReferable>(list.size)
                    val levelDef = LevelDefinition(refKind == Referable.RefKind.PLEVEL, PsiTreeUtil.getChildOfType(parent, ArendLevelCmp::class.java)?.lessOrEquals == null, tcList, actualParent)
                    for (ref in list) {
                        tcList.add(TCLevelReferable(SmartPointerManager.getInstance(file.project).createSmartPsiElementPointer<PsiLocatedReferable>(ref, file), ref.refName, levelDef))
                    }
                    tcRefMap[longName] = tcList[0]
                    tcReferableCache = tcList[index]
                    tcList[index]
                }
            }
        }
}

abstract class ArendPLevelIdentifierImplMixin(node: ASTNode) : ArendLevelIdentifierBase(node, Referable.RefKind.PLEVEL), ArendPLevelIdentifier {
    override val referenceName: String
        get() = id.text
}

abstract class ArendHLevelIdentifierImplMixin(node: ASTNode) : ArendLevelIdentifierBase(node, Referable.RefKind.HLEVEL), ArendHLevelIdentifier {
    override val referenceName: String
        get() = id.text
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
        val parent = parent
        val parentLongName = parent as? ArendLongName
        val isImport = (parentLongName?.parent as? ArendStatCmd)?.importKw != null
        val last = if (isImport) parentLongName?.refIdentifierList?.lastOrNull() else null
        return ArendReferenceImpl<ArendRefIdentifier>(this, last != null && last != this, if (parent is ArendAtomLevelExpr) (if (ancestors.filterIsInstance<ArendTopLevelLevelExpr>().firstOrNull()?.isPLevels() != false) Referable.RefKind.PLEVEL else Referable.RefKind.HLEVEL) else Referable.RefKind.EXPR)
    }

    override fun getTopmostEquivalentSourceNode() = getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = getParentSourceNode(this)
}

abstract class ArendAliasIdentifierImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), PsiNameIdentifierOwner {
    override fun getNameIdentifier(): PsiElement? = firstChild

    override fun setName(name: String): PsiElement? =
            this.replaceWithNotification(ArendPsiFactory(project).createAliasIdentifier(name))
}
