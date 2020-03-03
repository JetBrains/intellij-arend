package org.arend.typechecking

import org.arend.core.definition.Definition
import org.arend.naming.reference.TCReferable
import org.arend.typechecking.computation.ComputationRunner


class BackgroundTypecheckerState(private val typecheckerState: TypecheckerState) : TypecheckerState {
    override fun getTypechecked(def: TCReferable?): Definition? = typecheckerState.getTypechecked(def)

    override fun reset(def: TCReferable?): Definition? = typecheckerState.reset(def)

    override fun reset() {
        typecheckerState.reset()
    }

    override fun record(def: TCReferable?, res: Definition?): Definition? =
        synchronized(typecheckerState) {
            ComputationRunner.checkCanceled()
            typecheckerState.record(def, res)
        }

    override fun rewrite(def: TCReferable?, res: Definition?) {
        synchronized(typecheckerState) {
            ComputationRunner.checkCanceled()
            typecheckerState.rewrite(def, res)
        }
    }
}