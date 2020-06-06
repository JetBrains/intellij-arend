package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.ArendFile
import org.arend.psi.ext.TCDefinition
import org.arend.psi.ext.impl.ArendGroup
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group

abstract class BaseGroupPass(file: ArendFile, protected val group: ArendGroup, editor: Editor, name: String, textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : BasePass(file, editor, name, textRange, highlightInfoProcessor) {

    open fun visitDefinition(definition: Concrete.Definition, progress: ProgressIndicator) {}

    open fun visitDefinition(definition: LocatedReferable, progress: ProgressIndicator) {
        if (definition !is TCDefinition) {
            return
        }

        (file.concreteProvider.getConcrete(definition) as? Concrete.Definition)?.let { def ->
            visitDefinition(def, progress)
            progress.checkCanceled()
        }
        advanceProgress(1)
    }

    private fun visitGroup(group: ArendGroup, progress: ProgressIndicator) {
        visitDefinition(group.referable, progress)
        for (subgroup in group.subgroups) {
            visitGroup(subgroup, progress)
        }
        for (subgroup in group.dynamicSubgroups) {
            visitGroup(subgroup, progress)
        }
    }

    open fun collectInfo(progress: ProgressIndicator) {
        visitGroup(group, progress)
    }

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        setProgressLimit(numberOfDefinitions(group).toLong())
        collectInfo(progress)
    }

    protected open fun countDefinition(def: TCDefinition) = true

    protected open fun numberOfDefinitions(group: Group): Int {
        val def = group.referable
        var res = if (def is TCDefinition && countDefinition(def)) 1 else 0

        for (subgroup in group.subgroups) {
            res += numberOfDefinitions(subgroup)
        }
        for (subgroup in group.dynamicSubgroups) {
            res += numberOfDefinitions(subgroup)
        }
        return res
    }
}