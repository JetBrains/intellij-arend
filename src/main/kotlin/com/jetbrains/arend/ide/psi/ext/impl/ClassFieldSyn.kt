package com.jetbrains.arend.ide.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.arend.ide.ArdIcons
import com.jetbrains.arend.ide.psi.ArdClassFieldSyn
import com.jetbrains.arend.ide.psi.stubs.ArdClassFieldSynStub
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.TypedReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.ClassFieldImplScope
import javax.swing.Icon

abstract class ClassFieldSynAdapter : ReferableAdapter<ArdClassFieldSynStub>, ArdClassFieldSyn {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArdClassFieldSynStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.FIELD

    override fun getPrecedence() = calcPrecedence(prec)

    override fun getReferable() = this

    override fun getUnderlyingField() = refIdentifier

    override fun isVisible() = true

    override fun getIcon(flags: Int): Icon = ArdIcons.CLASS_FIELD

    override fun getUnderlyingReference() = (parent as? ClassReferable)?.underlyingReference?.let { classRef -> ExpressionResolveNameVisitor.resolve(unresolvedUnderlyingReference.referent, ClassFieldImplScope(classRef, true)) as? LocatedReferable }

    override fun getUnresolvedUnderlyingReference() = underlyingField

    override fun isExplicitField() = true

    override fun getTypeClassReference() = (underlyingReference as? TypedReferable)?.typeClassReference

    override fun getParameterType(params: List<Boolean>) = (underlyingReference as? TypedReferable)?.getParameterType(params)

    override fun getTypeOf() = (underlyingReference as? TypedReferable)?.typeOf

    override fun isFieldSynonym() = true
}
