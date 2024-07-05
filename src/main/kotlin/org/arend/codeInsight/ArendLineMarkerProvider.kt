package org.arend.codeInsight

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.impl.LineMarkerNavigator
import com.intellij.codeInsight.daemon.impl.MarkerType
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import org.arend.psi.ext.ArendDefClass
import org.arend.psi.ext.ArendDefIdentifier
import org.arend.search.ClassDescendantsSearch
import java.awt.event.MouseEvent

class ArendLineMarkerProvider: LineMarkerProviderDescriptor() {
    override fun getName() = "Arend line markers"

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (element in elements) {
            if (element is ArendDefIdentifier) {
                (element.parent as? ArendDefClass)?.let { clazz ->
                    ProgressManager.checkCanceled()
                    if (clazz.project.service<ClassDescendantsSearch>().search(clazz).isNotEmpty()) {
                        result.add(LineMarkerInfo(element.id, element.textRange, AllIcons.Gutter.OverridenMethod,
                            SUPERCLASS_OF.tooltip, SUPERCLASS_OF.navigationHandler,
                            GutterIconRenderer.Alignment.RIGHT) { "subclasses" })
                    }
                }
            }
        }
    }

    companion object {
        private val SUPERCLASS_OF = MarkerType("SUPERCLASS_OF", { "Is overridden by several subclasses" },
            object : LineMarkerNavigator() {
                override fun browse(e: MouseEvent, element: PsiElement) {
                    val clazz = element.parent.parent as? ArendDefClass ?: return
                    PsiTargetNavigator(clazz.project.service<ClassDescendantsSearch>().getAllDescendants(clazz).toTypedArray()).navigate(e, "Subclasses of " + clazz.name, element.project)
                }
            })
    }
}
