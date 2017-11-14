package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.stubs.VcDefFunctionStub
import javax.swing.Icon

abstract class FunctionDefinitionAdapter : DefinitionAdapter<VcDefFunctionStub>, VcDefFunction, Abstract.FunctionDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<VcNameTele> = nameTeleList

    override fun getResultType(): VcExpr? = expr

    override fun getTerm(): VcExpr? = functionBody?.expr

    override fun getEliminatedExpressions(): List<VcRefIdentifier> = functionBody?.elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<VcClause> = functionBody?.functionClauses?.clauseList ?: emptyList()

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitFunction(this)

    override fun getIcon(flags: Int): Icon = VcIcons.FUNCTION_DEFINITION
}
