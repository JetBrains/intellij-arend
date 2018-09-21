package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.arend.error.ErrorReporter
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.psi.stubs.ArendNamedStub
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.group.ChildGroup
import org.arend.term.group.Group

abstract class DefinitionAdapter<StubT> : ReferableAdapter<StubT>, ChildGroup, Abstract.Definition, PsiConcreteReferable
where StubT : ArendNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope: Scope
        get() = groupScope

    open fun getWhere(): ArendWhere? = null

    override fun computeConcrete(referableConverter: ReferableConverter, errorReporter: ErrorReporter): Concrete.Definition? =
        ConcreteBuilder.convert(referableConverter, this, errorReporter)

    override fun getParentGroup(): ChildGroup? = parent.ancestors.filterIsInstance<ChildGroup>().firstOrNull()

    override fun getReferable() = this

    override fun getSubgroups(): List<ChildGroup> = getWhere()?.statementList?.mapNotNull { it.definition ?: it.defModule as ChildGroup? } ?: emptyList()

    override fun getNamespaceCommands(): List<ArendStatCmd> = getWhere()?.statementList?.mapNotNull { it.statCmd } ?: emptyList()

    override fun getConstructors(): List<ArendConstructor> = emptyList()

    override fun getDynamicSubgroups(): List<ChildGroup> = emptyList()

    override fun getFields(): List<Group.InternalReferable> = emptyList()

    override fun getEnclosingClass(): ClassReferable? {
        var parent = parentGroup
        while (parent != null && parent !is ArendFile) {
            val ref = parent.referable
            if (ref is ClassReferable) {
                for (subgroup in parent.dynamicSubgroups) {
                    if (subgroup.referable == referable) {
                        return ref
                    }
                }
            }
            parent = parent.parentGroup
        }
        return null
    }
}
