package org.vclang.parser

import com.intellij.psi.PsiFile
import com.jetbrains.jetpad.vclang.error.CompositeErrorReporter
import com.jetbrains.jetpad.vclang.error.CountingErrorReporter
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.frontend.resolving.OneshotNameResolver
import com.jetbrains.jetpad.vclang.module.source.SourceId
import com.jetbrains.jetpad.vclang.naming.NameResolver
import com.jetbrains.jetpad.vclang.naming.scope.primitive.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.primitive.Scope
import com.jetbrains.jetpad.vclang.term.Abstract
import org.vclang.psi.VcFile
import org.vclang.resolve.SurrogateResolveListener

object AbstractTreeFactory {

    fun createFromPsiFile(
            sourceId: SourceId,
            file: PsiFile,
            errorReporter: ErrorReporter,
            nameResolver: NameResolver? = null,
            globalScope: Scope = EmptyScope()
    ): Abstract.ClassDefinition? {
        if (file !is VcFile) return null

        val countingErrorReporter = CountingErrorReporter()
        val compositeErrorReporter = CompositeErrorReporter(errorReporter, countingErrorReporter)
        val visitor = AbstractTreeBuildVisitor(sourceId, compositeErrorReporter)
        val module = visitor.visitModule(file)

        if (nameResolver != null) {
            OneshotNameResolver.visitModule(
                    module,
                    globalScope,
                    nameResolver,
                SurrogateResolveListener(),
                    compositeErrorReporter
            )
        }

        return if (countingErrorReporter.errorsNumber == 0) module else null
    }
}
