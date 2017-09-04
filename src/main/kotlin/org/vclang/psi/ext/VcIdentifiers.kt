package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcInfixName
import org.vclang.psi.VcPostfixName
import org.vclang.psi.VcPrefixName
import org.vclang.psi.VcRefIdentifier
import org.vclang.resolve.EmptyNamespace
import org.vclang.resolve.EmptyScope
import org.vclang.resolve.Namespace
import org.vclang.resolve.Scope
import org.vclang.resolve.VcReference
import org.vclang.resolve.VcReferenceBase

abstract class VcDefIdentifierImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                         VcDefIdentifier {

    override val referenceNameElement: VcDefIdentifierImplMixin
        get() = this

    override val referenceName: String
        get() = referenceNameElement.text

    override fun getName(): String = referenceName
}

abstract class VcRefIdentifierImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                         VcRefIdentifier {
    override val namespace: Namespace
        get() {
            val resolved = reference.resolve() as? VcCompositeElement
            return resolved?.namespace ?: EmptyNamespace
        }

    override val scope: Scope
        get() = (parent as? VcCompositeElement)?.scope ?: EmptyScope

    override val referenceNameElement: VcRefIdentifierImplMixin
        get() = this

    override val referenceName: String
        get() = referenceNameElement.text

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = VcRefIdentifierReference()

    private inner class VcRefIdentifierReference : VcReferenceBase<VcRefIdentifier>(
        this@VcRefIdentifierImplMixin
    ) {

        override fun resolve(): VcCompositeElement? = scope.resolve(name)

        override fun getVariants(): Array<Any> = scope.symbols.toTypedArray()
    }
}

abstract class VcPrefixImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                  VcPrefixName {
    override val namespace: Namespace
            get() {
                val resolved = reference.resolve() as? VcCompositeElement
                return resolved?.namespace ?: EmptyNamespace
            }

    override val scope: Scope
        get() = (parent as? VcCompositeElement)?.scope ?: EmptyScope

    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() {
            prefix?.let { return it.text }
            prefixInfix?.let { return it.text.removePrefix("`") }
            error("Invalid node")
        }

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = VcPrefixReference()

    private inner class VcPrefixReference : VcReferenceBase<VcPrefixName>(this@VcPrefixImplMixin) {

        override fun resolve(): VcCompositeElement? = scope.resolve(name)

        override fun getVariants(): Array<Any> = scope.symbols.toTypedArray()
    }
}

abstract class VcInfixImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                 VcInfixName {
    override val namespace: Namespace
        get() {
            val resolved = reference.resolve() as? VcCompositeElement
            return resolved?.namespace ?: EmptyNamespace
        }

    override val scope: Scope
        get() = (parent as? VcCompositeElement)?.scope ?: EmptyScope

    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() {
            infix?.let { return it.text }
            infixPrefix?.let { return it.text.removePrefix("`") }
            error("Invalid node")
        }

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = VcInfixReference()

    private inner class VcInfixReference : VcReferenceBase<VcInfixName>(this@VcInfixImplMixin) {

        override fun resolve(): VcCompositeElement? = scope.resolve(name)

        override fun getVariants(): Array<Any> = scope.symbols.toTypedArray()
    }
}

abstract class VcPostfixImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcPostfixName {
    override val namespace: Namespace
        get() {
            val resolved = reference.resolve() as? VcCompositeElement
            return resolved?.namespace ?: EmptyNamespace
        }

    override val scope: Scope
        get() = (parent as? VcCompositeElement)?.scope ?: EmptyScope

    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() {
            postfixInfix?.let { return it.text.removeSuffix("`") }
            postfixPrefix?.let { return it.text.removeSuffix("`") }
            error("Invalid node")
        }

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = VcPostfixReference()

    private inner class VcPostfixReference
        : VcReferenceBase<VcPostfixName>(this@VcPostfixImplMixin) {

        override fun resolve(): VcCompositeElement? = scope.resolve(name)

        override fun getVariants(): Array<Any> = scope.symbols.toTypedArray()
    }
}
