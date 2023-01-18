package org.arend.psi.ext

import com.intellij.openapi.application.runReadAction
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCReferable
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.resolving.DataLocatedReferable


interface PsiLocatedReferable : LocatedReferable, PsiReferable {
    override fun getTypecheckable(): PsiLocatedReferable

    val defIdentifier: ArendDefIdentifier?

    val tcReferable: TCReferable?

    val tcReferableCached: TCReferable?
        get() = null

    fun dropTCReferable()

    companion object {
        fun fromReferable(referable: GlobalReferable) = referable.underlyingReferable as? PsiLocatedReferable

        fun isValid(ref: TCReferable?) = ref != null && (ref !is DataLocatedReferable || ref.data == null || runReadAction { ref.data?.element } != null)
    }
}

interface PsiDefReferable : PsiLocatedReferable {
    fun dropTypechecked()
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