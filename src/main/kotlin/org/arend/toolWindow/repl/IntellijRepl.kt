package org.arend.toolWindow.repl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.arend.error.ListErrorReporter
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ArendRawLibrary
import org.arend.module.FullModulePath
import org.arend.module.config.ArendModuleConfigService
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.reference.converter.SimpleReferableConverter
import org.arend.prelude.Prelude
import org.arend.psi.ArendPsiFactory
import org.arend.refactoring.LocatedReferableConverter
import org.arend.repl.Repl
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.group.Group
import org.arend.typechecking.ArendLibraryResolver
import org.arend.typechecking.SimpleTypecheckerState
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.instance.provider.InstanceProviderSet
import java.util.*

abstract class IntellijRepl private constructor(
    private val project: Project,
    private val refConverter: ReferableConverter,
    errorReporter: ListErrorReporter
) : Repl(
    errorReporter,
    ArendLibraryResolver(project),
    PsiConcreteProvider(project, refConverter, errorReporter, null, false),
    PsiElementComparator,
    TreeSet(),
    ArendRawLibrary(ReplLibraryConfig("Repl", project)),
    InstanceProviderSet(),
    SimpleTypecheckerState()
) {
    companion object {
        private fun referableConverter(project: Project) =
            LocatedReferableConverter(ArendReferableConverter(project, SimpleReferableConverter()))
    }

    constructor(project: Project) : this(project, referableConverter(project), ListErrorReporter())

    private val psiFactory = ArendPsiFactory(project)
    fun loadModuleLibrary(module: Module) =
        loadLibrary(ArendRawLibrary(ArendModuleConfigService.getConfig(module)))

    override fun parseStatements(line: String): Group? = psiFactory.createFromText(line)
    override fun parseExpr(text: String) = psiFactory.createExpressionMaybe(text)
        ?.let { ConcreteBuilder.convertExpression(refConverter, it) }

    final override fun loadPreludeLibrary() {
        val preludeLibrary = ArendPreludeLibrary(project, myTypecheckerState)
        if (!loadLibrary(preludeLibrary)) {
            eprintln("[FATAL] Failed to load Prelude")
            return
        }
        preludeLibrary.prelude?.generatedModulePath = FullModulePath(Prelude.LIBRARY_NAME, FullModulePath.LocationKind.GENERATED, Prelude.MODULE_PATH.toList())
        val scope = preludeLibrary.prelude?.groupScope
        if (scope != null) myMergedScopes.add(scope)
        else eprintln("[FATAL] Failed to obtain prelude scope")
    }
}
