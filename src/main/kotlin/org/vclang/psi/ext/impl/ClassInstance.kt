package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.UnresolvedReference
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.psi.VcDefInstance
import org.vclang.psi.VcNameTele
import org.vclang.psi.stubs.VcDefInstanceStub

abstract class InstanceAdapter : DefinitionAdapter<VcDefInstanceStub>, VcDefInstance, Abstract.InstanceDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefInstanceStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<VcNameTele> = nameTeleList

    override fun getResultClass(): Abstract.Reference? = longName

    override fun getImplementation(): List<Abstract.ClassFieldImpl> = coClauses?.coClauseList ?: emptyList()

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R? = visitor.visitInstance(this)

    override fun getTypeClassReference(): ClassReferable? =
        if (parameters.all { !it.isExplicit }) {
            val ref = resultClass?.referent
            ((ref as? UnresolvedReference)?.resolve(scope) ?: ref) as? ClassReferable
        } else null
}
