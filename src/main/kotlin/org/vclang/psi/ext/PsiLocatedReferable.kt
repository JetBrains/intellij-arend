package org.vclang.psi.ext

import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import org.vclang.psi.VcFile
import org.vclang.psi.ancestors
import org.vclang.resolving.DataLocatedReferable


interface PsiLocatedReferable : LocatedReferable, PsiReferable {
    override fun getTypecheckable(): PsiLocatedReferable

    override fun resolveTypeClassReference() = typeClassReference

    companion object {
        fun fromReferable(referable: GlobalReferable) = ((referable as? DataLocatedReferable)?.data ?: referable) as? PsiLocatedReferable
    }
}

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