package org.arend.toolWindow.repl

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import org.arend.error.ListErrorReporter
import org.arend.module.ArendRawLibrary
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.psi.ArendPsiFactory
import org.arend.repl.Repl
import org.arend.resolving.PsiConcreteProvider
import org.arend.resolving.WrapperReferableConverter
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group
import org.arend.typechecking.SimpleTypecheckerState
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.instance.provider.InstanceProviderSet
import java.util.*

abstract class IntellijRepl private constructor(
    @JvmField protected val service: TypeCheckingService,
    @JvmField protected val libraryConfig: LibraryConfig,
    @JvmField protected val refConverter: ReferableConverter,
    errorReporter: ListErrorReporter
) : Repl(
    errorReporter,
    service.libraryManager,
    PsiConcreteProvider(service.project, refConverter, errorReporter, null, false),
    PsiElementComparator,
    TreeSet(),
    ArendRawLibrary(libraryConfig),
    InstanceProviderSet(),
    SimpleTypecheckerState()
) {
    constructor(module: Module) : this(
        module.project.service(),
        ArendModuleConfigService.getConfig(module),
        WrapperReferableConverter,
        ListErrorReporter()
    )

    private val psiFactory = ArendPsiFactory(service.project)

    override fun parseStatements(line: String): Group? = psiFactory.createFromText(line)
    override fun parseExpr(text: String): Concrete.Expression? = psiFactory.createExpressionMaybe(text)
        ?.let { ConcreteBuilder.convertExpression(refConverter, it) }
}
