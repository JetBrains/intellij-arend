package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IStubElementType
import org.arend.naming.reference.*
import org.arend.psi.*
import org.arend.psi.stubs.ArendClassFieldParamStub
import org.arend.resolving.ArendDefReferenceImpl
import org.arend.resolving.ArendReference
import org.arend.ext.concrete.definition.ClassFieldKind
import org.arend.term.abs.Abstract
import org.arend.term.group.AccessModifier

class ArendFieldDefIdentifier : ArendClassFieldBase<ArendClassFieldParamStub>, ArendReferenceElement, StubBasedPsiElement<ArendClassFieldParamStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendClassFieldParamStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val parentFieldTele: ArendFieldTele?
        get() = parent as? ArendFieldTele

    override val defIdentifier: ArendDefIdentifier?
        get() = childOfType()

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = refName

    override val longName: List<String>
        get() = listOf(referenceName)

    override val resolve: PsiElement
        get() = this

    override val unresolvedReference: UnresolvedReference?
        get() = null

    override fun getClassFieldKind() = ClassFieldKind.ANY

    override fun getReference(): ArendReference = ArendDefReferenceImpl(this)

    override val isVisible
        get() = false

    override fun isExplicitField() = parentFieldTele?.isExplicit ?: true

    override fun isParameterField() = true

    override fun isClassifying() = parentFieldTele?.isClassifying == true

    override fun isCoerce() = parentFieldTele?.isCoerce == true

    override fun getParameters(): List<Abstract.Parameter> = emptyList()

    override fun getResultType(): ArendExpr? = parentFieldTele?.type

    override fun getResultTypeLevel(): ArendExpr? = null

    override fun getUseScope() = GlobalSearchScope.projectScope(project)

    override fun getReferenceText(): String = referenceName

    override fun getReferenceModule() = (containingFile as? ArendFile)?.moduleLocation

    override val psiElementType: PsiElement?
        get() = resultType

    override val rangeInElement: TextRange
        get() = TextRange(0, text.length)

    override fun getAccessModifier(): AccessModifier =
        super.getAccessModifier().max(classAccessModifier)

    private val classAccessModifier: AccessModifier
        get() = ancestor<ArendDefClass>()?.accessModifier ?: AccessModifier.PUBLIC
}