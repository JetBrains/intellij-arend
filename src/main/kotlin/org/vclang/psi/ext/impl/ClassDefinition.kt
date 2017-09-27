package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.stubs.VcDefClassStub
import javax.swing.Icon

abstract class ClassDefinitionAdapter : DefinitionAdapter<VcDefClassStub>, VcDefClass, Abstract.ClassDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefClassStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getDynamicSubgroups(): List<VcDefinition> = classStatList.mapNotNull { it.definition }

    override fun getFields(): List<VcClassField> = classStatList.mapNotNull { it.classField }

    override fun getParameters(): List<VcTele> = teleList

    override fun getSuperClasses(): List<VcExpr> = atomFieldsAccList

    override fun getClassFields(): List<VcClassField> = classStatList.mapNotNull { it.classField }

    override fun getClassFieldImpls(): List<VcClassImplement> = classStatList.mapNotNull { it.classImplement }

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitClass(this)

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_DEFINITION
}
