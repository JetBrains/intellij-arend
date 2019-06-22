package org.arend.highlight

import com.intellij.codeInsight.daemon.ChangeLocalityDetector
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.psi.*
import org.arend.psi.ext.impl.ArendGroup

class ArendChangeLocalityDetector : ChangeLocalityDetector {
    override fun getChangeHighlightingDirtyScopeFor(element: PsiElement) =
        when (element) {
            is LeafPsiElement -> if (AREND_COMMENTS.contains(element.node.elementType)) element else null
            is ArendDefIdentifier -> if (element.parent is ArendGroup) element.containingFile else null
            is ArendStatement -> element.containingFile
            is ArendGroup -> element
            is ArendStatCmd ->
                when (val parent = (element.parent as? ArendStatement)?.parent) {
                    is ArendWhere -> parent.parent
                    is ArendFile -> parent
                    else -> null
                }
            else -> null
        }
}