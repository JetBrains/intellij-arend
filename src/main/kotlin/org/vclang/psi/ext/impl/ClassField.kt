package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.stubs.VcClassFieldStub
import javax.swing.Icon

abstract class ClassFieldAdapter : ReferableAdapter<VcClassFieldStub>, VcClassField {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassFieldStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun computeConcrete(referableConverter: ReferableConverter, errorReporter: ErrorReporter): Concrete.ClassField? {
        val classDef = ancestors.filterIsInstance<VcDefClass>().firstOrNull()?.computeConcrete(referableConverter, errorReporter) as? Concrete.ClassDefinition ?: return null
        return classDef.fields.firstOrNull { it.data === this }
    }

    override fun getPrecedence() = calcPrecedence(prec)

    override fun getReferable() = this

    override fun getParameters(): List<VcTypeTele> = typeTeleList

    override fun getResultType(): VcExpr? = expr

    override fun isVisible() = true

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_FIELD
}
