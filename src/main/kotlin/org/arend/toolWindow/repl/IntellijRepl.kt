package org.arend.toolWindow.repl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.arend.core.expr.Expression
import org.arend.error.DummyErrorReporter
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.error.ListErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.library.LibraryDependency
import org.arend.module.config.LibraryConfig
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.naming.scope.ScopeFactory
import org.arend.psi.ArendPsiFactory
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.repl.Repl
import org.arend.settings.ArendProjectSettings
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.group.ConcreteGroup
import org.arend.toolWindow.repl.action.SetPromptCommand
import org.arend.toolWindow.repl.action.ShowContextCommandIntellij
import org.arend.typechecking.*
import org.arend.typechecking.computation.ComputationRunner
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.order.Ordering
import org.arend.typechecking.order.dependency.DummyDependencyListener
import org.arend.typechecking.order.listener.CollectingOrderingListener
import org.arend.typechecking.provider.ConcreteProvider
import org.arend.typechecking.result.TypecheckingResult
import java.lang.StringBuilder
import java.util.function.Consumer

abstract class IntellijRepl private constructor(
    val handler: ArendReplExecutionHandler,
    service: TypeCheckingService,
    extensionProvider: LibraryArendExtensionProvider,
    errorReporter: ListErrorReporter,
    psiConcreteProvider: ConcreteProvider,
) : Repl(
    errorReporter,
    service.libraryManager,
    ArendTypechecking(service, psiConcreteProvider, errorReporter, DummyDependencyListener.INSTANCE, extensionProvider),
) {
    constructor(
        handler: ArendReplExecutionHandler,
        project: Project,
    ) : this(handler, project.service(), ListErrorReporter())

    private constructor(
        handler: ArendReplExecutionHandler,
        service: TypeCheckingService,
        errorReporter: ListErrorReporter,
    ) : this(
        handler,
        service,
        LibraryArendExtensionProvider(service.libraryManager),
        errorReporter,
        ConcreteProvider.EMPTY // TODO[server2]: PsiConcreteProvider(service.project, errorReporter, null, true)
    )

    private val project = service.project
    private val definitionModificationTracker = service<ArendPsiChangeService>().definitionModificationTracker
    private val psiFactory = ArendPsiFactory(project, replModulePath.libraryName)
    override fun parseStatements(line: String): ConcreteGroup? = psiFactory.createFromText(line)
        ?.let { ConcreteBuilder.convertGroup(it, it.moduleLocation, DummyErrorReporter.INSTANCE) }
    override fun parseExpr(text: String) = psiFactory.createExpressionMaybe(text)
        ?.let { ConcreteBuilder.convertExpression(it) }

    fun clearScope() {
        myMergedScopes.clear()
    }

    override fun getPrettyPrinterFlags() = project.service<ArendProjectSettings>().replPrintingOptionsFilterSet

    override fun getNormalizationMode(): NormalizationMode? {
        val modeString = project.service<ArendProjectSettings>().data.replNormalizationMode
        return if (modeString == "NULL") null else NormalizationMode.valueOf(modeString)
    }

    override fun setNormalizationMode(mode: NormalizationMode?) {
        project.service<ArendProjectSettings>().data.replNormalizationMode = mode?.toString() ?: "NULL"
    }

    override fun loadCommands() {
        super.loadCommands()
        registerAction("prompt", SetPromptCommand)
        registerAction("show_context", ShowContextCommandIntellij)
        val arendFile = handler.arendFile
        arendFile.enforcedScope = ::resetCurrentLineScope
        arendFile.enforcedLibraryConfig = myLibraryConfig
        resetCurrentLineScope()
    }

    final override fun loadLibraries() {
        val service = project.service<TypeCheckingService>()
        if (service.initialize()) println("[INFO] Initialized prelude.")
        val prelude = service.preludeScope.also(myReplScope::addPreludeScope)
        if (prelude.elements.isEmpty()) eprintln("[FATAL] Failed to obtain prelude scope")
    }

    fun resetCurrentLineScope(): Scope {
        /* TODO[server2]
        val scope = ScopeFactory.forGroup(handler.arendFile, availableModuleScopeProvider)
        myReplScope.setCurrentLineScope(CachingScope.make(scope))
        */
        return myScope
    }

    private val myLibraryConfig = object : LibraryConfig(project) {
        override val name: String get() = replModulePath.libraryName
        override val root: VirtualFile? get() = null
        override val dependencies: List<LibraryDependency>
            get() = myLibraryManager.registeredLibraries.map { LibraryDependency(it.name) }
        override val modules: List<ModulePath>
            get() = service.updatedModules.map { it.modulePath }
    }

    override fun checkExpr(expr: Concrete.Expression, expectedType: Expression?, continuation: Consumer<TypecheckingResult>) {
        definitionModificationTracker.incModificationCount()
        val collector = CollectingOrderingListener()
        Ordering(typechecking.instanceScopeProvider, typechecking.concreteProvider, collector, DummyDependencyListener.INSTANCE, PsiElementComparator, errorReporter).orderExpression(expr)
        ApplicationManager.getApplication().executeOnPooledThread {
            ComputationRunner<Unit>().run(ModificationCancellationIndicator(definitionModificationTracker)) {
                typechecking.typecheckCollected(collector, ModificationCancellationIndicator(definitionModificationTracker))
                super.checkExpr(expr, expectedType, continuation)
            }
        }
    }

    override fun checkErrors() = runReadAction {
        super.checkErrors()
    }

    override fun typecheckStatements(group: ConcreteGroup, scope: Scope) {
        definitionModificationTracker.incModificationCount()
        val collector = CollectingOrderingListener()
        Ordering(typechecking.instanceScopeProvider, typechecking.concreteProvider, collector, DummyDependencyListener.INSTANCE, PsiElementComparator, errorReporter).orderModule(group)
        ApplicationManager.getApplication().executeOnPooledThread {
            val ok = typechecking.typecheckCollected(collector, ModificationCancellationIndicator(definitionModificationTracker))
            runReadAction {
                if (!ok) {
                    checkErrors()
                    removeScope(scope)
                }
            }
        }
    }

    override fun prettyExpr(builder: StringBuilder, expression: Expression): StringBuilder =
        runReadAction { super.prettyExpr(builder, expression) }
}
