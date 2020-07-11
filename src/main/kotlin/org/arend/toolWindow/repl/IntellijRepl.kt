package org.arend.toolWindow.repl

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.ext.error.ListErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.library.LibraryDependency
import org.arend.module.config.LibraryConfig
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.naming.scope.ScopeFactory
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.repl.Repl
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.group.Group
import org.arend.toolWindow.repl.action.SetPromptCommand
import org.arend.typechecking.ArendTypechecking
import org.arend.typechecking.LibraryArendExtensionProvider
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.order.dependency.DummyDependencyListener

abstract class IntellijRepl private constructor(
    val handler: ArendReplExecutionHandler,
    private val service: TypeCheckingService,
    extensionProvider: LibraryArendExtensionProvider,
    errorReporter: ListErrorReporter,
    psiConcreteProvider: PsiConcreteProvider,
    psiInstanceProviderSet: PsiInstanceProviderSet
) : Repl(
    errorReporter,
    service.libraryManager,
    ArendTypechecking(psiInstanceProviderSet, psiConcreteProvider, errorReporter, DummyDependencyListener.INSTANCE, extensionProvider)
) {
    constructor(
        handler: ArendReplExecutionHandler,
        project: Project
    ) : this(handler, project.service(), ListErrorReporter())

    private constructor(
        handler: ArendReplExecutionHandler,
        service: TypeCheckingService,
        errorReporter: ListErrorReporter
    ) : this(
        handler,
        service,
        LibraryArendExtensionProvider(service.libraryManager),
        errorReporter,
        PsiConcreteProvider(service.project, errorReporter, null, true),
        PsiInstanceProviderSet(PsiConcreteProvider(service.project, errorReporter, null, false))
    )

    private val psiFactory = ArendPsiFactory(service.project, replModulePath.libraryName)
    override fun parseStatements(line: String): Group? = psiFactory.createFromText(line)
    override fun parseExpr(text: String) = psiFactory.createExpressionMaybe(text)
        ?.let { ConcreteBuilder.convertExpression(it) }

    fun clearScope() {
        myMergedScopes.clear()
    }

    override fun loadCommands() {
        super.loadCommands()
        registerAction("prompt", SetPromptCommand)
    }

    final override fun loadLibraries() {
        if (service.initialize()) println("[INFO] Initialized prelude.")
        val prelude = service.preludeScope.also { myReplScope.addPreludeScope(it) }
        if (prelude.elements.isEmpty()) eprintln("[FATAL] Failed to obtain prelude scope")
    }

    fun withArendFile(arendFile: ArendFile) {
        arendFile.enforcedScope = { resetCurrentLineScope(arendFile) }
        arendFile.enforcedLibraryConfig = myLibraryConfig
        resetCurrentLineScope(arendFile)
    }

    fun resetCurrentLineScope(arendFile: ArendFile): Scope {
        val scope = ScopeFactory.forGroup(arendFile, availableModuleScopeProvider)
        myReplScope.setCurrentLineScope(CachingScope.makeWithModules(scope))
        return myScope
    }

    private val myLibraryConfig = object : LibraryConfig(service.project) {
        override val name: String get() = replModulePath.libraryName
        override val rootDir: String? get() = null
        override val dependencies: List<LibraryDependency>
            get() = myLibraryManager.registeredLibraries.map { LibraryDependency(it.name) }
        override val modules: List<ModulePath>
            get() = service.updatedModules.map { it.modulePath }
    }
}
