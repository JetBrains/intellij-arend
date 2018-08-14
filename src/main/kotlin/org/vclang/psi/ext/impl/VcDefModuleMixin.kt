package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.group.ChildGroup
import com.jetbrains.jetpad.vclang.term.group.Group
import org.vclang.psi.VcConstructor
import org.vclang.psi.VcDefModule
import org.vclang.psi.VcStatCmd
import org.vclang.psi.ancestors
import org.vclang.psi.stubs.VcDefModuleStub
import org.vclang.typing.ExpectedTypeVisitor


abstract class VcDefModuleMixin : ReferableAdapter<VcDefModuleStub>, VcDefModule {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefModuleStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParentGroup(): ChildGroup? = parent.ancestors.filterIsInstance<ChildGroup>().firstOrNull()

    override fun getReferable() = this

    override fun getSubgroups(): List<ChildGroup> = where?.statementList?.mapNotNull { it.definition ?: it.defModule as ChildGroup? } ?: emptyList()

    override fun getNamespaceCommands(): List<VcStatCmd> = where?.statementList?.mapNotNull { it.statCmd } ?: emptyList()

    override fun getConstructors(): List<VcConstructor> = emptyList()

    override fun getDynamicSubgroups(): List<Group> = emptyList()

    override fun getFields(): List<Group.InternalReferable> = emptyList()

    override fun getParameterType(index: Int) = ExpectedTypeVisitor.TooManyArgumentsError(textRepresentation(), 0)
}