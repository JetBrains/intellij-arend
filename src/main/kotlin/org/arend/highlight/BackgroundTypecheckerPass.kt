package org.arend.highlight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.core.definition.Definition
import org.arend.psi.ArendFile
import org.arend.psi.ext.TCDefinition
import org.arend.psi.ext.impl.ArendGroup
import org.arend.settings.ArendSettings
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group
import org.arend.typechecking.*
import org.arend.typechecking.computation.DefinitionCancellationIndicator
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.provider.EmptyConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.typechecking.visitor.DumbTypechecker
import org.arend.util.FullName
import kotlin.concurrent.thread

class BackgroundTypecheckerPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BaseGroupPass(file, group, editor, "Arend background typechecker annotator", textRange, highlightInfoProcessor) {

    private val definitionBlackListService = service<DefinitionBlacklistService>()
    private val arendSettings = service<ArendSettings>()
    private val definitionsToTypecheck = ArrayList<TCDefinition>()
    var lastModification: Long = 0

    override fun visitDefinition(definition: Concrete.Definition, progress: ProgressIndicator) {
        DesugarVisitor.desugar(definition, this)

        progress.checkCanceled()

        definition.accept(DumbTypechecker(this), null)
    }

    private fun typecheckDefinition(typechecking: ArendTypechecking, definition: TCDefinition): Concrete.Definition? {
        val result = (typechecking.concreteProvider.getConcrete(definition) as? Concrete.Definition)?.let {
            val cancellationIndicator = DefinitionCancellationIndicator(it.data)
            val ok = definitionBlackListService.runTimed(definition, cancellationIndicator) {
                typechecking.typecheckDefinitions(listOf(it), cancellationIndicator)
            }

            if (!ok) {
                NotificationErrorReporter(myProject).warn("Typechecking of ${FullName(it.data)} was interrupted after ${arendSettings.typecheckingTimeLimit} second(s)")
                if (definitionsToTypecheck.isEmpty() || definitionsToTypecheck.last() != definition) {
                    DaemonCodeAnalyzer.getInstance(myProject).restart(file)
                }
            }

            it
        }

        advanceProgress(1)
        return result
    }

    override fun collectInfo(progress: ProgressIndicator) {
        when (arendSettings.typecheckingMode) {
            ArendSettings.TypecheckingMode.SMART -> if (definitionsToTypecheck.isNotEmpty() && !file.isReplFile) {
                val typechecking = ArendTypechecking.create(myProject, file.concreteProvider)
                val lastModified = file.lastModifiedDefinition
                if (lastModified != null) {
                    val typechecked = if (definitionsToTypecheck.remove(lastModified)) {
                        typecheckDefinition(typechecking, lastModified)?.data?.typechecked
                    } else null
                    if (typechecked?.status()?.withoutErrors() == true) {
                        file.lastModifiedDefinition = null
                        for (definition in definitionsToTypecheck) {
                            typecheckDefinition(typechecking, definition)
                        }
                    } else {
                        for (definition in definitionsToTypecheck) {
                            visitDefinition(definition, progress)
                        }
                    }
                } else {
                    for (definition in definitionsToTypecheck) {
                        typecheckDefinition(typechecking, definition)
                    }
                }
            }
            ArendSettings.TypecheckingMode.DUMB ->
                for (definition in definitionsToTypecheck) {
                    visitDefinition(definition, progress)
                }
            ArendSettings.TypecheckingMode.OFF -> {}
        }

        file.concreteProvider = EmptyConcreteProvider.INSTANCE
    }

    override fun applyInformationWithProgress() {
        synchronized(BackgroundTypecheckerPass::class.java) {
            if (file.lastDefinitionModification < lastModification) {
                file.lastDefinitionModification = lastModification
            }
        }
        super.applyInformationWithProgress()
        thread { DaemonCodeAnalyzer.getInstance(myProject).restart(file) }
    }

    override fun countDefinition(def: TCDefinition) =
        if (!definitionBlackListService.isBlacklisted(def) && def.tcReferable?.typechecked.let { it == null || it.status() == Definition.TypeCheckingStatus.TYPE_CHECKING }) {
            definitionsToTypecheck.add(def)
            true
        } else false

    override fun numberOfDefinitions(group: Group) =
        if (arendSettings.typecheckingMode == ArendSettings.TypecheckingMode.OFF) 0 else super.numberOfDefinitions(group)
}
