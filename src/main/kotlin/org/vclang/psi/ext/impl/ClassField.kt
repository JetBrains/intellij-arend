package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.term.Concrete
import org.vclang.VcIcons
import org.vclang.psi.VcClassField
import org.vclang.psi.stubs.VcClassFieldStub
import javax.swing.Icon

abstract class ClassFieldAdapter : ReferableAdapter<VcClassFieldStub>, VcClassField {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassFieldStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun computeConcrete(errorReporter: ErrorReporter): Concrete.ClassField {
        TODO("not implemented")
    }

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_FIELD
}
