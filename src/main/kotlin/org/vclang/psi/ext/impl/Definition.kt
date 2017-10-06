package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.ConcreteBuilder
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.term.ChildGroup
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.term.Group
import org.vclang.psi.*
import org.vclang.psi.stubs.VcNamedStub

abstract class DefinitionAdapter<StubT> : ReferableAdapter<StubT>, ChildGroup, Abstract.Definition
where StubT : VcNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    open fun getWhere(): VcWhere? = null

    override fun computeConcrete(errorReporter: ErrorReporter): Concrete.ReferableDefinition? = ConcreteBuilder.convert(this, errorReporter)

    override fun getGroupParent(): ChildGroup? = ancestors.filterIsInstance<ChildGroup>().first()

    override fun getReferable(): GlobalReferable = this

    override fun getSubgroups(): List<VcDefinition> = getWhere()?.statementList?.mapNotNull { it.definition } ?: emptyList()

    override fun getNamespaceCommands(): List<VcStatCmd> = getWhere()?.statementList?.mapNotNull { it.statCmd } ?: emptyList()

    override fun getConstructors(): List<VcConstructor> = emptyList()

    override fun getDynamicSubgroups(): List<Group> = emptyList()

    override fun getFields(): List<GlobalReferable> = emptyList()
}
