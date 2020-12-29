package org.arend.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.error.SourceInfo
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.*
import org.arend.prelude.Prelude
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.impl.CoClauseDefAdapter
import org.arend.psi.ext.moduleTextRepresentationImpl
import org.arend.psi.ext.positionTextRepresentationImpl
import org.arend.typechecking.TypeCheckingService


private data class Alias(val name: String, val precedence: Precedence)

open class DataLocatedReferable(
    private var psiElementPointer: SmartPsiElementPointer<PsiLocatedReferable>?,
    referable: LocatedReferable,
    parent: LocatedReferable?
) : LocatedReferableImpl(if (referable is CoClauseDefAdapter && referable.parentCoClause?.prec == null) null else referable.precedence, referable.textRepresentation(), parent, referable.kind), SourceInfo {

    private var alias = referable.aliasName?.let { Alias(it, referable.aliasPrecedence) }

    override fun getAliasName() = alias?.name

    override fun getAliasPrecedence(): Precedence = alias?.precedence ?: Precedence.DEFAULT

    override fun getData() = psiElementPointer

    override fun getUnderlyingReferable() =
        psiElementPointer?.let { runReadAction { it.element }?.underlyingReferable } ?: this

    override fun moduleTextRepresentation(): String? =
        psiElementPointer?.let { runReadAction { it.element?.moduleTextRepresentationImpl() } } ?: location?.toString()

    override fun positionTextRepresentation(): String? =
        psiElementPointer?.let { runReadAction { it.element?.positionTextRepresentationImpl() } }

    fun fixPointer(project: Project) =
        if (psiElementPointer == null) {
            runReadAction {
                LocatedReferable.Helper.resolveReferable(this) { modulePath ->
                    if (modulePath == Prelude.MODULE_PATH) {
                        project.service<TypeCheckingService>().libraryManager.getRegisteredLibrary(Prelude.LIBRARY_NAME)?.moduleScopeProvider?.forModule(modulePath)
                    } else null
                } as? PsiLocatedReferable
            }
        } else null
        /* TODO
        runReadAction {
            val psiRef = LocatedReferable.Helper.resolveReferable(this, TypeCheckingService.getInstance(project).libraryManager.getAvailableModuleScopeProvider()) as? PsiLocatedReferable
            if (psiElementPointer != null && psiRef != null) {
                psiElementPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(psiRef)
            }
            psiRef
        }
        */
}

class FieldDataLocatedReferable(
    psiElementPointer: SmartPsiElementPointer<PsiLocatedReferable>?,
    referable: FieldReferable,
    parent: LocatedReferable?
) : DataLocatedReferable(psiElementPointer, referable, parent), TCFieldReferable {

    private val isExplicit = referable.isExplicitField

    private val isParameter = referable.isParameterField

    override fun isExplicitField() = isExplicit

    override fun isParameterField() = isParameter
}
