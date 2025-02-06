package org.arend.psi.ext

import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.ArendFile
import org.arend.psi.ancestor


interface PsiLocatedReferable : LocatedReferable, PsiReferable {
    override fun getTypecheckable(): PsiLocatedReferable

    val defIdentifier: ArendDefIdentifier?

    companion object {
        fun fromReferable(referable: GlobalReferable) = referable.underlyingReferable as? PsiLocatedReferable
    }
}

private fun PsiLocatedReferable.getFullName(builder: StringBuilder) {
    val parent = parent?.ancestor<PsiLocatedReferable>()
    if (!(parent == null || parent is ArendFile)) {
        parent.getFullName(builder)
        builder.append('.')
    }
    builder.append(textRepresentation())
}

val PsiLocatedReferable.fullName: String
    get() {
        val builder = StringBuilder()
        getFullName(builder)
        return builder.toString()
    }