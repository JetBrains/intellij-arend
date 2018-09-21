package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.naming.reference.*
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.ScopeFactory
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.term.group.ChildGroup
import org.arend.term.group.Group
import org.arend.ArendIcons
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefClassStub
import org.arend.typing.ExpectedTypeVisitor
import javax.swing.Icon

abstract class ClassDefinitionAdapter : DefinitionAdapter<ArendDefClassStub>, ArendDefClass, Abstract.ClassDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefClassStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReferable() = this

    override fun getClassReference() = if (fatArrow == null) this else underlyingReference

    override fun isRecord(): Boolean = recordKw != null

    private fun resolve(ref: Referable?) =
        ExpressionResolveNameVisitor.resolve(ref, parentGroup?.groupScope ?: ScopeFactory.forGroup(null, moduleScopeProvider)) as? ClassReferable

    override fun getSuperClassReferences(): List<ClassReferable> = longNameList.mapNotNull { resolve(it.referent) }

    override fun getUnresolvedSuperClassReferences(): List<Reference> = longNameList

    override fun getDynamicSubgroups(): List<ChildGroup> = classStatList.mapNotNull { it.definition ?: it.defModule as ChildGroup? }

    private inline val parameterFields: List<ArendFieldDefIdentifier> get() = fieldTeleList.flatMap { it.fieldDefIdentifierList }

    override fun getFields(): List<Group.InternalReferable> =
        (parameterFields as List<Group.InternalReferable> ) +
            classStatList.mapNotNull { it.classField } +
            classFieldSynList

    override fun getFieldReferables(): List<FieldReferable> =
        (parameterFields as List<FieldReferable>) +
            classStatList.mapNotNull { it.classField } +
            classFieldSynList

    override fun getImplementedFields(): List<LocatedReferable> =
        classFieldImpls.mapNotNull { it.longName.refIdentifierList.lastOrNull()?.reference?.resolve() as? LocatedReferable }

    override fun getParameters(): List<Abstract.Parameter> = fieldTeleList

    override fun getSuperClasses(): List<ArendLongName> = longNameList

    override fun getClassFields(): List<Abstract.ClassField> = classStatList.mapNotNull { it.classField }

    override fun getClassFieldImpls(): List<ArendClassImplement> = classStatList.mapNotNull { it.classImplement }

    override fun getArgumentsExplicitness() = emptyList<Boolean>()

    override fun getPrecedence() = calcPrecedence(prec)

    override fun getUnderlyingClass() = refIdentifier

    override fun getUnderlyingReference() = resolve(unresolvedUnderlyingReference?.referent)

    override fun getUnresolvedUnderlyingReference(): Reference? = underlyingClass

    override fun getCoercingFunctions(): List<LocatedReferable> =
        (dynamicSubgroups + subgroups).mapNotNull { if (it is ArendDefFunction && it.coerceKw != null) it else null }

    override fun getParameterType(params: List<Boolean>): Any? {
        val fields = ClassReferable.Helper.getNotImplementedFields(this)
        val it = fields.iterator()
        var i = 0
        while (it.hasNext()) {
            val field = it.next()
            if (field.isExplicitField == params[i]) {
                i++
                if (i == params.size) {
                    return field.typeOf
                }
            } else if (!params[i]) {
                return if (i == params.size - 1) ExpectedTypeVisitor.ImplicitArgumentError(textRepresentation(), params.size) else null
            }
        }
        return ExpectedTypeVisitor.TooManyArgumentsError(textRepresentation(), fields.size)
    }

    override fun getTypeOf() = ExpectedTypeVisitor.Universe

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitClass(this)

    override fun getIcon(flags: Int): Icon = ArendIcons.CLASS_DEFINITION
}
