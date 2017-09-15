package org.vclang.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import org.vclang.resolving.*

interface VcCompositeElement : PsiElement {
    val scope: Scope
        get() = (parent as? VcCompositeElement)?.scope ?: EmptyScope.INSTANCE // TODO[abstract]

    override fun getReference(): VcReference?
}

abstract class VcCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node),
                                                       VcCompositeElement {
    override fun getReference(): VcReference? = null
}

abstract class VcStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT>,
                                                              VcCompositeElement {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun toString(): String = "${javaClass.simpleName}($elementType)"

    override fun getReference(): VcReference? = null
}
