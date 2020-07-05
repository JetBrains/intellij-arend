package org.arend.resolving

import org.arend.naming.reference.*


abstract class TCReferableWrapper : TCReferable {
    protected abstract val referable: LocatedReferable

    override fun getLocation() = referable.location

    override fun getLocatedReferableParent() = referable.locatedReferableParent

    override fun getPrecedence() = referable.precedence

    override fun hasAlias() = referable.hasAlias()

    override fun getAliasName(): String? = referable.aliasName

    override fun getAliasPrecedence() = referable.aliasPrecedence

    override fun getTypecheckable(): TCReferable {
        val tc = referable.typecheckable
        return if (tc !== referable && tc is LocatedReferable) (wrap(tc) ?: this) else this
    }

    override fun getData() = referable.underlyingReferable

    override fun getUnderlyingReferable() = referable

    override fun textRepresentation() = referable.textRepresentation()

    override fun getKind() = referable.kind

    override fun equals(other: Any?) = this === other || referable == (other as? TCReferableWrapper)?.referable

    override fun hashCode() = referable.hashCode()

    companion object {
        fun wrap(referable: LocatedReferable?) = when (referable) {
            null -> null
            is TCReferable -> referable
            is FieldReferable -> TCFieldReferableWrapper(referable)
            else -> TCReferableWrapperImpl(referable)
        }
    }

}

class TCReferableWrapperImpl(override val referable: LocatedReferable) : TCReferableWrapper()

class TCFieldReferableWrapper(override val referable: FieldReferable) : TCFieldReferable, TCReferableWrapper() {
    override fun isExplicitField() = referable.isExplicitField

    override fun isParameterField() = referable.isParameterField
}

object WrapperReferableConverter : BaseReferableConverter() {
    override fun toDataLocatedReferable(referable: LocatedReferable?) = TCReferableWrapper.wrap(referable)
}
