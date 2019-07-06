package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.psi.ArendFile
import org.arend.psi.ext.impl.ArendGroup
import org.arend.term.concrete.Concrete
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.typecheckable.provider.ConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.typechecking.visitor.DumbTypechecker

class DumbTypecheckerPass(file: ArendFile, group: ArendGroup, editor: Editor, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BaseGroupPass(file, group, editor, "Arend dumb typechecker annotator", textRange, highlightInfoProcessor) {

    override fun visitDefinition(definition: Concrete.Definition, concreteProvider: ConcreteProvider, progress: ProgressIndicator) {
        val state = TypeCheckingService.getInstance(file.project).typecheckerState
        if (state.getTypechecked(definition.data) != null) {
            return
        }

        DesugarVisitor.desugar(definition, concreteProvider, this)
        progress.checkCanceled()
        definition.accept(DumbTypechecker(state, this), null)
    }
}
