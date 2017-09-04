package org.vclang.parser

import com.jetbrains.jetpad.vclang.term.Abstract
import org.vclang.psi.*

val Abstract.Definition.fullName: String
    get() = ancestors.toList().reversed().map { it.name }.joinToString(".")

val Abstract.Definition.ancestors: Sequence<Abstract.Definition>
    get() = generateSequence(this) { it.parentDefinition }

val VcDefinition.isTypeCheckable
    get() = this is VcDefClass
            || this is VcDefData
            || this is VcDefInstance
            || this is VcDefFunction
