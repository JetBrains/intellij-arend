package org.arend.toolWindow.repl

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.arend.error.ListErrorReporter
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ArendRawLibrary
import org.arend.module.config.ArendModuleConfigService
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.prelude.Prelude
import org.arend.psi.ArendPsiFactory
import org.arend.repl.Repl
import org.arend.resolving.PsiConcreteProvider
import org.arend.resolving.WrapperReferableConverter
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.group.Group
import org.arend.typechecking.SimpleTypecheckerState
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.instance.provider.InstanceProviderSet
import java.util.*

abstract class IntellijRepl private constructor(
    private val service: TypeCheckingService,
    private val refConverter: ReferableConverter,
    errorReporter: ListErrorReporter
) : Repl(
    errorReporter,
    service.libraryManager,
    PsiConcreteProvider(service.project, refConverter, errorReporter, null, false),
    PsiElementComparator,
    TreeSet(),
    ArendRawLibrary(ReplLibraryConfig("Repl", service.project)),
    InstanceProviderSet(),
    SimpleTypecheckerState()
) {
    constructor(project: Project) : this(
        project.service(),
        WrapperReferableConverter,
        ListErrorReporter()
    )

    private val psiFactory = ArendPsiFactory(service.project)
    fun loadModuleLibrary(module: Module) =
        loadLibrary(ArendRawLibrary(ArendModuleConfigService.getConfig(module)))

    override fun parseStatements(line: String): Group? = psiFactory.createFromText(line)
    override fun parseExpr(text: String) = psiFactory.createExpressionMaybe(text)
        ?.let { ConcreteBuilder.convertExpression(refConverter, it) }

    final override fun loadPreludeLibrary() {
        val preludeLibrary = ArendPreludeLibrary(service.project, myTypecheckerState)
        if (!loadLibrary(preludeLibrary)) {
            eprintln("[FATAL] Failed to load Prelude")
            return
        }
        val scope = preludeLibrary.moduleScopeProvider.forModule(Prelude.MODULE_PATH)
        if (scope != null) myMergedScopes.add(scope)
        else eprintln("[FATAL] Failed to obtain prelude scope")
    }
}
