package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.core.definition.Definition
import org.arend.error.ErrorReporter
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.resolving.PsiConcreteProvider
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.order.dependency.DependencyListener
import org.arend.typechecking.order.listener.TypecheckingOrderingListener
import org.arend.typechecking.typecheckable.provider.ConcreteProvider


open class ArendTypechecking(instanceProviderSet: PsiInstanceProviderSet, typeCheckingService: TypeCheckingService, concreteProvider: ConcreteProvider, referableConverter: ReferableConverter, errorReporter: ErrorReporter, dependencyListener: DependencyListener)
    : TypecheckingOrderingListener(instanceProviderSet, typeCheckingService.typecheckerState, concreteProvider, referableConverter, errorReporter, dependencyListener, PsiElementComparator) {

    companion object {
        fun create(project: Project, errorReporter: ErrorReporter): ArendTypechecking {
            val typecheckingService = project.service<TypeCheckingService>()
            val referableConverter = typecheckingService.newReferableConverter(true)
            val concreteProvider = PsiConcreteProvider(project, referableConverter, errorReporter, null, true)
            return ArendTypechecking(PsiInstanceProviderSet(concreteProvider, referableConverter), typecheckingService, concreteProvider, referableConverter, errorReporter, typecheckingService.dependencyListener)
        }
    }

    protected open fun typecheckingFinished(ref: PsiLocatedReferable, definition: Definition) {
        if (definition.status() == Definition.TypeCheckingStatus.NO_ERRORS) {
            runReadAction {
                val file = ref.containingFile as? ArendFile ?: return@runReadAction
                file.project.service<BinaryFileSaver>().addToQueue(file, referableConverter)
            }
        }
    }

    override fun typecheckingUnitFinished(referable: TCReferable, definition: Definition) {
        typecheckingFinished(PsiLocatedReferable.fromReferable(referable) ?: return, definition)
    }

    override fun typecheckingBodyFinished(referable: TCReferable, definition: Definition) {
        typecheckingFinished(PsiLocatedReferable.fromReferable(referable) ?: return, definition)
    }
}