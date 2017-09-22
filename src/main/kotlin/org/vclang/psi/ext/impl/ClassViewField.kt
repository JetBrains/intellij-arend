package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import org.vclang.VcIcons
import org.vclang.psi.VcClassViewField
import org.vclang.psi.stubs.VcClassViewFieldStub
import javax.swing.Icon

abstract class ClassViewFieldAdapter : ReferableAdapter<VcClassViewFieldStub>, VcClassViewField {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassViewFieldStub, nodeType: IStubElementType<*, *>): super(stub, nodeType)

    override fun computeConcrete(errorReporter: ErrorReporter): Concrete.ClassViewField? = null // TODO[classes]

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_VIEW_FIELD
}
