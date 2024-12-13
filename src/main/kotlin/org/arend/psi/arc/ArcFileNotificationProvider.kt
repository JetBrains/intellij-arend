package org.arend.psi.arc

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import org.arend.ext.module.ModulePath
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.module.config.ArendModuleConfigService
import org.arend.psi.ArendFile
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.typechecking.ProgressCancellationIndicator
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.TestBasedTypechecking
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.TypecheckingErrorReporter
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.execution.TypeCheckProcessHandler
import org.arend.typechecking.execution.TypecheckingEventsProcessor
import org.arend.typechecking.order.Ordering
import org.arend.typechecking.order.listener.CollectingOrderingListener
import org.arend.util.*
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.FileUtils.SERIALIZED_EXTENSION
import java.util.function.Function
import javax.swing.JComponent

class ArcFileNotificationProvider : EditorNotificationProvider {
    private val indicator: ProgressIndicator = ProgressIndicatorBase()

    override fun collectNotificationData(project: Project, virtualFile: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!project.service<ArcUnloadedModuleService>().containsUnloadedModule(virtualFile)) {
            return null
        }
        return Function { createPanel(project, virtualFile, it) }
    }

    private fun createPanel(project: Project, virtualFile: VirtualFile, editor: FileEditor): EditorNotificationPanel? {
        val panel = EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info)

        val config = project.arendModules.map { ArendModuleConfigService.getInstance(it) }.find {
            it?.root?.let { root -> VfsUtilCore.isAncestor(root, virtualFile, true) } ?: false
        } ?: ArendModuleConfigService.getInstance(project.arendModules.getOrNull(0))
        val relativePath = config?.binariesDirFile?.getRelativePath(virtualFile) ?: mutableListOf(virtualFile.name)
        relativePath[relativePath.lastIndex] = relativePath[relativePath.lastIndex].removeSuffix(SERIALIZED_EXTENSION)

        val psiManager = PsiManager.getInstance(project)
        val arendFile = config?.sourcesDirFile?.getRelativeFile(relativePath, EXTENSION)
            ?.let { psiManager.findFile(it) } as? ArendFile? ?: return null
        val modulePath = arendFile.moduleLocation?.modulePath ?: ModulePath()
        panel.text = ArendBundle.message("arend.arc.retypecheck", modulePath)

        panel.createActionLabel(ArendBundle.message("arend.arc.typecheck")) {
            ApplicationManager.getApplication().executeOnPooledThread {
                project.service<ArcUnloadedModuleService>().removeLoadedModule(virtualFile)
                val library = arendFile.arendLibrary
                runReadAction {
                    library?.resetGroup(arendFile)
                }

                val typeCheckerService = project.service<TypeCheckingService>()
                val eventsProcessor = TypecheckingEventsProcessor(
                    project,
                    SMTestProxy.SMRootTestProxy(),
                    "ArcCheckRunner"
                )
                val typecheckingErrorReporter = TypecheckingErrorReporter(typeCheckerService.project.service(), PrettyPrinterConfig.DEFAULT, eventsProcessor)
                val concreteProvider = PsiConcreteProvider(typeCheckerService.project, project.service<ErrorService>(), typecheckingErrorReporter.eventsProcessor)
                val instanceProviderSet = PsiInstanceProviderSet()
                val collector = CollectingOrderingListener()
                val ordering = Ordering(instanceProviderSet, concreteProvider, collector, typeCheckerService.dependencyListener, ArendReferableConverter, PsiElementComparator)
                runReadAction {
                    TypeCheckProcessHandler.orderGroup(arendFile, ordering, indicator)
                }

                val typechecking = TestBasedTypechecking(typecheckingErrorReporter.eventsProcessor, instanceProviderSet, typeCheckerService, concreteProvider, typecheckingErrorReporter, typeCheckerService.dependencyListener)
                try {
                    typechecking.typecheckCollected(collector, ProgressCancellationIndicator(indicator))
                } finally {
                    typecheckingErrorReporter.flush()
                    project.afterTypechecking(listOf(arendFile))

                    EditorNotifications.getInstance(project).updateNotifications(virtualFile)
                    invokeLater {
                        FileDocumentManager.getInstance().reloadBinaryFiles()
                    }
                }
            }
        }
        return panel
    }
}
