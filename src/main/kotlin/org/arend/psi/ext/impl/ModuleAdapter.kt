package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCReferable
import org.arend.naming.scope.Scope
import org.arend.naming.scope.Scopes
import org.arend.psi.*
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.stubs.ArendDefModuleStub
import org.arend.resolving.DataLocatedReferable


abstract class ModuleAdapter : ReferableAdapter<ArendDefModuleStub>, ArendDefModule {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefModuleStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope: Scope
        get() = groupScope

    override val scopes: Scopes
        get() = groupScopes

    override fun getStatements(): List<ArendStatement> = where?.statList ?: emptyList()

    override fun getParentGroup() = parent.ancestor<ArendGroup>()

    override fun getReferable() = this

    override fun getDynamicSubgroups(): List<ArendGroup> = emptyList()

    override fun getInternalReferables(): List<ArendInternalReferable> = emptyList()

    override fun getKind() = GlobalReferable.Kind.OTHER

    override fun getPrec(): ArendPrec? = null

    override fun getAlias(): ArendAlias? = null

    override fun makeTCReferable(data: SmartPsiElementPointer<PsiLocatedReferable>, parent: LocatedReferable?): TCReferable =
        DataLocatedReferable(data, this, parent)

    override fun getIcon(flags: Int) = ArendIcons.MODULE_DEFINITION

    override fun getTypecheckable() = this
}