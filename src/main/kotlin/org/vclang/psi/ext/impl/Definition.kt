package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.ConcreteBuilder
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.term.group.ChildGroup
import com.jetbrains.jetpad.vclang.term.group.Group
import org.vclang.psi.*
import org.vclang.psi.stubs.VcNamedStub
import org.vclang.resolving.VcReferableConverter

abstract class DefinitionAdapter<StubT> : ReferableAdapter<StubT>, ChildGroup, Abstract.Definition
where StubT : VcNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope: Scope
        get() = groupScope

    open fun getWhere(): VcWhere? = null

    override fun computeConcrete(errorReporter: ErrorReporter): Concrete.ReferableDefinition? = ConcreteBuilder.convert(VcReferableConverter(containingFile), this, errorReporter)

    override fun getParentGroup(): ChildGroup? = parent.ancestors.filterIsInstance<ChildGroup>().firstOrNull()

    override fun getReferable() = this

    override fun getSubgroups(): List<VcDefinition> = getWhere()?.statementList?.mapNotNull { it.definition } ?: emptyList()

    override fun getNamespaceCommands(): List<VcStatCmd> = getWhere()?.statementList?.mapNotNull { it.statCmd } ?: emptyList()

    override fun getConstructors(): List<VcConstructor> = emptyList()

    override fun getDynamicSubgroups(): List<Group> = emptyList()

    override fun getFields(): List<Group.InternalReferable> = emptyList()
}
