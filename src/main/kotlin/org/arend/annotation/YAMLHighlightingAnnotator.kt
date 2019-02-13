package org.arend.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.module.ArendModuleType
import org.arend.module.config.*
import org.arend.psi.module
import org.arend.util.FileUtils
import org.jetbrains.yaml.YAMLTokenTypes
import org.jetbrains.yaml.psi.YAMLFile


class YAMLHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = holder.currentAnnotationSession.file as? YAMLFile ?: return
        if (file.name != FileUtils.LIBRARY_CONFIG_FILE) {
            return
        }
        val module = file.module ?: return
        if (!ArendModuleType.has(module)) {
            return
        }
        val rootPath = ArendModuleConfigService.getConfig(module).rootPath ?: return
        if (file.virtualFile.parent.path != FileUtil.toSystemIndependentName(rootPath.toString())) {
            return
        }

        if (element is LeafPsiElement && element.node.elementType == YAMLTokenTypes.SCALAR_KEY) {
            val text = element.text
            if (text != SOURCES && text != BINARIES && text != MODULES && text != DEPENDENCIES) {
                holder.createErrorAnnotation(element as PsiElement, "Unknown key: $text")
            }
        }
    }
}