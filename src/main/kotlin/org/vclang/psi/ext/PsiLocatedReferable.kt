package org.vclang.psi.ext

import com.intellij.openapi.application.runReadAction
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import org.vclang.psi.VcFile
import org.vclang.psi.ancestors
import org.vclang.resolving.DataLocatedReferable


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