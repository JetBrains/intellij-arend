package org.arend.codeInsight

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.impl.LineMarkerNavigator
import com.intellij.codeInsight.daemon.impl.MarkerType
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.codeInsight.navigation.BackgroundUpdaterTask
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiBundle
import com.intellij.psi.PsiElement
import com.intellij.util.containers.toArray
import org.arend.psi.ArendDefClass
import org.arend.psi.ArendDefIdentifier
import org.arend.search.ClassInheritorsSearch
import org.arend.util.FullName
import java.awt.event.MouseEvent

class ArendLineMarkerProvider: LineMarkerProviderDescriptor() {
    override fun getName() = "Arend line markers"

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        for (element in elements) {
            if (element is ArendDefIdentifier) {
                (element.parent as? ArendDefClass)?.let { clazz ->
                    ProgressManager.checkCanceled()
                    if (clazz.project.service<ClassInheritorsSearch>().search(clazz).isNotEmpty()) {
                        result.add(LineMarkerInfo(element.id, element.textRange, AllIcons.Gutter.OverridenMethod,
                            SUPERCLASS_OF.tooltip, SUPERCLASS_OF.navigationHandler,
                            GutterIconRenderer.Alignment.RIGHT))
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
                    PsiElementListNavigator.openTargets(e, clazz.project.service<ClassInheritorsSearch>().getAllInheritors(clazz).toArray(arrayOf()), "Subclasses of " + clazz.name,
                        CodeInsightBundle.message("goto.implementation.findUsages.title", clazz.name), MyListCellRenderer, null as BackgroundUpdaterTask?)
                }
            })
    }

    private object MyListCellRenderer : PsiElementListCellRenderer<ArendDefClass>() {
        override fun getElementText(element: ArendDefClass): String {
            val fullName = FullName(element)
            return PsiBundle.message("class.context.display", fullName.longName.toString(), fullName.modulePath.toString())
        }

        override fun getContainerText(element: ArendDefClass, name: String) =
            PsiClassListCellRenderer.getContainerTextStatic(element)

        override fun getIconFlags() = 0
    }
}