package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.psi.ArendDefModule
import org.arend.psi.ArendStatCmd
import org.arend.psi.ancestors
import org.arend.psi.stubs.ArendDefModuleStub
import org.arend.term.group.ChildGroup
import org.arend.typing.ExpectedTypeVisitor


abstract class ModuleAdapter : ReferableAdapter<ArendDefModuleStub>, ArendDefModule {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefModuleStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParentGroup(): ChildGroup? = parent.ancestors.filterIsInstance<ChildGroup>().firstOrNull()

    override fun getReferable() = this

    override fun getSubgroups(): List<ChildGroup> = where?.statementList?.mapNotNull { it.definition ?: it.defModule as ChildGroup? } ?: emptyList()

    override fun getNamespaceCommands(): List<ArendStatCmd> = where?.statementList?.mapNotNull { it.statCmd } ?: emptyList()

    override fun getParameterType(params: List<Boolean>) = ExpectedTypeVisitor.TooManyArgumentsError(textRepresentation(), 0)

    override fun getInternalReferables(): List<ArendInternalReferable> = emptyList()
}