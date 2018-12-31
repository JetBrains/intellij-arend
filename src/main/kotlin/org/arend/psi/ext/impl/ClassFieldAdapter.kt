package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendClassFieldStub
import org.arend.term.ClassFieldKind
import org.arend.term.abs.Abstract
import org.arend.typing.ExpectedTypeVisitor
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

    override fun getPrecedence() = calcPrecedence(prec)

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

    override fun getParameterType(params: List<Boolean>) =
        when {
            params[0] -> ExpectedTypeVisitor.getParameterType(parameters, resultType, params, textRepresentation())
            params.size == 1 -> ancestors.filterIsInstance<ArendDefClass>().firstOrNull()?.let { ExpectedTypeVisitor.ReferenceImpl(it) }
            else -> ExpectedTypeVisitor.getParameterType(parameters, resultType, params.drop(1), textRepresentation())
        }

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(parameters, resultType)

    override val psiElementType: PsiElement?
        get() = resultType
}
