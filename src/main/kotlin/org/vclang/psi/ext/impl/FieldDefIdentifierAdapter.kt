package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.stubs.VcClassFieldParamStub
import org.vclang.resolving.VcDefReferenceImpl
import org.vclang.resolving.VcReference
import org.vclang.typing.ExpectedTypeVisitor
import org.vclang.typing.ReferableExtractVisitor

abstract class FieldDefIdentifierAdapter : ReferableAdapter<VcClassFieldParamStub>, VcFieldDefIdentifier {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassFieldParamStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.FIELD

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = name

    override fun getName(): String = stub?.name ?: text

    override fun textRepresentation(): String = name

    override fun getReference(): VcReference = VcDefReferenceImpl<VcFieldDefIdentifier>(this)

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getReferable() = this

    override fun isVisible() = false

    override fun getTypeClassReference(): ClassReferable? =
        resultType?.let { ReferableExtractVisitor().findClassReferable(it) }

    override fun getParameterType(params: List<Boolean>) =
        when {
            params[0] -> ExpectedTypeVisitor.getParameterType(resultType, params, name)
            params.size == 1 -> ancestors.filterIsInstance<VcDefClass>().firstOrNull()?.let { ExpectedTypeVisitor.ReferenceImpl(it) }
            else -> ExpectedTypeVisitor.getParameterType(resultType, params.drop(1), name)
        }

    override fun getTypeOf() = resultType

    override fun getParameters(): List<Abstract.Parameter> = emptyList()

    override fun getResultType(): VcExpr? = (parent as? VcFieldTele)?.expr

    override fun getIcon(flags: Int) = VcIcons.CLASS_FIELD

    override val psiElementType: PsiElement?
        get() = (parent as? VcFieldTele)?.expr
}