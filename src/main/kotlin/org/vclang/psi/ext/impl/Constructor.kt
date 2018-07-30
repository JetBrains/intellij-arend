package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.stubs.VcConstructorStub
import javax.swing.Icon

abstract class ConstructorAdapter : ReferableAdapter<VcConstructorStub>, VcConstructor {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcConstructorStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.CONSTRUCTOR

    override fun getData() = this

    override fun getPatterns(): List<Abstract.Pattern> = emptyList()

    override fun getConstructors(): List<VcConstructor> = listOf(this)

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun getReferable(): LocatedReferable = this

    override fun getParameters(): List<VcTypeTele> = typeTeleList

    override fun getEliminatedExpressions(): List<VcRefIdentifier> = elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<VcClause> = clauseList

    override fun isVisible(): Boolean = true

    override fun getIcon(flags: Int): Icon = VcIcons.CONSTRUCTOR

    override val psiElementType: PsiElement?
        get() = ancestors.filterIsInstance<VcDefData>().firstOrNull()?.defIdentifier
}
