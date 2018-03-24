package org.vclang.psi.ext

import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import org.vclang.psi.VcFile
import org.vclang.psi.ancestors


interface PsiLocatedReferable : LocatedReferable, PsiReferable

private fun PsiLocatedReferable.getFullName(builder: StringBuilder) {
    val parent = parent?.ancestors?.filterIsInstance<PsiLocatedReferable>()?.firstOrNull()
    if (!(parent == null || parent is VcFile)) {
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