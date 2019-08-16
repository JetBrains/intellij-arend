package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import org.arend.core.definition.Definition
import org.arend.editor.ArendOptions
import org.arend.error.ErrorReporter
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.resolving.PsiConcreteProvider
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.order.dependency.DependencyListener
import org.arend.typechecking.order.listener.TypecheckingOrderingListener
import org.arend.typechecking.typecheckable.provider.ConcreteProvider
import org.arend.util.FullName


open class SilentTypechecking(instanceProviderSet: PsiInstanceProviderSet, private val typeCheckingService: TypeCheckingService, concreteProvider: ConcreteProvider, referableConverter: ReferableConverter, errorReporter: ErrorReporter, dependencyListener: DependencyListener)
    : TypecheckingOrderingListener(instanceProviderSet, typeCheckingService.typecheckerState, concreteProvider, referableConverter, errorReporter, dependencyListener, PsiElementComparator) {

    companion object {
        fun create(project: Project): SilentTypechecking {
            val service = TypeCheckingService.getInstance(project)
            val referableConverter = service.newReferableConverter(true)
            val concreteProvider = PsiConcreteProvider(project, referableConverter, service, null, true)
            return SilentTypechecking(PsiInstanceProviderSet(concreteProvider, referableConverter), service, concreteProvider, referableConverter, service, service.dependencyListener)
        }
    }

    protected open fun typecheckingFinished(ref: PsiLocatedReferable, definition: Definition) {
        if (!definition.status().isOK) {
            val status = when {
                !definition.status().headerIsOK() -> Definition.TypeCheckingStatus.HEADER_HAS_ERRORS
                definition.status() == Definition.TypeCheckingStatus.HAS_WARNINGS || definition.status() == Definition.TypeCheckingStatus.MAY_BE_TYPE_CHECKED_WITH_WARNINGS -> Definition.TypeCheckingStatus.MAY_BE_TYPE_CHECKED_WITH_WARNINGS
                else -> Definition.TypeCheckingStatus.MAY_BE_TYPE_CHECKED_WITH_ERRORS
            }
            definition.setStatus(status)
        }
        if (definition.status() == Definition.TypeCheckingStatus.NO_ERRORS) {
            runReadAction {
                val file = ref.containingFile as? ArendFile ?: return@runReadAction
                ServiceManager.getService(file.project, BinaryFileSaver::class.java).addToQueue(file, referableConverter)
            }
        }
    }

    override fun typecheckingUnitFinished(referable: TCReferable, definition: Definition) {
        typecheckingFinished(PsiLocatedReferable.fromReferable(referable) ?: return, definition)
    }

    override fun typecheckingBodyFinished(referable: TCReferable, definition: Definition) {
        typecheckingFinished(PsiLocatedReferable.fromReferable(referable) ?: return, definition)
    }

    override fun typecheckingInterrupted(definition: TCReferable, typechecked: Definition?) {
        NotificationErrorReporter(typeCheckingService.project).warn("Typechecking of ${FullName(definition)} was interrupted after ${ArendOptions.instance.typecheckingTimeLimit} second(s)")
    }
}