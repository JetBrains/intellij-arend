package org.arend.toolWindow.repl

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.ext.error.ListErrorReporter
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.scope.ConvertingScope
import org.arend.psi.ArendPsiFactory
import org.arend.refactoring.LocatedReferableConverter
import org.arend.repl.Repl
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.group.Group
import org.arend.typechecking.ArendTypechecking
import org.arend.typechecking.LibraryArendExtensionProvider
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.order.dependency.DummyDependencyListener

abstract class IntellijRepl private constructor(
    private val service: TypeCheckingService,
    private val refConverter: ReferableConverter,
    errorReporter: ListErrorReporter,
    psiConcreteProvider: PsiConcreteProvider,
    psiInstanceProviderSet: PsiInstanceProviderSet
) : Repl(
    errorReporter,
    service.libraryManager,
    psiConcreteProvider,
    psiInstanceProviderSet,
    ArendTypechecking(psiInstanceProviderSet, service.typecheckerState, psiConcreteProvider, refConverter, errorReporter, DummyDependencyListener.INSTANCE, LibraryArendExtensionProvider(service.libraryManager)),
    service.typecheckerState
) {
    constructor(project: Project) : this(project.service(), ListErrorReporter())
    constructor(service: TypeCheckingService, errorReporter: ListErrorReporter) : this(
        service,
        LocatedReferableConverter(service.newReferableConverter(false)),
        errorReporter,
        PsiConcreteProvider(service.project, LocatedReferableConverter(service.newReferableConverter(false)), errorReporter, null, false),
        PsiInstanceProviderSet(PsiConcreteProvider(service.project, LocatedReferableConverter(service.newReferableConverter(false)), errorReporter, null, false), LocatedReferableConverter(service.newReferableConverter(false)))
    )

    init {
        myScope = ConvertingScope(refConverter, myScope)
    }

    private val psiFactory = ArendPsiFactory(service.project)
    override fun parseStatements(line: String): Group? = psiFactory.createFromText(line)
    override fun parseExpr(text: String) = psiFactory.createExpressionMaybe(text)
        ?.let { ConcreteBuilder.convertExpression(refConverter, it) }

    final override fun loadLibraries() {
        if (service.initialize()) println("[INFO] Initialized prelude.")
        val prelude = service.preludeScope
        myMergedScopes.add(prelude)
        if (prelude.elements.isEmpty()) eprintln("[FATAL] Failed to obtain prelude scope")
    }
}
