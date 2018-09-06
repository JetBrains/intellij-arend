package com.jetbrains.arend.ide.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.arend.ide.ArdIcons
import com.jetbrains.arend.ide.psi.ArdArgumentAppExpr
import com.jetbrains.arend.ide.psi.ArdCoClause
import com.jetbrains.arend.ide.psi.ArdDefInstance
import com.jetbrains.arend.ide.psi.ArdNameTele
import com.jetbrains.arend.ide.psi.stubs.ArdDefInstanceStub
import com.jetbrains.arend.ide.typing.ExpectedTypeVisitor
import com.jetbrains.arend.ide.typing.ReferableExtractVisitor
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import javax.swing.Icon

abstract class InstanceAdapter : DefinitionAdapter<ArdDefInstanceStub>, ArdDefInstance, Abstract.InstanceDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArdDefInstanceStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<ArdNameTele> = nameTeleList

    override fun getResultType(): ArdArgumentAppExpr? = argumentAppExpr

    override fun getClassFieldImpls(): List<ArdCoClause> = coClauses?.coClauseList ?: emptyList()

    override fun getArgumentsExplicitness() = argumentAppExpr?.argumentList?.map { it.isExplicit } ?: emptyList()

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R? = visitor.visitInstance(this)

    override fun getTypeClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
    }

    override fun getParameterType(params: List<Boolean>) = ExpectedTypeVisitor.getParameterType(parameters, resultType, params, textRepresentation())

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(parameters, resultType)

    override fun getClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return ReferableExtractVisitor().findClassReferable(type)
    }

    override fun getIcon(flags: Int): Icon = ArdIcons.CLASS_INSTANCE

    override val psiElementType: PsiElement?
        get() = resultType
}
