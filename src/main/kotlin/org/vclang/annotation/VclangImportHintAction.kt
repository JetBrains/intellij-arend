package org.vclang.annotation

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.HintAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.popup.list.ListPopupImpl
import com.jetbrains.jetpad.vclang.util.LongName
import org.vclang.quickfix.ResolveRefFixData

class VclangImportHintAction(val proposedImports: List<ResolveRefFixData>) : HintAction, HighPriorityAction {
    override fun startInWriteAction(): Boolean = false

    override fun getFamilyName(): String = "vclang.reference.resolve"

    override fun showHint(editor: Editor): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return this.proposedImports.isNotEmpty()
    }

    override fun getText(): String {
        return "Fix import"
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (proposedImports.size == 1)
            proposedImports[0].execute(editor)

        if (proposedImports.size > 1) {
            val step = object: BaseListPopupStep<ResolveRefFixData>("FIXER", proposedImports) {
                override fun getTextFor(value: ResolveRefFixData?): String {
                    if (value != null) {
                        val location = value.targetFullName.subList(0, value.targetFullName.size-1)
                        val name = value.targetFullName[value.targetFullName.size - 1]
                        return name + " in " + LongName(location).toString()
                    }
                    return super.getTextFor(value)
                }

                override fun onChosen(selectedValue: ResolveRefFixData?, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice && selectedValue != null) {
                        return doFinalStep {
                            PsiDocumentManager.getInstance(project).commitAllDocuments()

                            WriteCommandAction.runWriteCommandAction(project, QuickFixBundle.message("add.import"), null, object : Runnable {
                                        override fun run() = selectedValue.execute(editor)
                                    },
                                    selectedValue.currentElement.getContainingFile())

                        }
                    }

                    return PopupStep.FINAL_CHOICE
                }
            }
            val popup = ListPopupImpl(step)

            if (editor != null)
              popup.showInBestPositionFor(editor)
        }
    }

    fun doFix(editor: Editor, allowPopup : Boolean, allowCaretNearRef : Boolean) {

    }
}
