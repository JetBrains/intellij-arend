package org.arend.quickfix.instance

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.definition.FunctionDefinition
import org.arend.core.expr.Expression
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendDefinition
import org.arend.psi.ext.ArendGroup
import org.arend.psi.ext.ArendLongName
import org.arend.refactoring.*
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.ErrorService
import org.arend.ext.error.InstanceInferenceError
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.resolving.ArendReferenceBase
import org.arend.util.ArendBundle

class InstanceInferenceQuickFix(val error: InstanceInferenceError, val cause: SmartPsiElementPointer<ArendLongName>) : IntentionAction {
    private val classRef: TCDefReferable?
        get() = error.classRef as? TCDefReferable

    override fun startInWriteAction(): Boolean = false

    override fun getText(): String = ArendBundle.message("arend.instance.importInstance")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        error.classifyingExpression != null || project.service<TypeCheckingService>().isInstanceAvailable(classRef)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null) return
        var instances : List<List<FunctionDefinition>>? = null

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Searching for instances", true) {
            override fun run(indicator: ProgressIndicator) {
                instances = project.service<TypeCheckingService>().findInstances(classRef, error.classifyingExpression as? Expression)
            }

            override fun onFinished() {
                val instancesVal = instances
                val longName = cause.element

                if (instancesVal != null && instancesVal.size > 1 && longName != null) {
                    val lookupList = instancesVal.map {
                        val ref = it.first().ref
                        (ArendReferenceBase.createArendLookUpElement(ref, null, false, null, false, "") ?: LookupElementBuilder.create(ref, "")).withPresentableText(ref.refName)
                    }
                    val lookup = LookupManager.getInstance(project).showLookup(editor, *lookupList.toTypedArray())
                    lookup?.addLookupListener(object : LookupListener {
                        override fun itemSelected(event: LookupEvent) {
                            val index = lookupList.indexOf(event.item)
                            if (index != -1) {
                                PsiDocumentManager.getInstance(project).commitAllDocuments()
                                doAddImplicitArg(project, longName, instancesVal[index])
                            }
                        }
                    })
                    (lookup as? LookupImpl)?.addAdvertisement("Choose instance", null)
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
                val enclosingDefinition = longName.ancestor<ArendDefinition<*>>()
                val mySourceContainer = enclosingDefinition?.parentGroup
                if (mySourceContainer != null) {
                    val psiFactory = ArendPsiFactory(project)

                    for (element in chosenElement) {
                        val sourceContainerFile = (mySourceContainer as PsiElement).containingFile as ArendFile
                        val elementReferable = element.referable?.data as? PsiLocatedReferable ?: continue
                        val targetLocation = LocationData.createLocationData(elementReferable)
                        val importData = targetLocation?.let { calculateReferenceName(it, sourceContainerFile, longName) }

                        if (importData != null) {
                            val openedName: List<String> = importData.second
                            importData.first?.execute()
                            if (openedName.size > 1 && elementReferable is ArendGroup)
                                doAddIdToOpen(psiFactory, openedName, longName, elementReferable, instanceMode = true)
                        }
                    }

                    val tcService = project.service<TypeCheckingService>()
                    val file = mySourceContainer.containingFile as? ArendFile ?: return@runWriteCommandAction
                    tcService.updateDefinition(enclosingDefinition, file, true)
                    for ((error,element) in project.service<ErrorService>().getTypecheckingErrors(file)) {
                        if (error is InstanceInferenceError) {
                            element.ancestor<ArendDefinition<*>>()?.let {
                                tcService.updateDefinition(it, file, true)
                            }
                        }
                    }
                }
            }, longName.containingFile)
        }
    }

}