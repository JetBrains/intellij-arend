package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.arend.ext.error.ErrorReporter
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.ParameterReferable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.stubs.ArendNamedStub
import org.arend.resolving.DataLocatedReferable
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

    override fun getStatements(): List<ArendStatement> = where?.statList ?: emptyList()

    override fun computeConcrete(referableConverter: ReferableConverter, errorReporter: ErrorReporter): Concrete.ResolvableDefinition {
        val def = ConcreteBuilder.convert(referableConverter, this, errorReporter)
        if (def.status == Concrete.Status.HAS_ERRORS) {
            accept(object : PsiRecursiveElementVisitor() {
                override fun visitErrorElement(element: PsiErrorElement) {
                    errorReporter.report(IncompleteExpressionError(SourceInfoErrorData(element)))
                }
            })
        }
        return def
    }

    override fun getParentGroup() = parent?.ancestor<ArendGroup>()

    override fun getReferable(): LocatedReferable = this

    override fun getDynamicSubgroups(): List<ArendGroup> = emptyList()

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

    protected open val parametersExt: List<Abstract.Parameter>
        get() = emptyList()

    override fun getExternalParameters(): List<ParameterReferable> {
        val parent = locatedReferableParent as? DefinitionAdapter<*> ?: return emptyList()
        val tcRef = parent.tcReferable as? TCDefReferable ?: return emptyList()
        val params = parent.parametersExt
        if (params.isEmpty()) return emptyList()
        val result = ArrayList<ParameterReferable>()
        var i = 0
        for (param in params) {
            for (referable in param.referableList) {
                if (referable != null) {
                    result.add(ParameterReferable(tcRef, i, referable.refName))
                }
                i++
            }
        }
        return result
    }

    override fun makeTCReferable(data: SmartPsiElementPointer<PsiLocatedReferable>, parent: LocatedReferable?): TCReferable =
        DataLocatedReferable(data, this, parent)

    abstract fun getPLevelParams(): ArendPLevelParams?

    abstract fun getHLevelParams(): ArendHLevelParams?

    override fun getPLevelParameters(): Abstract.LevelParameters? = getPLevelParams()?.pLevelParamsSeq

    override fun getHLevelParameters(): Abstract.LevelParameters? = getHLevelParams()?.hLevelParamsSeq
}
