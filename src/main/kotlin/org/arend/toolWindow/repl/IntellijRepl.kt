package org.arend.toolWindow.repl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.arend.core.expr.Expression
import org.arend.ext.error.ListErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.library.LibraryDependency
import org.arend.module.config.LibraryConfig
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ConvertingScope
import org.arend.naming.scope.Scope
import org.arend.naming.scope.ScopeFactory
import org.arend.psi.ArendPsiFactory
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.repl.Repl
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group
import org.arend.toolWindow.repl.action.SetPromptCommand
import org.arend.typechecking.*
import org.arend.typechecking.computation.ComputationRunner
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.order.Ordering
import org.arend.typechecking.order.dependency.DummyDependencyListener
import org.arend.typechecking.order.listener.CollectingOrderingListener
import org.arend.typechecking.result.TypecheckingResult
import java.lang.StringBuilder
import java.util.function.Consumer

abstract class IntellijRepl private constructor(
    val handler: ArendReplExecutionHandler,
    private val service: TypeCheckingService,
    extensionProvider: LibraryArendExtensionProvider,
    errorReporter: ListErrorReporter,
    psiConcreteProvider: PsiConcreteProvider,
) : Repl(
    errorReporter,
    service.libraryManager,
    ArendTypechecking(PsiInstanceProviderSet(), psiConcreteProvider, errorReporter, DummyDependencyListener.INSTANCE, extensionProvider),
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
        PsiConcreteProvider(service.project, errorReporter, null, true),
    )

    init {
        myScope = ConvertingScope(ArendReferableConverter, myScope)
    }

    private val definitionModificationTracker = service.project.service<ArendPsiChangeService>().definitionModificationTracker
    private val psiFactory = ArendPsiFactory(service.project, replModulePath.libraryName)
    override fun parseStatements(line: String): Group? = psiFactory.createFromText(line)
        ?.also { resetCurrentLineScope() }
    override fun parseExpr(text: String) = psiFactory.createExpressionMaybe(text)
        ?.let { ConcreteBuilder.convertExpression(it) }

    fun clearScope() {
        myMergedScopes.clear()
    }

    override fun loadCommands() {
        super.loadCommands()
        registerAction("prompt", SetPromptCommand)
        val arendFile = handler.arendFile
        arendFile.enforcedScope = ::resetCurrentLineScope
        arendFile.enforcedLibraryConfig = myLibraryConfig
        resetCurrentLineScope()
    }

    final override fun loadLibraries() {
        if (service.initialize()) println("[INFO] Initialized prelude.")
        val prelude = service.preludeScope.also(myReplScope::addPreludeScope)
        if (prelude.elements.isEmpty()) eprintln("[FATAL] Failed to obtain prelude scope")
    }

    fun resetCurrentLineScope(): Scope {
        val scope = ScopeFactory.forGroup(handler.arendFile, availableModuleScopeProvider)
        myReplScope.setCurrentLineScope(CachingScope.make(scope))
        return myScope
    }

    private val myLibraryConfig = object : LibraryConfig(service.project) {
        override val name: String get() = replModulePath.libraryName
        override val root: VirtualFile? get() = null
        override val dependencies: List<LibraryDependency>
            get() = myLibraryManager.registeredLibraries.map { LibraryDependency(it.name) }
        override val modules: List<ModulePath>
            get() = service.updatedModules.map { it.modulePath }
    }

    override fun checkExpr(expr: Concrete.Expression, expectedType: Expression?, continuation: Consumer<TypecheckingResult>) {
        definitionModificationTracker.incModificationCount()
        ApplicationManager.getApplication().executeOnPooledThread {
            ComputationRunner<Unit>().run(ModificationCancellationIndicator(definitionModificationTracker)) {
                super.checkExpr(expr, expectedType, continuation)
            }
        }
    }

    override fun typecheckStatements(group: Group, scope: Scope) {
        definitionModificationTracker.incModificationCount()
        val collector = CollectingOrderingListener()
        Ordering(myTypechecking.instanceProviderSet, myTypechecking.concreteProvider, collector, DummyDependencyListener.INSTANCE, myTypechecking.referableConverter, PsiElementComparator).orderModule(group)
        ApplicationManager.getApplication().executeOnPooledThread {
            val ok = myTypechecking.typecheckCollected(collector, ModificationCancellationIndicator(definitionModificationTracker))
            runReadAction {
                if (!ok) {
                    checkErrors()
                    removeScope(scope)
                }
                onScopeAdded(group)
            }
        }
    }

    override fun prettyExpr(builder: StringBuilder, expression: Expression): StringBuilder =
        runReadAction { super.prettyExpr(builder, expression) }
}
