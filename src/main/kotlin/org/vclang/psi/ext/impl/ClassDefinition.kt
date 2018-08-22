package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.*
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.ClassFieldImplScope
import com.jetbrains.jetpad.vclang.naming.scope.ScopeFactory
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import com.jetbrains.jetpad.vclang.term.group.ChildGroup
import com.jetbrains.jetpad.vclang.term.group.Group
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.stubs.VcDefClassStub
import org.vclang.typing.ExpectedTypeVisitor
import javax.swing.Icon

abstract class ClassDefinitionAdapter : DefinitionAdapter<VcDefClassStub>, VcDefClass, Abstract.ClassDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefClassStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReferable() = this

    override fun getClassReference() = if (fatArrow == null) this else underlyingReference

    override fun isRecord(): Boolean = recordKw != null

    private fun resolve(ref: Referable?) =
        ExpressionResolveNameVisitor.resolve(ref, parentGroup?.groupScope ?: ScopeFactory.forGroup(null, moduleScopeProvider)) as? ClassReferable

    override fun getSuperClassReferences(): List<ClassReferable> = longNameList.mapNotNull { resolve(it.referent) }

    override fun getUnresolvedSuperClassReferences(): List<Reference> = longNameList

    override fun getDynamicSubgroups(): List<ChildGroup> = classStatList.mapNotNull { it.definition ?: it.defModule as ChildGroup? }

    private inline val parameterFields: List<VcFieldDefIdentifier> get() = fieldTeleList.flatMap { it.fieldDefIdentifierList }

    override fun getFields(): List<Group.InternalReferable> =
        (parameterFields as List<Group.InternalReferable> ) +
            classStatList.mapNotNull { it.classField } +
            classFieldSynList

    override fun getFieldReferables(): List<FieldReferable> =
        (parameterFields as List<FieldReferable>) +
            classStatList.mapNotNull { it.classField } +
            classFieldSynList

    override fun getParameters(): List<Abstract.Parameter> = fieldTeleList

    override fun getSuperClasses(): List<VcLongName> = longNameList

    override fun getClassFields(): List<Abstract.ClassField> = classStatList.mapNotNull { it.classField }

    override fun getClassFieldImpls(): List<VcClassImplement> = classStatList.mapNotNull { it.classImplement }

    override fun getArgumentsExplicitness() = emptyList<Boolean>()

    override fun getPrecedence() = calcPrecedence(prec)

    override fun getUnderlyingClass() = refIdentifier

    override fun getUnderlyingReference() = resolve(unresolvedUnderlyingReference?.referent)

    override fun getUnresolvedUnderlyingReference(): Reference? = underlyingClass

    override fun getCoercingFunctions(): List<LocatedReferable> =
        (dynamicSubgroups + subgroups).mapNotNull { if (it is VcDefFunction && it.coerceKw != null) it else null }

    override fun getParameterType(params: List<Boolean>): Any? {
        val index = params.size - 1
        val fields = if (superClasses.isEmpty()) fieldReferables else ClassFieldImplScope(this, false).elements
        return if (index < fields.size) (fields.toList()[index] as? TypedReferable)?.typeOf else ExpectedTypeVisitor.TooManyArgumentsError(textRepresentation(), fields.size)
    }

    override fun getTypeOf() = ExpectedTypeVisitor.Universe

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitClass(this)

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_DEFINITION
}
