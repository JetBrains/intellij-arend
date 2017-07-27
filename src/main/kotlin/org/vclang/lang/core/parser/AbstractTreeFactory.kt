package org.vclang.lang.core.parser

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jetpad.vclang.error.CompositeErrorReporter
import com.jetbrains.jetpad.vclang.error.CountingErrorReporter
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.frontend.namespace.ModuleRegistry
import com.jetbrains.jetpad.vclang.frontend.resolving.OneshotNameResolver
import com.jetbrains.jetpad.vclang.module.source.SourceId
import com.jetbrains.jetpad.vclang.naming.NameResolver
import com.jetbrains.jetpad.vclang.naming.scope.primitive.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.primitive.Scope
import com.jetbrains.jetpad.vclang.term.Abstract
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcStatements
import org.vclang.lang.core.resolve.SurrogateResolveListener

object AbstractTreeFactory {

    fun createFromPsiFile(
            sourceId: SourceId,
            file: PsiFile,
            errorReporter: ErrorReporter,
            nameResolver: NameResolver? = null,
            globalScope: Scope = EmptyScope(),
            moduleRegistry: ModuleRegistry? = null
    ): Abstract.ClassDefinition? {
        val countingErrorReporter = CountingErrorReporter()
        val compositeErrorReporter = CompositeErrorReporter(errorReporter, countingErrorReporter)

        val tree = PsiTreeUtil.getChildOfType(file, VcStatements::class.java) ?: return null
        val visitor = AbstractTreeBuildVisitor(sourceId, compositeErrorReporter)
        val statements = visitor.visitStatements(tree)
        val result = Surrogate.ClassDefinition(
                Surrogate.Position(sourceId, 0, 0),
                sourceId.modulePath.name,
                statements
        )

        moduleRegistry?.registerModule(sourceId.modulePath, result)
        if (nameResolver != null) {
            OneshotNameResolver.visitModule(
                    result,
                    globalScope,
                    nameResolver,
                    SurrogateResolveListener(),
                    compositeErrorReporter
            )
        }
        if (countingErrorReporter.errorsNumber > 0) {
            moduleRegistry?.unregisterModule(sourceId.modulePath)
            return null
        }

        return result
    }
}
