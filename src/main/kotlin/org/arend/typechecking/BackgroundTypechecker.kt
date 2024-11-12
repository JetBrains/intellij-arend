package org.arend.typechecking

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ArendFile
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.settings.ArendSettings
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.order.listener.CollectingOrderingListener
import org.arend.typechecking.provider.ConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.typechecking.visitor.DumbTypechecker
import org.arend.util.FileUtils.SERIALIZED_EXTENSION
import org.arend.util.FullName
import org.arend.util.afterTypechecking
import org.arend.util.checkArcFile

class BackgroundTypechecker(private val project: Project, private val instanceProviderSet: PsiInstanceProviderSet, private val concreteProvider: ConcreteProvider, private val modificationCount: Long) {
    private val definitionBlackListService = service<DefinitionBlacklistService>()
    private val modificationTracker = service<ArendPsiChangeService>().definitionModificationTracker
    private val errorService: ErrorService = project.service()

    fun runTypechecker(file: ArendFile, lastModified: TCDefReferable?, collector1: CollectingOrderingListener, collector2: CollectingOrderingListener, runAnalyzer: Boolean) {
        if (checkArcFile(file.virtualFile)) {
            return
        }
        if (collector1.isEmpty && collector2.isEmpty) {
            return
        }

        val settings = service<ArendSettings>()
        val mode = settings.typecheckingMode
        if (mode == ArendSettings.TypecheckingMode.OFF) {
            return
        }

        if (mode == ArendSettings.TypecheckingMode.DUMB) {
            for (def in collector1.allDefinitions + collector2.allDefinitions) {
                runDumbTypechecker(def)
            }
            if (runAnalyzer) runReadAction { DaemonCodeAnalyzer.getInstance(project).restart(file) }
            return
        }

        object : Task.Backgroundable(project, "Typechecking", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                val check2 = !collector2.isEmpty && !settings.typecheckOnlyLast || lastModified == null || lastModified.typechecked?.status()?.isOK == true
                val total = collector1.elements.size + if (check2) collector2.elements.size else 0
                var i = 0

                val tcService = project.service<TypeCheckingService>()
                val typechecking = ArendTypechecking(tcService, instanceProviderSet, concreteProvider, errorService, tcService.dependencyListener, LibraryArendExtensionProvider(tcService.libraryManager))

                if (!collector1.isEmpty) {
                    for (element in collector1.elements) {
                        if (!typecheckDefinition(typechecking, element)) {
                            indicator.fraction = 1.0
                            return
                        }
                        indicator.fraction = (++i).toDouble() / total
                    }
                }

                if (check2) {
                    for (element in collector2.elements) {
                        if (!typecheckDefinition(typechecking, element)) {
                            indicator.fraction = 1.0
                            return
                        }
                        indicator.fraction = (++i).toDouble() / total
                    }
                }

                if (runAnalyzer) {
                    project.afterTypechecking(listOf(file))
                }

                modificationTracker.incModificationCount()
                file.lastDefinitionModification.updateAndGet { maxOf(it, modificationTracker.modificationCount) }
                invokeLater {
                    FileDocumentManager.getInstance().reloadBinaryFiles()
                }
                return
            }
        }.queue()
    }

    private fun typecheckDefinition(typechecking: ArendTypechecking, element: CollectingOrderingListener.Element): Boolean {
        val indicator = ModificationCancellationIndicator(modificationTracker, modificationCount)
        val def = element.anyDefinition.data
        val ok = definitionBlackListService.runTimed(def, indicator) {
            typechecking.run(indicator) {
                if (element.allDefinitions.any { it !is TCDefReferable || it.typechecked == null || it.typechecked?.status()?.needsTypeChecking() == true }) {
                    element.feedTo(typechecking)
                }
                true
            }
        }

        if (ok == null) {
            NotificationErrorReporter(project).warn("Typechecking of ${FullName(def)} was interrupted after ${service<ArendSettings>().typecheckingTimeLimit} second(s)")
        }

        return ok == true
    }

    private fun runDumbTypechecker(def: Concrete.ResolvableDefinition) {
        DesugarVisitor.desugar(def, errorService)
        def.accept(DumbTypechecker(errorService), null)
    }
}
