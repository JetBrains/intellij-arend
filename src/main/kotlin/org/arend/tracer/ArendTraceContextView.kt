package org.arend.tracer

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPointerManager
import org.arend.injection.InjectedArendEditor
import org.arend.toolWindow.errors.ArendPrintOptionsActionGroup
import org.arend.toolWindow.errors.PrintOptionKind
import org.arend.toolWindow.errors.tree.ArendErrorTreeElement
import org.arend.typechecking.error.ArendError
import org.arend.util.ArendBundle

class ArendTraceContextView(project: Project) : InjectedArendEditor(project, "Arend Trace Context", null) {
    override val printOptionKind: PrintOptionKind = PrintOptionKind.TRACER_PRINT_OPTIONS

    init {
        actionGroup.add(ArendPrintOptionsActionGroup(project, PrintOptionKind.TRACER_PRINT_OPTIONS, ::updateErrorText))
    }

    fun update(traceEntry: ArendTraceEntry) {
        val psiElement = traceEntry.psiElement
        if (psiElement == null) {
            runWriteAction {
                modifyDocument { setText(ArendBundle.message("arend.tracer.no.information")) }
            }
            return
        }
        treeElement = ArendErrorTreeElement(ArendError(traceEntry.goalDataHolder, SmartPointerManager.createPointer(psiElement)))
        updateErrorText()
    }
}