package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.VcIcons
import org.vclang.psi.VcConstructor
import org.vclang.psi.VcDefData
import org.vclang.psi.stubs.VcDefDataStub
import javax.swing.Icon

abstract class DataDefinitionAdapter : DefinitionAdapter<VcDefDataStub>, VcDefData, Abstract.DataDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefDataStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getConstructors(): List<VcConstructor> {
        val body = dataBody ?: return emptyList()
        return (body.dataClauses?.constructorClauseList?.flatMap { it.constructorList } ?: emptyList()) +
               (body.dataConstructors?.constructorList ?: emptyList())
    }

    override fun getParameters(): List<Abstract.Parameter> {
        TODO("not implemented")
    }

    override fun getEliminatedExpressions(): List<Abstract.Expression> {
        TODO("not implemented")
    }

    override fun isTruncated(): Boolean {
        TODO("not implemented")
    }

    override fun getUniverse(): Abstract.Expression? {
        TODO("not implemented")
    }

    override fun getClauses(): List<Abstract.ConstructorClause> {
        TODO("not implemented")
    }

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitData(this)

    override fun getIcon(flags: Int): Icon = VcIcons.DATA_DEFINITION
}
