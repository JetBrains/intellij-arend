package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefModuleStub


class ArendDefModule : ReferableBase<ArendDefModuleStub>, ArendGroup, StubBasedPsiElement<ArendDefModuleStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefModuleStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val where: ArendWhere?
        get() = childOfType()

    override fun getStatements(): List<ArendStat> = ArendStat.flatStatements(where?.statList)

    override fun getParentGroup() = parent.ancestor<ArendGroup>()

    override fun getReferable() = this

    override fun isDynamicContext() = parent is ArendClassStat

    override fun getDynamicSubgroups(): List<ArendGroup> = emptyList()

    override fun getInternalReferables(): List<ArendInternalReferable> = emptyList()

    override fun getKind() = GlobalReferable.Kind.OTHER

    override val prec: ArendPrec?
        get() = null

    override val alias: ArendAlias?
        get() = null

    override fun getIcon(flags: Int) = ArendIcons.MODULE_DEFINITION

    override fun getGroupDefinition() = null
}