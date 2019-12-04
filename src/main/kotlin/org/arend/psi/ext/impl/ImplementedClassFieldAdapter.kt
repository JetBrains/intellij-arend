package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.ArendExpr
import org.arend.psi.ArendImplementedClassField
import org.arend.psi.ArendTypeTele
import org.arend.psi.stubs.ArendImplementedClassFieldStub
import org.arend.term.ClassFieldKind
import org.arend.typing.ReferableExtractVisitor
import javax.swing.Icon


abstract class ImplementedClassFieldAdapter : ReferableAdapter<ArendImplementedClassFieldStub>, ArendImplementedClassField {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendImplementedClassFieldStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.FIELD

    override fun getClassFieldKind() = when {
        fieldKw != null -> ClassFieldKind.FIELD
        propertyKw != null -> ClassFieldKind.PROPERTY
        else -> ClassFieldKind.ANY
    }

    override fun getReferable() = this

    override fun getParameters(): List<ArendTypeTele> = typeTeleList

    override fun getResultType(): ArendExpr? = returnExpr?.let { it.expr ?: it.atomFieldsAccList.firstOrNull() }

    override fun getResultTypeLevel(): ArendExpr? = returnExpr?.atomFieldsAccList?.getOrNull(1)

    override fun getFieldImplementation() = expr

    override fun isVisible() = true

    override fun getIcon(flags: Int): Icon = ArendIcons.CLASS_FIELD

    override fun isExplicitField() = true

    override fun isParameterField() = false

    override fun getTypeClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
    }

    override fun getTypeOf() = org.arend.typing.getTypeOf(parameters, resultType)

    override val psiElementType: PsiElement?
        get() = resultType
}