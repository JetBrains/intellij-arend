package org.vclang.parser

import com.intellij.psi.PsiFile
import com.jetbrains.jetpad.vclang.error.CompositeErrorReporter
import com.jetbrains.jetpad.vclang.error.CountingErrorReporter
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.module.source.SourceId
import com.jetbrains.jetpad.vclang.naming.NameResolver
import com.jetbrains.jetpad.vclang.naming.resolving.GroupNameResolver
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.term.Group
import org.vclang.psi.VcFile

object AbstractTreeFactory {

    fun rebuildModule(
            sourceId: SourceId,
            file: PsiFile,
            errorReporter: ErrorReporter,
            nameResolver: NameResolver? = null,
            globalScope: Scope = EmptyScope.INSTANCE
    ): Group? {
        /* TODO[abstract]
        if (file !is VcFile) return null

        val countingErrorReporter = CountingErrorReporter()
        val compositeErrorReporter = CompositeErrorReporter(errorReporter, countingErrorReporter)
        val visitor = AbstractTreeBuildVisitor(sourceId, compositeErrorReporter)
        val module = visitor.visitModule(file)

        if (nameResolver != null) {
            GroupNameResolver(nameResolver, errorReporter, )
            OneshotNameResolver.visitModule(
                    module,
                    globalScope,
                    nameResolver,
                    SurrogateResolveListener(),
                    compositeErrorReporter
            )
        }

        return if (countingErrorReporter.errorsNumber == 0) module else null
        */
        return null
    }
}
