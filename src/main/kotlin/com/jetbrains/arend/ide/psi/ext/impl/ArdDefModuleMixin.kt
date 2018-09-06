package com.jetbrains.arend.ide.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.arend.ide.psi.ArdConstructor
import com.jetbrains.arend.ide.psi.ArdDefModule
import com.jetbrains.arend.ide.psi.ArdStatCmd
import com.jetbrains.arend.ide.psi.ancestors
import com.jetbrains.arend.ide.psi.stubs.ArdDefModuleStub
import com.jetbrains.arend.ide.typing.ExpectedTypeVisitor
import com.jetbrains.jetpad.vclang.term.group.ChildGroup
import com.jetbrains.jetpad.vclang.term.group.Group


abstract class ArdDefModuleMixin : ReferableAdapter<ArdDefModuleStub>, ArdDefModule {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArdDefModuleStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParentGroup(): ChildGroup? = parent.ancestors.filterIsInstance<ChildGroup>().firstOrNull()

    override fun getReferable() = this

    override fun getSubgroups(): List<ChildGroup> = where?.statementList?.mapNotNull {
        it.definition ?: it.defModule as ChildGroup?
    } ?: emptyList()

    override fun getNamespaceCommands(): List<ArdStatCmd> = where?.statementList?.mapNotNull { it.statCmd }
            ?: emptyList()

    override fun getConstructors(): List<ArdConstructor> = emptyList()

    override fun getDynamicSubgroups(): List<Group> = emptyList()

    override fun getFields(): List<Group.InternalReferable> = emptyList()

    override fun getParameterType(params: List<Boolean>) = ExpectedTypeVisitor.TooManyArgumentsError(textRepresentation(), 0)
}