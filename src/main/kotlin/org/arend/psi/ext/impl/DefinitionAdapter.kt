package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.arend.error.ErrorReporter
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.scope.Scope
import org.arend.psi.ArendFile
import org.arend.psi.ArendStatCmd
import org.arend.psi.ancestors
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.psi.stubs.ArendNamedStub
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.group.ChildGroup

abstract class DefinitionAdapter<StubT> : ReferableAdapter<StubT>, ArendGroup, Abstract.Definition, PsiConcreteReferable
where StubT : ArendNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope: Scope
        get() = groupScope

    override fun computeConcrete(referableConverter: ReferableConverter, errorReporter: ErrorReporter): Concrete.Definition? =
        ConcreteBuilder.convert(referableConverter, this, errorReporter)

    override fun getParentGroup(): ChildGroup? = parent.ancestors.filterIsInstance<ChildGroup>().firstOrNull()

    override fun getReferable() = this

    override fun getSubgroups(): List<ChildGroup> = where?.statementList?.mapNotNull { it.definition ?: it.defModule as ChildGroup? } ?: emptyList()

    override fun getNamespaceCommands(): List<ArendStatCmd> = where?.statementList?.mapNotNull { it.statCmd } ?: emptyList()

    override fun getEnclosingClass(): ClassReferable? {
        var prev: ChildGroup = this
        var parent = parentGroup
        while (parent != null && parent !is ArendFile) {
            val ref = parent.referable
            if (ref is ClassReferable) {
                for (subgroup in parent.dynamicSubgroups) {
                    if (subgroup.referable == prev) {
                        return ref
                    }
                }
            }
            prev = parent
            parent = parent.parentGroup
        }
        return null
    }
}
