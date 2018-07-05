package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.VcIcons
import org.vclang.psi.VcDefInstance
import org.vclang.psi.VcNameTele
import org.vclang.psi.stubs.VcDefInstanceStub
import org.vclang.resolving.PsiPartialConcreteProvider
import javax.swing.Icon

abstract class InstanceAdapter : DefinitionAdapter<VcDefInstanceStub>, VcDefInstance, Abstract.InstanceDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefInstanceStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<VcNameTele> = nameTeleList

    override fun getResultType(): Abstract.Expression? = argumentAppExpr

    override fun getImplementation(): List<Abstract.ClassFieldImpl> = coClauses?.coClauseList ?: emptyList()

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R? = visitor.visitInstance(this)

    override fun getTypeClassReference(): ClassReferable? =
        if (parameters.all { !it.isExplicit }) ExpressionResolveNameVisitor.resolve(PsiPartialConcreteProvider.getInstanceReference(this)?.referent, scope) as? ClassReferable else null

    override fun getClassReference() = typeClassReference

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_INSTANCE
}
