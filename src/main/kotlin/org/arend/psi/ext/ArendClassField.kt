package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.elementType
import org.arend.psi.stubs.ArendClassFieldStub
import org.arend.ext.concrete.definition.ClassFieldKind
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.group.AccessModifier

class ArendClassField : ArendClassFieldBase<ArendClassFieldStub>, StubBasedPsiElement<ArendClassFieldStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendClassFieldStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val returnExpr: ArendReturnExpr?
        get() = childOfType()

    override fun getClassFieldKind(): ClassFieldKind =
        when ((parent as? ArendClassStat)?.firstRelevantChild.elementType) {
            FIELD_KW -> ClassFieldKind.FIELD
            PROPERTY_KW -> ClassFieldKind.PROPERTY
            else -> ClassFieldKind.ANY
        }

    override fun getParameters(): List<ArendTypeTele> = getChildrenOfType()

    override fun getResultType(): ArendExpr? = returnExpr?.type

    override fun getResultTypeLevel(): ArendExpr? = returnExpr?.typeLevel

    override fun isVisible() = true

    override fun isExplicitField() = true

    override fun isParameterField() = false

    override fun isClassifying() = hasChildOfType(CLASSIFYING_KW)

    override fun isCoerce() = hasChildOfType(COERCE_KW)

    override fun getAccessModifier(): AccessModifier = (childOfType<ArendAccessMod>()?.accessModifier ?: AccessModifier.PUBLIC).max(classAccessModifier)

    override val psiElementType: PsiElement?
        get() = resultType

    private val classAccessModifier: AccessModifier
        get() = ancestor<ArendDefClass>()?.accessModifier ?: AccessModifier.PUBLIC
}
