package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.term.Precedence
import org.vclang.psi.VcFieldDefIdentifier
import org.vclang.psi.VcFieldTele
import org.vclang.psi.stubs.VcClassFieldParamStub
import org.vclang.resolving.VcDefReferenceImpl
import org.vclang.resolving.VcReference
import org.vclang.typing.ReferableExtractVisitor

abstract class FieldDefIdentifierAdapter : ReferableAdapter<VcClassFieldParamStub>, VcFieldDefIdentifier {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassFieldParamStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun textRepresentation(): String = referenceName

    override fun getReference(): VcReference = VcDefReferenceImpl<VcFieldDefIdentifier>(this)

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getReferable() = this

    override fun isVisible() = false

    override fun getTypeClassReference(): ClassReferable? =
        (parent as? VcFieldTele)?.expr?.let { ReferableExtractVisitor(scope).findClassReferable(it) }
}