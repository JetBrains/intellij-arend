package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.core.definition.Definition
import org.arend.ext.error.ErrorReporter
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.order.dependency.DependencyListener
import org.arend.typechecking.order.listener.TypecheckingOrderingListener
import org.arend.typechecking.provider.ConcreteProvider


open class ArendTypechecking(instanceProviderSet: PsiInstanceProviderSet, concreteProvider: ConcreteProvider, errorReporter: ErrorReporter, dependencyListener: DependencyListener, extensionProvider: ArendExtensionProvider)
    : TypecheckingOrderingListener(instanceProviderSet, concreteProvider, ArendReferableConverter, errorReporter, dependencyListener, PsiElementComparator, extensionProvider) {

    companion object {
        fun create(project: Project, concreteProvider: ConcreteProvider? = null): ArendTypechecking {
            val typecheckingService = project.service<TypeCheckingService>()
            val errorReporter = project.service<ErrorService>()
            return ArendTypechecking(PsiInstanceProviderSet(), concreteProvider ?: PsiConcreteProvider(project, errorReporter, null, true), errorReporter, typecheckingService.dependencyListener, LibraryArendExtensionProvider(typecheckingService.libraryManager))
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

    override fun typecheckingUnitFinished(referable: TCDefReferable, definition: Definition) {
        typecheckingFinished(PsiLocatedReferable.fromReferable(referable) ?: return, definition)
    }

    override fun typecheckingBodyFinished(referable: TCDefReferable, definition: Definition) {
        typecheckingFinished(PsiLocatedReferable.fromReferable(referable) ?: return, definition)
    }
}