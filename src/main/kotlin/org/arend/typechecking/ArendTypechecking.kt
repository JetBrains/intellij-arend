package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.core.definition.Definition
import org.arend.ext.error.ErrorReporter
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.resolving.PsiConcreteProvider
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.order.dependency.DependencyListener
import org.arend.typechecking.order.listener.TypecheckingOrderingListener
import org.arend.typechecking.provider.ConcreteProvider


open class ArendTypechecking(instanceProviderSet: PsiInstanceProviderSet, typecheckerState: TypecheckerState, concreteProvider: ConcreteProvider, referableConverter: ReferableConverter, errorReporter: ErrorReporter, dependencyListener: DependencyListener)
    : TypecheckingOrderingListener(instanceProviderSet, typecheckerState, concreteProvider, referableConverter, errorReporter, dependencyListener, PsiElementComparator, ArendTypecheckingListener) {

    companion object {
        fun create(project: Project, typecheckerState: TypecheckerState? = null): ArendTypechecking {
            val typecheckingService = project.service<TypeCheckingService>()
            val referableConverter = typecheckingService.newReferableConverter(true)
            val errorReporter = project.service<ErrorService>()
            val concreteProvider = PsiConcreteProvider(project, referableConverter, errorReporter, null, true)
            return ArendTypechecking(PsiInstanceProviderSet(concreteProvider, referableConverter), typecheckerState ?: typecheckingService.typecheckerState, concreteProvider, referableConverter, errorReporter, typecheckingService.dependencyListener)
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