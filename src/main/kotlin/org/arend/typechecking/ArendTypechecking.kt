package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.core.definition.Definition
import org.arend.ext.error.ErrorReporter
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.instance.provider.InstanceScopeProvider
import org.arend.typechecking.order.dependency.DependencyListener
import org.arend.typechecking.order.listener.TypecheckingOrderingListener
import org.arend.typechecking.provider.ConcreteProvider


open class ArendTypechecking(protected val typeCheckingService: TypeCheckingService, concreteProvider: ConcreteProvider, errorReporter: ErrorReporter, dependencyListener: DependencyListener, extensionProvider: ArendExtensionProvider)
    : TypecheckingOrderingListener(InstanceScopeProvider.EMPTY, concreteProvider, errorReporter, dependencyListener, PsiElementComparator, extensionProvider) {

    companion object {
        fun create(project: Project, concreteProvider: ConcreteProvider? = null, errorReporter: ErrorReporter = project.service<ErrorService>()): ArendTypechecking {
            val typecheckingService = project.service<TypeCheckingService>()
            return ArendTypechecking(typecheckingService, concreteProvider ?: ConcreteProvider.EMPTY, errorReporter, typecheckingService.dependencyListener, LibraryArendExtensionProvider(typecheckingService.libraryManager))
        }
    }

    protected open fun typecheckingFinished(ref: PsiLocatedReferable?, definition: Definition) {
        typeCheckingService.typechecked(definition)
        if (ref == null) return
        runReadAction {
            val file = ref.containingFile as? ArendFile ?: return@runReadAction
            file.project.service<BinaryFileSaver>().addToQueue(file)
        }
    }

    override fun typecheckingUnitFinished(referable: TCDefReferable, definition: Definition) {
        typecheckingFinished(PsiLocatedReferable.fromReferable(referable), definition)
    }

    override fun typecheckingBodyFinished(referable: TCDefReferable, definition: Definition) {
        typecheckingFinished(PsiLocatedReferable.fromReferable(referable), definition)
    }
}