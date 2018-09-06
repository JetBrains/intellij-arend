package com.jetbrains.arend.ide.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.arend.ide.ArdIcons
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.psi.stubs.ArdDefDataStub
import com.jetbrains.arend.ide.typing.ExpectedTypeVisitor
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import javax.swing.Icon

abstract class DataDefinitionAdapter : DefinitionAdapter<ArdDefDataStub>, ArdDefData, Abstract.DataDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArdDefDataStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getConstructors(): List<ArdConstructor> {
        val body = dataBody ?: return emptyList()
        return body.constructorClauseList.flatMap { it.constructorList } + body.constructorList
    }

    override fun getParameters(): List<ArdTypeTele> = typeTeleList

    override fun getEliminatedExpressions(): List<ArdRefIdentifier>? = dataBody?.elim?.refIdentifierList

    override fun isTruncated(): Boolean = truncatedKw != null

    override fun getUniverse(): ArdExpr? = universeExpr

    override fun getClauses(): List<Abstract.ConstructorClause> {
        val body = dataBody ?: return emptyList()
        return body.constructorClauseList + body.constructorList
    }

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun getCoercingFunctions(): List<LocatedReferable> = where?.statementList?.mapNotNull {
        val def = it.definition
        if (def is ArdDefFunction && def.coerceKw != null) def else null
    } ?: emptyList()

    override fun getParameterType(params: List<Boolean>) =
            ExpectedTypeVisitor.getParameterType(parameters, ExpectedTypeVisitor.Universe, params, textRepresentation())

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(parameters, ExpectedTypeVisitor.Universe)

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitData(this)

    override fun getIcon(flags: Int): Icon = ArdIcons.DATA_DEFINITION

    override val psiElementType: PsiElement?
        get() = universe
}
