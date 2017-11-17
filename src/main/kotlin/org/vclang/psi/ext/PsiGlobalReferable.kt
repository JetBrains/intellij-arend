package org.vclang.psi.ext

import com.jetbrains.jetpad.vclang.naming.FullName
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import org.vclang.psi.VcFile
import org.vclang.psi.ancestors


interface PsiGlobalReferable : GlobalReferable, PsiReferable

private fun PsiGlobalReferable.getFullName(builder: StringBuilder) {
    val parent = parent?.ancestors?.filterIsInstance<PsiGlobalReferable>()?.firstOrNull()
    if (!(parent == null || parent is VcFile)) {
        parent.getFullName(builder)
        builder.append('.')
    }
    builder.append(textRepresentation())
}

val PsiGlobalReferable.fullName: String
    get() {
        val builder = StringBuilder()
        getFullName(builder)
        return builder.toString()
    }

val PsiGlobalReferable.fullName_: FullName
    get() = FullName(ancestors.filterIsInstance<PsiGlobalReferable>().takeWhile { it !is VcFile }.map { it.textRepresentation() }.toList().asReversed())
