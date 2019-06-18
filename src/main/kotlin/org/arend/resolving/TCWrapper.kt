package org.arend.resolving

import org.arend.naming.reference.*


abstract class TCReferableWrapper : TCReferable {
    protected abstract val referable: LocatedReferable

    override fun getLocation() = referable.location

    override fun getLocatedReferableParent() = referable.locatedReferableParent

    override fun getPrecedence() = referable.precedence

    override fun getTypecheckable(): TCReferable {
        val tc = referable.typecheckable
        return if (tc !== referable && tc is LocatedReferable) (wrap(tc) ?: this) else this
    }

    override fun getData() = referable

    override fun textRepresentation() = referable.textRepresentation()

    override fun getKind() = referable.kind

    override fun getTypeClassReference() = referable.typeClassReference

    override fun getTypeOf() = referable.typeOf

    override fun getParameterType(parameters: MutableList<Boolean>?) = referable.getParameterType(parameters)

    companion object {
        fun wrap(referable: LocatedReferable?): TCReferable? =
                when (referable) {
                    null -> null
                    is TCReferable -> referable
                    is FieldReferable -> TCFieldReferableWrapper(referable)
                    is ClassReferable -> TCClassReferableWrapper(referable)
                    else -> TCReferableWrapperImpl(referable)
                }
    }
}

class TCReferableWrapperImpl(override val referable: LocatedReferable) : TCReferableWrapper()

class TCFieldReferableWrapper(override val referable: FieldReferable) : TCFieldReferable, TCReferableWrapper() {
    override fun isExplicitField() = referable.isExplicitField

    override fun isParameterField() = referable.isParameterField
}

class TCClassReferableWrapper(override val referable: ClassReferable) : TCClassReferable, TCReferableWrapper() {
    override fun getUnresolvedSuperClassReferences(): Collection<Reference> = referable.unresolvedSuperClassReferences

    override fun getSuperClassReferences() = referable.superClassReferences.map { TCClassReferableWrapper(it) }

    override fun getFieldReferables() = referable.fieldReferables.map { TCFieldReferableWrapper(it) }

    override fun getImplementedFields() = referable.implementedFields.map { if (it is FieldReferable) TCFieldReferableWrapper(it) else it }

    override fun isRecord() = referable.isRecord
}
