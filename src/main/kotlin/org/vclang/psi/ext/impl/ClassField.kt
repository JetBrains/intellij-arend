package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import org.vclang.VcIcons
import org.vclang.psi.VcClassField
import org.vclang.psi.VcExpr
import org.vclang.psi.VcTypeTele
import org.vclang.psi.stubs.VcClassFieldStub
import org.vclang.typing.ReferableExtractVisitor
import javax.swing.Icon

abstract class ClassFieldAdapter : ReferableAdapter<VcClassFieldStub>, VcClassField {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassFieldStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.FIELD

    override fun getPrecedence() = calcPrecedence(prec)

    override fun getReferable() = this

    override fun getParameters(): List<VcTypeTele> = typeTeleList

    override fun getResultType(): VcExpr? = expr

    override fun isVisible() = true

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_FIELD

    override fun getTypeClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor(scope).findClassReferable(type) else null
    }

    override val psiElementType: PsiElement?
        get() = resultType
}
