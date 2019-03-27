package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.scope.Scope
import org.arend.psi.ArendDefModule
import org.arend.psi.ArendStatCmd
import org.arend.psi.ancestors
import org.arend.psi.stubs.ArendDefModuleStub
import org.arend.typing.ExpectedTypeVisitor


abstract class ModuleAdapter : ReferableAdapter<ArendDefModuleStub>, ArendDefModule {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefModuleStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope: Scope
        get() = groupScope

    override fun getParentGroup(): ArendGroup? = parent.ancestors.filterIsInstance<ArendGroup>().firstOrNull()

    override fun getReferable() = this

    override fun getSubgroups(): List<ArendGroup> = where?.statementList?.mapNotNull { it.definition ?: it.defModule } ?: emptyList()

    override fun getDynamicSubgroups(): List<ArendGroup> = emptyList()

    override fun getNamespaceCommands(): List<ArendStatCmd> = where?.statementList?.mapNotNull { it.statCmd } ?: emptyList()

    override fun getParameterType(params: List<Boolean>) = ExpectedTypeVisitor.TooManyArgumentsError(textRepresentation(), 0)

    override fun getInternalReferables(): List<ArendInternalReferable> = emptyList()

    override fun getIcon(flags: Int) = ArendIcons.MODULE_DEFINITION
}