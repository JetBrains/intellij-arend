package org.arend.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.module.config.KEYS
import org.arend.module.config.isYAMLConfig
import org.jetbrains.yaml.YAMLTokenTypes


class YAMLHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!holder.currentAnnotationSession.file.isYAMLConfig) {
            return
        }

        if (element is LeafPsiElement && element.node.elementType == YAMLTokenTypes.SCALAR_KEY) {
            val text = element.text
            if (!KEYS.contains(text)) {
                holder.createErrorAnnotation(element as PsiElement, "Unknown key: $text")
            }
        }
    }
}