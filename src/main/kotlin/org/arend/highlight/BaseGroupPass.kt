package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.psi.*
import org.arend.psi.ext.impl.ArendGroup
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group
import org.arend.typechecking.typecheckable.provider.ConcreteProvider

abstract class BaseGroupPass(file: ArendFile, protected val group: ArendGroup, editor: Editor, name: String, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BasePass(file, editor, name, textRange, highlightInfoProcessor) {

    open fun visitDefinition(definition: Concrete.Definition, concreteProvider: ConcreteProvider, progress: ProgressIndicator) {}

    private fun visitGroup(group: ArendGroup, concreteProvider: ConcreteProvider, progress: ProgressIndicator) {
        (group.referable as? ArendDefinition)?.let {
            (concreteProvider.getConcrete(it) as? Concrete.Definition)?.let { def ->
                visitDefinition(def, concreteProvider, progress)
                progress.checkCanceled()
            }
            advanceProgress(1)
        }
        for (subgroup in group.subgroups) {
            visitGroup(subgroup, concreteProvider, progress)
        }
        for (subgroup in group.dynamicSubgroups) {
            visitGroup(subgroup, concreteProvider, progress)
        }
    }

    open fun collectInfo(progress: ProgressIndicator) {
        visitGroup(group, myProject.getComponent(ArendHighlightingPassFactory::class.java).concreteProvider, progress)
    }

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        setProgressLimit(numberOfDefinitions(group).toLong() - 1)
        collectInfo(progress)
    }

    companion object {
        private fun numberOfDefinitions(group: Group): Int {
            var res = if (group.referable is ArendDefinition) 1 else 0
            for (subgroup in group.subgroups) {
                res += numberOfDefinitions(subgroup)
            }
            for (subgroup in group.dynamicSubgroups) {
                res += numberOfDefinitions(subgroup)
            }
            return res
        }
    }
}