package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.*
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.ScopeFactory
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefClassStub
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.typing.Universe
import javax.swing.Icon

abstract class ClassDefinitionAdapter : DefinitionAdapter<ArendDefClassStub>, ArendDefClass, Abstract.ClassDefinition, ClassReferenceHolder {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefClassStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReferable() = this

    override fun isRecord(): Boolean = recordKw != null

    private fun resolve(ref: Referable?) =
        ExpressionResolveNameVisitor.resolve(ref, parentGroup?.groupScope ?: ScopeFactory.forGroup(null, moduleScopeProvider)) as? ClassReferable

    override fun getSuperClassReferences(): List<ClassReferable> = longNameList.mapNotNull { resolve(it.referent) }

    override fun getUnresolvedSuperClassReferences(): List<Reference> = longNameList

    override fun getDynamicSubgroups(): List<ArendGroup> = classStatList.mapNotNull { it.definition ?: it.defModule }

    private inline val parameterFields: List<ArendFieldDefIdentifier> get() = fieldTeleList.flatMap { it.fieldDefIdentifierList }

    override fun getInternalReferables(): List<ArendInternalReferable> =
        (parameterFields as List<ArendInternalReferable> ) +
            classStatList.mapNotNull { it.classField } +
            classFieldList

    override fun getFields() = internalReferables

    override fun getFieldReferables(): List<FieldReferable> =
        (parameterFields as List<FieldReferable>) +
            classStatList.mapNotNull { it.classField } +
            classFieldList

    override fun getImplementedFields(): List<LocatedReferable> =
        classFieldImpls.mapNotNull { it.longName.refIdentifierList.lastOrNull()?.reference?.resolve() as? LocatedReferable }

    override fun getParameters(): List<Abstract.FieldParameter> = fieldTeleList

    override fun getSuperClasses(): List<ArendLongName> = longNameList

    override fun getClassFields(): List<Abstract.ClassField> = classStatList.mapNotNull { it.classField } + classFieldList

    override fun getClassFieldImpls(): List<ArendClassImplement> = classStatList.mapNotNull { it.classImplement } + classImplementList

    override fun getClassReference() = this

    override fun getClassReferenceData(onlyClassRef: Boolean): ClassReferenceData? =
        ClassReferenceData(this, emptyList(), emptySet(), false)

    override fun getUsedDefinitions(): List<LocatedReferable> =
        (dynamicSubgroups + subgroups).mapNotNull { if (it is ArendDefFunction && it.useKw != null) it else null }

    override fun getTypeOf() = Universe

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitClass(this)

    override fun getIcon(flags: Int): Icon = ArendIcons.CLASS_DEFINITION
}
