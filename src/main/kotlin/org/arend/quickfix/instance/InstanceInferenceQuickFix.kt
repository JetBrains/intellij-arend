package org.arend.quickfix.instance

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.popup.list.ListPopupImpl
import org.arend.core.definition.FunctionDefinition
import org.arend.psi.ArendLongName
import org.arend.psi.ext.fullName
import org.arend.resolving.DataLocatedReferable
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.local.inference.InstanceInferenceError
import javax.swing.Icon

class InstanceInferenceQuickFix(val error: InstanceInferenceError, val cause: SmartPsiElementPointer<ArendLongName>):
    IntentionAction {
    override fun startInWriteAction(): Boolean = false

    override fun getText(): String = "Specify class instance"

    override fun getFamilyName(): String = "arend.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null) return
        var instances : List<List<FunctionDefinition>>? = null

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Searching for instances", true) {
            override fun run(indicator: ProgressIndicator) {
                instances = project.service<TypeCheckingService>().findInstances(error.classRef, error.classifyingExpression)
            }

            override fun onFinished() {
                val instancesVal = instances
                val longName = cause.element

                if (instancesVal != null && instancesVal.size > 1 && longName != null) {
                    val step = object: BaseListPopupStep<List<FunctionDefinition>>("Class Instances", instancesVal) {
                        override fun getTextFor(value: List<FunctionDefinition>?): String {
                            val instance = value?.firstOrNull()
                            if (instance != null) {
                                val element = (instance.referable as? DataLocatedReferable)?.data?.element
                                if (element != null) return element.fullName
                            }
                            return super.getTextFor(value)
                        }

                        override fun getIconFor(value: List<FunctionDefinition>?): Icon? {
                            val instance = value?.firstOrNull()
                            if (instance != null) {
                                val element = (instance.referable as? DataLocatedReferable)?.data?.element
                                if (element != null) return element.getIcon(0)
                            }

                            return null
                        }


                        override fun onChosen(selectedValue: List<FunctionDefinition>?, finalChoice: Boolean): PopupStep<*>? {
                            if (finalChoice && selectedValue != null) {
                                return doFinalStep {
                                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                                    doAddImplicitArg(project, longName, selectedValue)
                                }
                            }

                            return FINAL_CHOICE
                        }
                    }
                    val popup = ListPopupImpl(project, step)

                    popup.showInBestPositionFor(editor)
                } else if (instancesVal != null && instancesVal.size == 1 && longName != null) {
                    doAddImplicitArg(project, longName, instancesVal.first())
                } else {
                    HintManager.getInstance().showErrorHint(editor, "InstanceInferenceQuickFix was unable to find instances for ${longName?.text}")
                }
            }
        })
    }

    companion object {
        fun doAddImplicitArg(project: Project, longName: ArendLongName, chosenElement: List<FunctionDefinition>) {
            WriteCommandAction.runWriteCommandAction(project, "Specify Instance", null, {
                //TODO: Implement me
            }, longName.containingFile)
        }
    }

}