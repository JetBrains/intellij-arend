package org.arend.toolWindow.repl

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.error.ListErrorReporter
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.scope.ConvertingScope
import org.arend.psi.ArendPsiFactory
import org.arend.refactoring.LocatedReferableConverter
import org.arend.repl.Repl
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.group.Group
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.instance.provider.InstanceProviderSet

abstract class IntellijRepl private constructor(
    private val service: TypeCheckingService,
    private val refConverter: ReferableConverter,
    errorReporter: ListErrorReporter
) : Repl(
    errorReporter,
    service.libraryManager,
    PsiConcreteProvider(service.project, refConverter, errorReporter, null, false),
    PsiElementComparator,
    InstanceProviderSet(),
    service.typecheckerState
) {
    constructor(project: Project) : this(project.service(), ListErrorReporter())
    constructor(service: TypeCheckingService, errorReporter: ListErrorReporter) : this(
        service,
        LocatedReferableConverter(service.newReferableConverter(false)),
        errorReporter
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
