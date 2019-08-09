package org.arend.typechecking

import com.intellij.openapi.project.Project
import org.arend.error.ErrorReporter
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.order.listener.TypecheckingOrderingListener
import org.arend.typechecking.typecheckable.provider.ConcreteProvider


class SilentTypechecking private constructor(service: TypeCheckingService, concreteProvider: ConcreteProvider, referableConverter: ReferableConverter, errorReporter: ErrorReporter)
    : TypecheckingOrderingListener(PsiInstanceProviderSet(concreteProvider, referableConverter), service.typecheckerState, concreteProvider, referableConverter, errorReporter, PsiElementComparator) {

    private constructor(project: Project, service: TypeCheckingService, referableConverter: ReferableConverter, errorReporter: ErrorReporter) : this(service, PsiConcreteProvider(project, referableConverter, service, null, true), referableConverter, errorReporter)
    private constructor(project: Project, service: TypeCheckingService, errorReporter: ErrorReporter) : this(project, service, service.newReferableConverter(true), errorReporter)
    private constructor(project: Project, service: TypeCheckingService) : this(project, service, service.newReferableConverter(true), service)
    constructor(project: Project, errorReporter: ErrorReporter) : this(project, TypeCheckingService.getInstance(project), errorReporter)
    constructor(project: Project) : this(project, TypeCheckingService.getInstance(project))
}