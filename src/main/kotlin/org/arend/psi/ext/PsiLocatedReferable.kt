package org.arend.psi.ext

import org.arend.ext.module.LongName
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.term.abs.Abstract
import org.arend.util.FullName


interface PsiLocatedReferable : Abstract.AbstractLocatedReferable, PsiReferable {
    val defIdentifier: ArendDefIdentifier?

    companion object {
        fun fromReferable(referable: GlobalReferable) = referable.abstractReferable as? PsiLocatedReferable
    }
}

private fun PsiLocatedReferable.getFullName(builder: StringBuilder) {
    val parent = parent?.ancestor<PsiLocatedReferable>()
    if (!(parent == null || parent is ArendFile)) {
        parent.getFullName(builder)
        builder.append('.')
    }
    builder.append(refName)
}

val PsiLocatedReferable.fullNameText: String
    get() {
        val builder = StringBuilder()
        getFullName(builder)
        return builder.toString()
    }

val PsiLocatedReferable.fullName: FullName
    get() {
        val list = ArrayList<String>()
        var ref = this
        while (ref !is ArendFile) {
            list.add(ref.refName)
            ref = parent?.ancestor<PsiLocatedReferable>() ?: break
        }
        list.reverse()
        return FullName((ref as? ArendFile)?.moduleLocation, LongName(list))
    }
