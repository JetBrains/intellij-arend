package org.arend.typechecking

import org.arend.core.context.param.DependentLink
import org.arend.core.definition.Definition
import org.arend.core.expr.Expression
import org.arend.core.sort.Sort
import org.arend.naming.reference.TCClassReferable
import org.arend.naming.reference.TCReferable
import org.arend.psi.ArendDefinition

class DumbTypecheckerState(private val service: TypeCheckingService) : TypecheckerState {
    private val map = HashMap<TCReferable, Definition>()

    override fun getTypechecked(ref: TCReferable): Definition? {
        val def = map.computeIfAbsent(ref) {
            (ref.underlyingReferable as? ArendDefinition)?.let { service.getTypechecked(it) } ?: NullDefinition
        }
        return if (def === NullDefinition) null else def
    }

    private object NullDefinition : Definition(TCClassReferable.NULL_REFERABLE, TypeCheckingStatus.NO_ERRORS) {
        override fun getTypeWithParams(params: MutableList<in DependentLink>?, sortArgument: Sort?) = null
        override fun getDefCall(sortArgument: Sort?, args: MutableList<Expression>?) = null
    }

    override fun record(ref: TCReferable, def: Definition?) = getTypechecked(ref)

    override fun rewrite(ref: TCReferable?, def: Definition?) {}

    override fun reset(ref: TCReferable) = getTypechecked(ref)

    override fun reset() {}
}