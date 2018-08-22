package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.VcIcons
import org.vclang.psi.VcArgumentAppExpr
import org.vclang.psi.VcCoClause
import org.vclang.psi.VcDefInstance
import org.vclang.psi.VcNameTele
import org.vclang.psi.stubs.VcDefInstanceStub
import org.vclang.typing.ExpectedTypeVisitor
import org.vclang.typing.ReferableExtractVisitor
import javax.swing.Icon

abstract class InstanceAdapter : DefinitionAdapter<VcDefInstanceStub>, VcDefInstance, Abstract.InstanceDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefInstanceStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<VcNameTele> = nameTeleList

    override fun getResultType(): VcArgumentAppExpr? = argumentAppExpr

    override fun getClassFieldImpls(): List<VcCoClause> = coClauses?.coClauseList ?: emptyList()

    override fun getNumberOfArguments() = argumentAppExpr?.argumentList?.size ?: 0

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R? = visitor.visitInstance(this)

    override fun getTypeClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
    }

    override fun getParameterType(params: List<Boolean>) = ExpectedTypeVisitor.getParameterType(parameters, resultType, params, textRepresentation())

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(parameters, resultType)

    override fun getClassReference() = typeClassReference

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_INSTANCE

    override val psiElementType: PsiElement?
        get() = resultType
}
