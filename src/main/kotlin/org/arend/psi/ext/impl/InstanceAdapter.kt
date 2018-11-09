package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.ClassReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefInstanceStub
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.typing.ExpectedTypeVisitor
import org.arend.typing.ReferableExtractVisitor
import javax.swing.Icon

abstract class InstanceAdapter : DefinitionAdapter<ArendDefInstanceStub>, ArendDefInstance, Abstract.InstanceDefinition, ClassReferenceHolder {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefInstanceStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getResultType(): ArendArgumentAppExpr? = argumentAppExpr

    override fun getClassFieldImpls(): List<ArendCoClause> = coClauses?.coClauseList ?: emptyList()

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R? = visitor.visitInstance(this)

    override fun getTypeClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
    }

    override fun getParameterType(params: List<Boolean>) = ExpectedTypeVisitor.getParameterType(parameters, resultType, params, textRepresentation())

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(parameters, resultType)

    override fun getClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return ReferableExtractVisitor().findReferable(type) as? ClassReferable
    }

    override fun getClassReferenceData(): ClassReferenceData? {
        val type = resultType ?: return null
        val visitor = ReferableExtractVisitor(true)
        val classRef = visitor.findReferable(type) as? ClassReferable ?: return null
        return ClassReferenceData(classRef, visitor.argumentsExplicitness, visitor.implementedFields)
    }

    override fun getIcon(flags: Int): Icon = ArendIcons.CLASS_INSTANCE

    override val psiElementType: PsiElement?
        get() = resultType
}
