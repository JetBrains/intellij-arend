package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.elementType
import org.arend.ext.error.ErrorReporter
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.ParameterReferable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.stubs.ArendNamedStub
import org.arend.resolving.DataLocatedReferable
import org.arend.resolving.IntellijTCReferable
import org.arend.resolving.util.ReferableExtractVisitor
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.abs.IncompleteExpressionError
import org.arend.term.concrete.Concrete

abstract class ArendDefinition<StubT> : ReferableBase<StubT>, ArendGroup, Abstract.Definition, PsiConcreteReferable
where StubT : ArendNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope: Scope
        get() = groupScope

    override fun getStatements(): List<ArendStatement> = ArendStat.flatStatements(where?.statList)

    override fun isDynamicContext() = parent is ArendClassStat

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

    override fun getUseParent() = parentGroup?.referable

    override fun withUse() = parent?.hasChildOfType(USE_KW) == true

    override fun getUsedDefinitions(): List<LocatedReferable> = statements.mapNotNull {
        val group = it.group
        if (group is ArendDefinition<*> && group.withUse()) group.referable else null
    }

    protected open val parametersExt: List<Abstract.Parameter>
        get() = emptyList()

    override fun getExternalParameters(): List<ParameterReferable> {
        val parent = locatedReferableParent as? ArendDefinition<*> ?: return emptyList()
        val tcRef = parent.tcReferable as? TCDefReferable ?: return emptyList()
        val params = parent.parametersExt
        if (params.isEmpty()) return emptyList()

        val eliminated = if (parent is Abstract.FunctionDefinition && !parent.withTerm() && !parent.isCowith) {
            val elimList = parent.eliminatedExpressions
            if (elimList.isEmpty()) return emptyList()
            elimList.mapTo(HashSet()) { it.referent.refName }
        } else emptySet()

        val result = ArrayList<ParameterReferable>()
        var i = 0
        for (param in params) {
            val classRef = (param.type as? ArendExpr)?.let { ReferableExtractVisitor().findClassReferable(it) }
            for (referable in param.referableList) {
                if (referable != null && !eliminated.contains(referable.refName)) {
                    result.add(ParameterReferable(tcRef, i, referable, if (classRef == null) null else Concrete.ReferenceExpression(null, classRef)))
                }
                i++
            }
        }
        return result
    }

    override fun makeTCReferable(data: SmartPsiElementPointer<PsiLocatedReferable>, parent: LocatedReferable?): IntellijTCReferable =
        DataLocatedReferable(data, accessModifier, this, parent)

    override fun getPLevelParameters(): ArendLevelParamsSeq? =
        getChild { it.elementType == P_LEVEL_PARAMS_SEQ }

    override fun getHLevelParameters(): ArendLevelParamsSeq? =
        getChild { it.elementType == H_LEVEL_PARAMS_SEQ }

    override val where: ArendWhere?
        get() = childOfType()
}
