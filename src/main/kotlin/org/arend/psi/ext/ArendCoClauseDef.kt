package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.stubs.ArendCoClauseDefStub
import org.arend.ext.concrete.definition.FunctionKind
import org.arend.naming.reference.*
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.LexicalScope
import org.arend.naming.scope.local.TelescopeScope
import org.arend.term.abs.Abstract
import org.arend.resolving.util.ReferableExtractVisitor
import org.arend.term.group.AccessModifier
import javax.swing.Icon

class ArendCoClauseDef : ArendFunctionDefinition<ArendCoClauseDefStub>, Abstract.FunctionDefinition, TCDefinition, StubBasedPsiElement<ArendCoClauseDefStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendCoClauseDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val parentCoClause: ArendCoClause?
        get() = parent as? ArendCoClause

    override val scope: Scope
        get() = if (isDefault) {
            val parentClass = ancestor<ArendDefClass>()
            if (parentClass == null) {
                getArendScope(this)
            } else {
                val parentParent = parentClass.parent.ancestor<ArendSourceNode>()?.topmostEquivalentSourceNode ?: parentClass.containingFile as? ArendFile
                LexicalScope.insideOf(parentClass, parentParent?.scope ?: EmptyScope.INSTANCE, true)
            }
        } else {
            val parentFunction = parent?.ancestor<ArendFunctionDefinition<*>>()
            val parentScope = parentFunction?.scope
            if (parentScope != null) TelescopeScope.make(parentScope, parentFunction.parameters) else getArendScope(this)
        }

    override fun getNameIdentifier() = parentCoClause?.defIdentifier ?: parentCoClause?.longName?.refIdentifierList?.lastOrNull()

    override fun getName() = stub?.name ?: parentCoClause?.defIdentifier?.id?.text ?: parentCoClause?.longName?.refIdentifierList?.lastOrNull()?.referenceName

    private val isDefault: Boolean
        get() = parentCoClause?.parent is ArendClassStat

    override fun getAccessModifier() = if (isDefault) AccessModifier.PROTECTED else AccessModifier.PUBLIC

    override val prec: ArendPrec?
        get() {
            val coClause = parentCoClause ?: return null
            coClause.prec?.let { return it }
            val classRef = (coClause.parent?.parent as? ClassReferenceHolder)?.classReference ?: return null
            return (Scope.resolveName(ClassFieldImplScope(classRef, false), coClause.longName.refIdentifierList.map { it.refName }) as? ReferableBase<*>)?.prec
        }

    override val defIdentifier: ArendDefIdentifier?
        get() = parentCoClause?.defIdentifier

    override val where: ArendWhere?
        get() = null

    override fun getClassReference(): ClassReferable? = resultType?.let { ReferableExtractVisitor().findClassReferable(it) }

    override fun getFunctionKind() = if (isDefault) FunctionKind.CLASS_COCLAUSE else FunctionKind.FUNC_COCLAUSE

    override fun getImplementedField(): Abstract.Reference? = parentCoClause?.longName?.refIdentifierList?.lastOrNull()

    override fun getKind() = GlobalReferable.Kind.COCLAUSE_FUNCTION

    override fun getIcon(flags: Int): Icon = ArendIcons.COCLAUSE_DEFINITION

    override fun getExternalParameters(): List<ParameterReferable> = emptyList()

    override fun findParametersElement(): PsiElement? = firstChild
}