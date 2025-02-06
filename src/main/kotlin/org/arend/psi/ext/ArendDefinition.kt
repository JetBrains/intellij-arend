package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.elementType
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.stubs.ArendNamedStub
import org.arend.term.abs.Abstract

abstract class ArendDefinition<StubT> : ReferableBase<StubT>, ArendGroup, Abstract.Definition
where StubT : ArendNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getStatements(): List<ArendStatement> = ArendStat.flatStatements(where?.statList)

    override fun isDynamicContext() = parent is ArendClassStat

    override fun getParentGroup() = parent?.ancestor<ArendGroup>()

    override fun getReferable(): LocatedReferable = this

    override fun getDynamicSubgroups(): List<ArendGroup> = emptyList()

    override fun getInternalReferables(): List<ArendInternalReferable> = emptyList()

    // TODO[server2]: Should be removed
    val enclosingClass: ClassReferable?
        get () {
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

    // TODO[server2]: Should be removed
    val useParent: LocatedReferable?
        get() = parentGroup?.referable

    override fun withUse() = parent?.hasChildOfType(USE_KW) == true

    override fun getUsedDefinitions(): List<LocatedReferable> = statements.mapNotNull {
        val group = it.group
        if (group is ArendDefinition<*> && group.withUse()) group.referable else null
    }

    protected open val parametersExt: List<Abstract.Parameter>
        get() = emptyList()

    override fun getPLevelParameters(): ArendLevelParamsSeq? =
        getChild { it.elementType == P_LEVEL_PARAMS_SEQ }

    override fun getHLevelParameters(): ArendLevelParamsSeq? =
        getChild { it.elementType == H_LEVEL_PARAMS_SEQ }

    override fun getGroupDefinition() = this

    override val where: ArendWhere?
        get() = childOfType()
}
