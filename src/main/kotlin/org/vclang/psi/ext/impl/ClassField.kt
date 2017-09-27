package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import org.vclang.VcIcons
import org.vclang.psi.VcClassField
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcExpr
import org.vclang.psi.ancestors
import org.vclang.psi.stubs.VcClassFieldStub
import javax.swing.Icon

abstract class ClassFieldAdapter : ReferableAdapter<VcClassFieldStub>, VcClassField {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassFieldStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun computeConcrete(errorReporter: ErrorReporter): Concrete.ClassField? {
        val classDef = ancestors.filterIsInstance<VcDefClass>().firstOrNull()?.computeConcrete(errorReporter) as? Concrete.ClassDefinition ?: return null
        return classDef.fields.firstOrNull { it.data === this }
    }

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun getReferable(): GlobalReferable = this

    override fun getResultType(): VcExpr = expr

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_FIELD
}
