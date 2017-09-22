package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import org.vclang.VcIcons
import org.vclang.psi.VcConstructor
import org.vclang.psi.stubs.VcConstructorStub
import javax.swing.Icon

abstract class ConstructorAdapter : ReferableAdapter<VcConstructorStub>, VcConstructor {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcConstructorStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun computeConcrete(errorReporter: ErrorReporter): Concrete.Constructor {
        TODO("not implemented")
    }

    override fun getIcon(flags: Int): Icon = VcIcons.CONSTRUCTOR
}
