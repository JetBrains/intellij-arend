package org.arend.codeInsight

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.impl.LineMarkerNavigator
import com.intellij.codeInsight.daemon.impl.MarkerType
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.*
import com.intellij.psi.presentation.java.ClassPresentationUtil
import com.intellij.ui.SimpleListCellRenderer
import org.arend.psi.ArendDefClass
import org.arend.psi.ArendDefIdentifier
import com.intellij.util.Function
import com.intellij.util.containers.toArray
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.util.ClassInheritorsSearch
import org.arend.util.FullName
import java.awt.event.MouseEvent
import javax.swing.JList

class ArendLineMarkerProvider: LineMarkerProviderDescriptor() {
    override fun getName(): String {
        return "Arend line markers"
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        return null
    }

    override fun collectSlowLineMarkers(elements: MutableList<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        for (element in elements) {
            if (element is ArendDefIdentifier && element.parent is ArendDefClass) {
                val clazz = element.parent as ArendDefClass
                if (!ClassInheritorsSearch.getInstance(element.project).search(clazz).isEmpty()) {
                    result.add(LineMarkerInfo(element.id, element.textRange, AllIcons.Gutter.OverridenMethod,
                            SUPERCLASS_OF.tooltip, SUPERCLASS_OF.navigationHandler,
                            GutterIconRenderer.Alignment.RIGHT))
                }
            }
        }
    }

    companion object {
        private val SUPERCLASS_OF = MarkerType("SUPERCLASS_OF",
                Function<PsiElement, String> { x -> getSubclassesTooltip(x) },
                object : LineMarkerNavigator() {
                    override fun browse(e: MouseEvent, element: PsiElement) {
                        val clazz = element.parent.parent as ArendDefClass
                        PsiElementListNavigator.openTargets(e, ClassInheritorsSearch.getInstance(element.project).search(clazz).toArray(arrayOf()), "Subclasses of " + clazz.name,
                                CodeInsightBundle.message("goto.implementation.findUsages.title", clazz.name),
                                MyListCellRenderer(), null)
                    }
                })

        private fun getSubclassesTooltip(e: PsiElement): String {
            return "Is overridden by several subclasses"
        }
    }

    private class MyListCellRenderer: PsiElementListCellRenderer<ArendDefClass>() {
        override fun getElementText(element: ArendDefClass): String {
            val fullName = FullName(element)
            return PsiBundle.message("class.context.display", fullName.longName.toString(), fullName.modulePath.toString())
        }

        override fun getContainerText(element: ArendDefClass, name: String): String? {
            return PsiClassListCellRenderer.getContainerTextStatic(element)
        }

        override fun getIconFlags(): Int {
            return 0
        }
    }
}