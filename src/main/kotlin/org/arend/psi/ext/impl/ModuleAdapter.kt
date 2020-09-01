package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.TCReferable
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiStubbedReferableImpl
import org.arend.psi.stubs.ArendDefModuleStub


abstract class ModuleAdapter : PsiStubbedReferableImpl<ArendDefModuleStub>, ArendDefModule {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefModuleStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope: Scope
        get() = groupScope

    override val statements: List<ArendStatement>
        get() = where?.statementList ?: emptyList()

    override fun getParentGroup() = parent.ancestor<ArendGroup>()

    override fun getReferable() = this

    override fun getSubgroups(): List<ArendGroup> = where?.statementList?.mapNotNull { it.definition ?: it.defModule } ?: emptyList()

    override fun getDynamicSubgroups(): List<ArendGroup> = emptyList()

    override fun getNamespaceCommands(): List<ArendStatCmd> = where?.statementList?.mapNotNull { it.statCmd } ?: emptyList()

    override fun getInternalReferables(): List<ArendInternalReferable> = emptyList()

    override fun getKind() = GlobalReferable.Kind.OTHER

    override fun getIcon(flags: Int) = ArendIcons.MODULE_DEFINITION

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getTypecheckable() = this

    override fun getLocation() = if (isValid) (containingFile as? ArendFile)?.moduleLocation else null

    override fun getLocatedReferableParent() = parent?.ancestor<PsiLocatedReferable>()

    override val tcReferable: TCReferable?
        get() = null

    override fun dropTypechecked() {}

    override fun checkTCReferable() {}
}