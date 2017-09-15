package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import org.vclang.psi.*
import org.vclang.resolving.*

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
    override val scope: Scope
        get() = (parent as? VcCompositeElement)?.scope ?: EmptyScope.INSTANCE

    override val referenceNameElement: VcRefIdentifierImplMixin
        get() = this

    override val referenceName: String
        get() = referenceNameElement.text

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = VcReferenceImpl<VcRefIdentifier>(this)
}

abstract class VcPrefixImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                  VcPrefixName {
    override val scope: Scope
        get() = (parent as? VcCompositeElement)?.scope ?: EmptyScope.INSTANCE

    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() {
            prefix?.let { return it.text }
            prefixInfix?.let { return it.text.removePrefix("`") }
            error("Invalid node")
        }

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = VcReferenceImpl<VcPrefixName>(this)
}

abstract class VcInfixImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                 VcInfixName {
    override val scope: Scope
        get() = (parent as? VcCompositeElement)?.scope ?: EmptyScope.INSTANCE

    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() {
            infix?.let { return it.text }
            infixPrefix?.let { return it.text.removePrefix("`") }
            error("Invalid node")
        }

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = VcReferenceImpl<VcInfixName>(this)
}

abstract class VcPostfixImplMixin(node: ASTNode) : VcCompositeElementImpl(node),
                                                   VcPostfixName {
    override val scope: Scope
        get() = (parent as? VcCompositeElement)?.scope ?: EmptyScope.INSTANCE

    override val referenceNameElement: VcCompositeElement
        get() = this

    override val referenceName: String
        get() {
            postfixInfix?.let { return it.text.removeSuffix("`") }
            postfixPrefix?.let { return it.text.removeSuffix("`") }
            error("Invalid node")
        }

    override fun getName(): String = referenceName

    override fun getReference(): VcReference = VcReferenceImpl<VcPostfixName>(this)
}
