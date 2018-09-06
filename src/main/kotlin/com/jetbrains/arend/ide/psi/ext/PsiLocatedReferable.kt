package com.jetbrains.arend.ide.psi.ext

import com.intellij.openapi.application.runReadAction
import com.jetbrains.arend.ide.psi.ArdFile
import com.jetbrains.arend.ide.psi.ancestors
import com.jetbrains.arend.ide.resolving.DataLocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable


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
    if (!(parent == null || parent is ArdFile)) {
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