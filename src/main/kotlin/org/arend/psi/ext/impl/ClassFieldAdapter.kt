package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.ArendClassField
import org.arend.psi.ArendClassStat
import org.arend.psi.ArendExpr
import org.arend.psi.ArendTypeTele
import org.arend.psi.stubs.ArendClassFieldStub
import org.arend.term.ClassFieldKind
import org.arend.typing.ReferableExtractVisitor
import javax.swing.Icon

abstract class ClassFieldAdapter : ReferableAdapter<ArendClassFieldStub>, ArendClassField {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendClassFieldStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.FIELD

    override fun getClassFieldKind(): ClassFieldKind {
        val parent = parent as? ArendClassStat ?: return ClassFieldKind.ANY
        return when {
            parent.fieldKw != null -> ClassFieldKind.FIELD
            parent.propertyKw != null -> ClassFieldKind.PROPERTY
            else -> ClassFieldKind.ANY
        }
    }

    override fun getReferable() = this

    override fun getParameters(): List<ArendTypeTele> = typeTeleList

    override fun getResultType(): ArendExpr? = returnExpr?.let { it.expr ?: it.atomFieldsAccList.firstOrNull() }

    override fun getResultTypeLevel(): ArendExpr? = returnExpr?.atomFieldsAccList?.getOrNull(1)

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
