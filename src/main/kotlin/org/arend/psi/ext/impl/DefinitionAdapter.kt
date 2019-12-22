package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiRecursiveElementVisitor
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
import org.arend.term.abs.IncompleteExpressionError
import org.arend.term.concrete.Concrete

abstract class DefinitionAdapter<StubT> : ReferableAdapter<StubT>, ArendGroup, Abstract.Definition, PsiConcreteReferable
where StubT : ArendNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope: Scope
        get() = groupScope

    override val statements: List<ArendStatement>
        get() = where?.statementList ?: emptyList()

    override fun computeConcrete(referableConverter: ReferableConverter, errorReporter: ErrorReporter): Concrete.Definition? {
        val def = ConcreteBuilder.convert(referableConverter, this, errorReporter)
        if (def?.status == Concrete.Status.HAS_ERRORS) {
            accept(object : PsiRecursiveElementVisitor() {
                override fun visitErrorElement(element: PsiErrorElement) {
                    errorReporter.report(IncompleteExpressionError(SourceInfoErrorData(element)))
                }
            })
        }
        return def
    }

    override fun getParentGroup() = parent?.ancestor<ArendGroup>()

    override fun getReferable() = this

    override fun getSubgroups(): List<ArendGroup> = where?.statementList?.mapNotNull { it.definition ?: it.defModule } ?: emptyList()

    override fun getDynamicSubgroups(): List<ArendGroup> = emptyList()

    override fun getNamespaceCommands(): List<ArendStatCmd> = where?.statementList?.mapNotNull { it.statCmd } ?: emptyList()

    override fun getInternalReferables(): List<ArendInternalReferable> = emptyList()

    override fun getEnclosingClass(): ClassReferable? {
        var prev: ArendGroup = this
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
