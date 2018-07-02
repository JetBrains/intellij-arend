package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.*
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.ClassFieldImplScope
import org.vclang.VcIcons
import org.vclang.psi.VcClassFieldSyn
import org.vclang.psi.stubs.VcClassFieldSynStub
import javax.swing.Icon

abstract class ClassFieldSynAdapter : ReferableAdapter<VcClassFieldSynStub>, VcClassFieldSyn {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassFieldSynStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getPrecedence() = calcPrecedence(prec)

    override fun getReferable() = this

    override fun getUnderlyingField() = refIdentifier

    override fun isVisible() = true

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_FIELD

    override fun getUnderlyingReference() = (parent as? ClassReferable)?.underlyingReference?.let { classRef -> ExpressionResolveNameVisitor.resolve(unresolvedUnderlyingReference.referent, ClassFieldImplScope(classRef, true)) as? LocatedReferable }

    override fun getUnresolvedUnderlyingReference() = underlyingField

    override fun getTypeClassReference() = (underlyingReference as? TypedReferable)?.typeClassReference

    override fun isFieldSynonym() = true
}
