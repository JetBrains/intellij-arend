package org.arend.psi.ext

import com.intellij.openapi.application.runReadAction
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.ArendFile
import org.arend.psi.ancestors
import org.arend.resolving.DataLocatedReferable


interface PsiLocatedReferable : LocatedReferable, PsiReferable {
    override fun getTypecheckable(): PsiLocatedReferable

    companion object {
        fun fromReferable(referable: GlobalReferable): PsiLocatedReferable? {
            val psiPtr = (referable as? DataLocatedReferable)?.data ?: return referable as? PsiLocatedReferable
            return runReadAction { psiPtr.element } as? PsiLocatedReferable
        }
    }
}

private fun PsiLocatedReferable.getFullName(builder: StringBuilder) {
    val parent = parent?.ancestors?.filterIsInstance<PsiLocatedReferable>()?.firstOrNull()
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