package org.arend.psi

import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.ext.ArendCompositeElement
import org.arend.term.abs.Abstract


interface ClassReferenceHolder : Abstract.ClassReferenceHolder, ArendCompositeElement {
    // If onlyClassRef is true, getClassReferenceData will try to infer classRef from the type of a referable if it is not a class already.
    // This is required for expressions of the form \new c { ... }, where c is an instance of a class.
    fun getClassReferenceData(onlyClassRef: Boolean): ClassReferenceData?
}

class ClassReferenceData(
    val classRef: ClassReferable,
    val argumentsExplicitness: List<Boolean>,
    val implementedFields: Set<LocatedReferable>,
    val withTailImplicits: Boolean)