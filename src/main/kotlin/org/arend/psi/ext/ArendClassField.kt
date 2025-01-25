package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.elementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.stubs.ArendClassFieldStub
import org.arend.resolving.FieldDataLocatedReferable
import org.arend.ext.concrete.definition.ClassFieldKind
import org.arend.naming.reference.FieldReferable
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract
import org.arend.resolving.util.getTypeOf
import org.arend.term.group.AccessModifier
import javax.swing.Icon

class ArendClassField : ReferableBase<ArendClassFieldStub>, ArendInternalReferable, FieldReferable, Abstract.ClassField, StubBasedPsiElement<ArendClassFieldStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendClassFieldStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val returnExpr: ArendReturnExpr?
        get() = childOfType()

    override fun getKind() = GlobalReferable.Kind.FIELD

    override fun getClassFieldKind(): ClassFieldKind =
        when ((parent as? ArendClassStat)?.firstRelevantChild.elementType) {
            FIELD_KW -> ClassFieldKind.FIELD
            PROPERTY_KW -> ClassFieldKind.PROPERTY
            else -> ClassFieldKind.ANY
        }

    override fun getReferable() = this

    override fun getParameters(): List<ArendTypeTele> = getChildrenOfType()

    override fun getResultType(): ArendExpr? = returnExpr?.type

    override fun getResultTypeLevel(): ArendExpr? = returnExpr?.typeLevel

    override fun isVisible() = true

    override fun getIcon(flags: Int): Icon = ArendIcons.CLASS_FIELD

    override fun isExplicitField() = true

    override fun isParameterField() = false

    override fun isClassifying() = hasChildOfType(CLASSIFYING_KW)

    override fun isCoerce() = hasChildOfType(COERCE_KW)

    override fun getAccessModifier(): AccessModifier = (childOfType<ArendAccessMod>()?.accessModifier ?: AccessModifier.PUBLIC).max(classAccessModifier)

    override val typeOf: Abstract.Expression?
        get() = getTypeOf(parameters, resultType)

    override val psiElementType: PsiElement?
        get() = resultType

    private val classAccessModifier: AccessModifier
        get() = ancestor<ArendDefClass>()?.accessModifier ?: AccessModifier.PUBLIC

    override fun makeTCReferable(data: SmartPsiElementPointer<PsiLocatedReferable>, parent: LocatedReferable?) =
        FieldDataLocatedReferable(data, accessModifier, this, parent)
}
