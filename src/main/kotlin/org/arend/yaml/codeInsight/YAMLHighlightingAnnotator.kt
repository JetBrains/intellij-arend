package org.arend.yaml.codeInsight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.yaml.KEYS
import org.arend.yaml.isYAMLConfig
import org.jetbrains.yaml.YAMLTokenTypes


class YAMLHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!holder.currentAnnotationSession.file.isYAMLConfig) {
            return
        }

        if (element is LeafPsiElement && element.node.elementType == YAMLTokenTypes.SCALAR_KEY) {
            val text = element.text
            if (!KEYS.contains(text)) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Unknown key: $text")
                    .range(element as PsiElement)
                    .create()
            }
        }
    }
}