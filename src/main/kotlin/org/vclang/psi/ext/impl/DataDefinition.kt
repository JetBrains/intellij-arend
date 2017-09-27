package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.stubs.VcDefDataStub
import javax.swing.Icon

abstract class DataDefinitionAdapter : DefinitionAdapter<VcDefDataStub>, VcDefData, Abstract.DataDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefDataStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getConstructors(): List<VcConstructor> {
        val body = dataBody ?: return emptyList()
        return body.constructorClauseList.flatMap { it.constructorList } + body.constructorList
    }

    override fun getParameters(): List<VcTele> = teleList

    override fun getEliminatedExpressions(): List<Abstract.Expression>? = dataBody?.elim?.atomFieldsAccList

    override fun isTruncated(): Boolean = truncatedKw != null

    override fun getUniverse(): VcExpr? = expr

    override fun getClauses(): List<VcConstructorClause> = dataBody?.constructorClauseList ?: emptyList()

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitData(this)

    override fun getIcon(flags: Int): Icon = VcIcons.DATA_DEFINITION
}
