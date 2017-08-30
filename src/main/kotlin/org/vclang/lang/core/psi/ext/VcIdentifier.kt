package org.vclang.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.vclang.lang.core.psi.VcIdentifier
import org.vclang.lang.core.psi.VcInfixName
import org.vclang.lang.core.psi.VcPostfixName
import org.vclang.lang.core.psi.VcPrefixName
import org.vclang.lang.core.resolve.VcReference
import org.vclang.lang.core.resolve.VcReferenceBase

abstract class VcIdentifierImplMixin(node: ASTNode) : VcNamedElementImpl(node),
                                                      VcIdentifier {
    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getNameIdentifier(): VcCompositeElement = referenceNameElement

    override fun getName(): String = referenceName

    override fun getNavigationElement(): PsiElement = this

    override fun getTextOffset(): Int = node.startOffset

    override fun getReference(): VcReference = object : VcReferenceBase<VcIdentifier>(
            this@VcIdentifierImplMixin
    ) {

        override fun resolve(): VcCompositeElement? = scope.resolve(name)

        override fun getVariants(): Array<Any> = arrayOf()
    }
}

abstract class VcPrefixImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                  VcPrefixName {
    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() {
            prefix?.let { return it.text }
            prefixInfix?.let { return it.text.drop(1) }
            error("Invalid node")
        }

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = object : VcReferenceBase<VcPrefixName>(
            this@VcPrefixImplMixin
    ) {

        override fun resolve(): VcCompositeElement? = scope.resolve(name)

        override fun getVariants(): Array<Any> = scope.symbols.toTypedArray()
    }
}

abstract class VcInfixImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                 VcInfixName {
    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() {
            infix?.let { return it.text }
            infixPrefix?.let { return it.text.drop(1) }
            error("Invalid node")
        }

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = object : VcReferenceBase<VcInfixName>(
            this@VcInfixImplMixin
    ) {

        override fun resolve(): VcCompositeElement? = scope.resolve(name)

        override fun getVariants(): Array<Any> = scope.symbols.toTypedArray()
    }
}

abstract class VcPostfixImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcPostfixName {
    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() {
            postfixInfix?.let { return it.text.dropLast(1) }
            postfixPrefix?.let { return it.text.dropLast(1) }
            error("Invalid node")
        }

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = object : VcReferenceBase<VcPostfixName>(
            this@VcPostfixImplMixin
    ) {

        override fun resolve(): VcCompositeElement? = scope.resolve(name)

        override fun getVariants(): Array<Any> = scope.symbols.toTypedArray()
    }
}
