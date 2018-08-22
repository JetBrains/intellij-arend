package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.stubs.VcClassFieldStub
import org.vclang.typing.ExpectedTypeVisitor
import org.vclang.typing.ReferableExtractVisitor
import javax.swing.Icon

abstract class ClassFieldAdapter : ReferableAdapter<VcClassFieldStub>, VcClassField {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassFieldStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.FIELD

    override fun getPrecedence() = calcPrecedence(prec)

    override fun getReferable() = this

    override fun getParameters(): List<VcTypeTele> = typeTeleList

    override fun getResultType(): VcExpr? = expr

    override fun isVisible() = true

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_FIELD

    override fun isExplicitField() = true

    override fun getTypeClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
    }

    override fun getParameterType(params: List<Boolean>) =
        when {
            params[0] -> ExpectedTypeVisitor.getParameterType(parameters, resultType, params, textRepresentation())
            params.size == 1 -> ancestors.filterIsInstance<VcDefClass>().firstOrNull()?.let { ExpectedTypeVisitor.ReferenceImpl(it) }
            else -> ExpectedTypeVisitor.getParameterType(parameters, resultType, params.drop(1), textRepresentation())
        }

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(parameters, resultType)

    override val psiElementType: PsiElement?
        get() = resultType
}
