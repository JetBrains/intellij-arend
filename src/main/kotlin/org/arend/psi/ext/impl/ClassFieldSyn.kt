package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TypedReferable
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.ArendIcons
import org.arend.psi.ArendClassFieldSyn
import org.arend.psi.stubs.ArendClassFieldSynStub
import javax.swing.Icon

abstract class ClassFieldSynAdapter : ReferableAdapter<ArendClassFieldSynStub>, ArendClassFieldSyn {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendClassFieldSynStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.FIELD

    override fun getPrecedence() = calcPrecedence(prec)

    override fun getReferable() = this

    override fun getUnderlyingField() = refIdentifier

    override fun isVisible() = true

    override fun getIcon(flags: Int): Icon = ArendIcons.CLASS_FIELD

    override fun getUnderlyingReference() = (parent as? ClassReferable)?.underlyingReference?.let { classRef -> ExpressionResolveNameVisitor.resolve(unresolvedUnderlyingReference.referent, ClassFieldImplScope(classRef, true)) as? LocatedReferable }

    override fun getUnresolvedUnderlyingReference() = underlyingField

    override fun isExplicitField() = true

    override fun getTypeClassReference() = (underlyingReference as? TypedReferable)?.typeClassReference

    override fun getParameterType(params: List<Boolean>) = (underlyingReference as? TypedReferable)?.getParameterType(params)

    override fun getTypeOf() = (underlyingReference as? TypedReferable)?.typeOf

    override fun isFieldSynonym() = true
}
