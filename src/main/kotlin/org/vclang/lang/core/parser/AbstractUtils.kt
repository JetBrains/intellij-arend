package org.vclang.lang.core.parser

import com.jetbrains.jetpad.vclang.term.Abstract

val Abstract.Definition.fullyQualifiedName: String
    get() = ancestors.toList().reversed().map { it.name }.joinToString(".")

val Abstract.Definition.ancestors: Sequence<Abstract.Definition>
    get() = generateSequence(this) { it.parentDefinition }
