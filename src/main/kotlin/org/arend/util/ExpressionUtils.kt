package org.arend.util

import org.arend.core.context.param.DependentLink
import org.arend.core.context.param.EmptyDependentLink
import org.arend.core.expr.PiExpression

fun PiExpression.allParameters(): Sequence<PiExpression> = generateSequence(this) { it.codomain as? PiExpression }

fun DependentLink.allBindings(): Sequence<DependentLink> = generateSequence(this) { it.next.takeIf { it !is EmptyDependentLink } }