package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.UnresolvedReference
import com.jetbrains.jetpad.vclang.naming.scope.ScopeFactory
import com.jetbrains.jetpad.vclang.term.Group
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.stubs.VcDefClassStub
import javax.swing.Icon

abstract class ClassDefinitionAdapter : DefinitionAdapter<VcDefClassStub>, VcDefClass, Abstract.ClassDefinition, Abstract.ClassReferenceHolder {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefClassStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReferable(): ClassReferable = this

    override fun getClassReference(): ClassReferable = this

    override fun getSuperClassReferences(): List<ClassReferable> = longNameList.mapNotNull {
        val ref = it.referent
        ((ref as? UnresolvedReference)?.resolve(parentGroup?.groupScope ?: ScopeFactory.forGroup(null, moduleScopeProvider)) ?: ref) as? ClassReferable
    }

    override fun getDynamicSubgroups(): List<Group> = classStatList.mapNotNull { it.definition }

    override fun getFields(): List<GlobalReferable> = (fieldTele?.fieldDefIdentifier?.let { listOf(it) } ?: emptyList()) + classStatList.mapNotNull { it.classField }

    override fun getSuperClasses(): List<VcLongName> = longNameList

    override fun getClassFields(): List<VcClassField> = classStatList.mapNotNull { it.classField }

    override fun getClassFieldImpls(): List<VcClassImplement> = classStatList.mapNotNull { it.classImplement }

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitClass(this)

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_DEFINITION
}
