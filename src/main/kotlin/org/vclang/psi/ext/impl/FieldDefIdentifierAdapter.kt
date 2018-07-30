package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.VcIcons
import org.vclang.psi.VcExpr
import org.vclang.psi.VcFieldDefIdentifier
import org.vclang.psi.VcFieldTele
import org.vclang.psi.stubs.VcClassFieldParamStub
import org.vclang.resolving.VcDefReferenceImpl
import org.vclang.resolving.VcReference
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
        resultType?.let { ReferableExtractVisitor(scope).findClassReferable(it) }

    override fun getParameters(): List<Abstract.Parameter> = emptyList()

    override fun getResultType(): VcExpr? = (parent as? VcFieldTele)?.expr

    override fun getIcon(flags: Int) = VcIcons.CLASS_FIELD

    override val psiElementType: PsiElement?
        get() = (parent as? VcFieldTele)?.expr
}