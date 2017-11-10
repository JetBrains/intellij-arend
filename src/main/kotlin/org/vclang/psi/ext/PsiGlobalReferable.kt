package org.vclang.psi.ext

import com.jetbrains.jetpad.vclang.naming.FullName
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable


interface PsiGlobalReferable : GlobalReferable, PsiReferable

private fun PsiGlobalReferable.getFullName(builder: StringBuilder) {
    val parent = parent
    if (parent is PsiGlobalReferable) {
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
    get() {
        val parent = parent
        return if (parent is PsiGlobalReferable) {
            FullName(parent.fullName_, textRepresentation())
        } else {
            FullName(textRepresentation())
        }
    }
