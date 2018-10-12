package org.arend.psi

import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.term.abs.Abstract


interface ClassReferenceHolder : Abstract.ClassReferenceHolder {
    fun getClassReferenceData(): ClassReferenceData?

    override fun getClassReference() = getClassReferenceData()?.classRef
}

class ClassReferenceData(
    val classRef: ClassReferable,
    val argumentsExplicitness: List<Boolean>,
    val implementedFields: List<LocatedReferable>)