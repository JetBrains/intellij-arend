package org.arend.quickfix.instance

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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.popup.list.ListPopupImpl
import org.arend.core.definition.FunctionDefinition
import org.arend.ext.module.LongName
import org.arend.psi.*
import org.arend.psi.ext.fullName
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.refactoring.*
import org.arend.resolving.DataLocatedReferable
import org.arend.term.NamespaceCommand
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.inference.InstanceInferenceError
import java.util.Collections.singletonList
import javax.swing.Icon

class InstanceInferenceQuickFix(val error: InstanceInferenceError, val cause: SmartPsiElementPointer<ArendLongName>):
    IntentionAction {
    override fun startInWriteAction(): Boolean = false

    override fun getText(): String = "Import instance"

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
            WriteCommandAction.runWriteCommandAction(project, "Import Instance", null, {
                val enclosingDefinition = longName.ancestor<ArendDefinition>()
                val mySourceContainer = enclosingDefinition?.parentGroup
                if (mySourceContainer != null) {
                    val psiFactory = ArendPsiFactory(project)
                    val anchor = mySourceContainer.namespaceCommands.lastOrNull { it.kind == NamespaceCommand.Kind.OPEN }?.let {RelativePosition(PositionKind.AFTER_ANCHOR, it as PsiElement)}
                        ?: mySourceContainer.namespaceCommands.lastOrNull()?.let{ RelativePosition(PositionKind.AFTER_ANCHOR, it as PsiElement) }
                        ?: if (mySourceContainer.statements.isNotEmpty()) RelativePosition(PositionKind.BEFORE_ANCHOR, mySourceContainer.statements.first()) else
                            getAnchorInAssociatedModule(psiFactory, mySourceContainer)?.let{RelativePosition(PositionKind.AFTER_ANCHOR, it)}

                    for (element in chosenElement) {
                        val sourceContainerFile = (mySourceContainer as PsiElement).containingFile as ArendFile
                        val elementReferable = (element.referable as? DataLocatedReferable)?.data?.element ?: continue
                        val targetContainer = (elementReferable as ArendGroup).parentGroup
                        val targetLocation = LocationData(elementReferable)
                        val importData = calculateReferenceName(targetLocation, sourceContainerFile, longName)

                        if (importData != null && anchor != null) {
                            val openedName: List<String> = importData.second
                            importData.first?.execute()
                            if (openedName.size > 1 && targetContainer is PsiElement)
                            addIdToUsing(enclosingDefinition.parent, targetContainer, LongName(openedName.subList(0, openedName.size - 1)).toString(), singletonList(Pair(openedName.last(), null)), psiFactory, anchor)
                        }
                    }

                    val tcService = project.service<TypeCheckingService>()
                    val file = mySourceContainer.containingFile as? ArendFile ?: return@runWriteCommandAction
                    tcService.updateDefinition(enclosingDefinition, file, true)
                    for ((error,element) in project.service<ErrorService>().getTypecheckingErrors(file)) {
                        if (error is InstanceInferenceError) {
                            element.ancestor<ArendDefinition>()?.let {
                                tcService.updateDefinition(it, file, true)
                            }
                        }
                    }
                    project.service<ArendPsiChangeService>().incModificationCount()
                }
            }, longName.containingFile)
        }
    }

}