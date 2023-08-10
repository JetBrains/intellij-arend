package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.childrenOfType
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.*
import org.arend.psi.*
import org.arend.psi.doc.ArendDocComment
import org.arend.resolving.*
import org.arend.resolving.util.ReferableExtractVisitor
import org.arend.resolving.util.getTypeOf
import org.arend.term.NamespaceCommand
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

abstract class ArendDefIdentifierBase(node: ASTNode, private val refKind: Referable.RefKind) : ArendIdentifierBase(node) {
    override val longName: List<String>
        get() = listOf(referenceName)

    override val resolve: PsiElement?
        get() = this

    override val unresolvedReference: UnresolvedReference?
        get() = null

    override fun setName(name: String): PsiElement? =
            this.replace(ArendPsiFactory(project).createDefIdentifier(name))

    override fun textRepresentation(): String = referenceName

    override fun getReference() = when (parent) {
        is PsiLocatedReferable -> null
        else -> ArendDefReferenceImpl<ArendReferenceElement>(this)
    }

    override fun getRefKind() = refKind

    override val psiElementType: PsiElement?
        get() {
            return when (val parent = parent) {
                is ArendLetClause -> parent.resultType
                is ArendIdentifierOrUnknown -> getTeleType(parent.parent)
                else -> null
            }
        }
}

class ArendDefIdentifier(node: ASTNode) : ArendDefIdentifierBase(node, Referable.RefKind.EXPR), TypedReferable {
    val id: PsiElement
        get() = childOfTypeStrict(ArendElementTypes.ID)

    override val referenceName: String
        get() = id.text

    override val typeOf: Abstract.Expression?
        get() = when (val parent = parent) {
            is ArendIdentifierOrUnknown -> getTeleType(parent.parent)
            is ArendFieldDefIdentifier -> (parent.parent as? ArendFieldTele)?.type
            is ArendLetClause -> getTypeOf(parent.parameters, parent.resultType)
            is ArendAsPattern -> parent.type
            is ArendPattern -> {
                val parentParent = parent.parent
                if (parentParent is ArendPattern && parentParent.childrenOfType<ArendPattern>().size == 1) parentParent.type else null
            }
            else -> null
        }

    override fun getTypeClassReference(): ClassReferable? {
        val parent = parent
        return if (parent is ArendLetClause) {
            parent.typeClassReference
        } else {
            (typeOf as? ArendExpr)?.let { ReferableExtractVisitor().findClassReferable(it) }
        }
    }
}

class ArendLevelIdentifier(node: ASTNode, refKind: Referable.RefKind) : ArendDefIdentifierBase(node, refKind), PsiLocatedReferable, LevelReferable, ArendReferenceElement {
    val id: PsiElement
        get() = childOfTypeStrict(ArendElementTypes.ID)

    override val referenceName: String
        get() = id.text

    override val defIdentifier: ArendDefIdentifier? = null

    override fun getData() = this

    override fun getKind() = GlobalReferable.Kind.LEVEL

    override fun getLocation() = if (isValid) (containingFile as? ArendFile)?.moduleLocation else null

    override fun getTypecheckable() = this

    override fun getLocatedReferableParent() = parent?.ancestor<PsiLocatedReferable>()

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    private var tcReferableCache: TCLevelReferable? = null

    override val tcReferableCached: IntellijTCReferable?
        get() = tcReferableCache as? IntellijTCReferable

    override fun dropTCReferable() {
        tcReferableCache = null
    }

    override val tcReferable: TCReferable?
        get() = tcReferableCache ?: runReadAction {
            val parent = parent
            synchronized(parent) {
                tcReferableCache ?: run {
                    val file = (if (parent.isValid) parent.containingFile as? ArendFile else null) ?: return@run null
                    val list = parent.getChildrenOfType<ArendLevelIdentifier>().ifEmpty { listOf(this) }
                    val index = list.indexOf(this).let { if (it == -1) 0 else it }
                    val longName = list.first().refLongName
                    val tcRefMap = file.getTCRefMap(refKind)
                    (tcRefMap[longName] as? TCLevelReferable)?.let {
                        val refs = it.defParent.referables
                        if (index < refs.size) {
                            tcReferableCache = refs[index]
                            return@run refs[index]
                        }
                    }
                    val locatedParent = locatedReferableParent
                    val actualParent = if (locatedParent is ArendFile) locatedParent.moduleLocation?.let { FullModuleReferable(it) } else locatedParent?.tcReferable
                    val tcList = ArrayList<IntellijTCLevelReferable>(list.size)
                    val levelDef = LevelDefinition(refKind == Referable.RefKind.PLEVEL, parent.childOfType<ArendLevelCmp>()?.isIncreasing != false, tcList, actualParent)
                    for (ref in list) {
                        tcList.add(IntellijTCLevelReferable(SmartPointerManager.getInstance(file.project).createSmartPsiElementPointer(ref, file), ref.refName, levelDef))
                    }
                    tcRefMap[longName] = tcList[0]
                    tcReferableCache = tcList[index]
                    tcList[index]
                }
            }
        }
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
        get() = reference.resolve()

    override val unresolvedReference: UnresolvedReference
        get() = referent

    override fun setName(name: String): PsiElement =
        this.replace(ArendPsiFactory(project).createRefIdentifier(name))

    override fun getData() = this

    override fun getReferent() = NamedUnresolvedReference(this, referenceName)

    override fun getReference(): ArendReference {
        val parent = parent
        val parentLongName = parent as? ArendLongName
        val isImport = (parentLongName?.parent as? ArendStatCmd)?.kind == NamespaceCommand.Kind.IMPORT
        val last = if (isImport) parentLongName?.refIdentifierList?.lastOrNull() else null
        return ArendReferenceImpl(this, last != null && last != this, if (parent is ArendAtomLevelExpr) (if (ancestors.filterIsInstance<ArendTopLevelLevelExpr>().firstOrNull()?.isPLevels() != false) Referable.RefKind.PLEVEL else Referable.RefKind.HLEVEL) else Referable.RefKind.EXPR)
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
