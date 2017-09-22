package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.frontend.term.Abstract
import com.jetbrains.jetpad.vclang.frontend.term.AbstractDefinitionVisitor
import org.vclang.VcIcons
import org.vclang.psi.VcClassField
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcDefinition
import org.vclang.psi.stubs.VcDefClassStub
import javax.swing.Icon

abstract class ClassDefinitionAdapter : DefinitionAdapter<VcDefClassStub>, VcDefClass, Abstract.ClassDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefClassStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getDynamicSubgroups(): List<VcDefinition> =
        classStats?.classStatList?.mapNotNull { it.definition } ?: emptyList()

    override fun getFields(): List<VcClassField> =
        classStats?.classStatList?.mapNotNull { it.classField } ?: emptyList()

    override fun getParameters(): List<Abstract.Parameter> {
        TODO("not implemented")
    }

    override fun getSuperClasses(): List<Abstract.Expression> {
        TODO("not implemented")
    }

    override fun getClassFields(): List<Abstract.ClassField> {
        TODO("not implemented")
    }

    override fun getClassFieldImpls(): List<Abstract.ClassFieldImpl> {
        TODO("not implemented")
    }

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitClass(this)

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_DEFINITION
}
