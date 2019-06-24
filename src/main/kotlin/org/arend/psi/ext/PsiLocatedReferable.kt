package org.arend.psi.ext

import com.intellij.openapi.application.runReadAction
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCReferable
import org.arend.psi.ArendDefIdentifier
import org.arend.psi.ArendFile
import org.arend.psi.ancestors
import org.arend.resolving.DataLocatedReferable


interface PsiLocatedReferable : LocatedReferable, PsiReferable {
    override fun getTypecheckable(): PsiLocatedReferable

    val defIdentifier: ArendDefIdentifier?

    companion object {
        fun fromReferable(referable: GlobalReferable): PsiLocatedReferable? {
            val psiPtr = (referable as? DataLocatedReferable)?.data ?: return referable as? PsiLocatedReferable
            return runReadAction { psiPtr.element } as? PsiLocatedReferable
        }

        // fun fromReferable(referable: Referable) = (referable as? GlobalReferable)?.let { fromReferable(it) } ?: referable

        fun isValid(ref: TCReferable?) = ref != null && (ref !is DataLocatedReferable || ref.data == null || runReadAction { ref.data?.element } != null)
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