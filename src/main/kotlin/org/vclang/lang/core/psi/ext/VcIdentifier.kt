package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.vclang.lang.core.psi.VcIdentifier
import org.vclang.lang.core.psi.VcInfix
import org.vclang.lang.core.resolve.VcReference
import org.vclang.lang.core.resolve.VcReferenceBase

abstract class VcIdentifierImplMixin(node: ASTNode) : VcNamedElementImpl(node),
                                                      VcIdentifier {
    override val referenceNameElement: PsiElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = object : VcReferenceBase<VcIdentifier>(this) {

        override fun resolve(): VcCompositeElement? = scope.resolve(name)

        override fun getVariants(): Array<Any> = arrayOf()
    }
}

abstract class VcInfixImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                 VcInfix {
    override val referenceNameElement: PsiElement
        get() = this

    override val referenceName: String
        get() = (id ?: binOp)!!.text

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = object : VcReferenceBase<VcInfix>(this) {

        override fun resolve(): VcCompositeElement? = scope.resolve(name)

        override fun getVariants(): Array<Any> = arrayOf()
    }
}
