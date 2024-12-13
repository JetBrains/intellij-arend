package org.arend.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.error.SourceInfo
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.*
import org.arend.prelude.Prelude
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.ArendCoClauseDef
import org.arend.psi.ext.ReferableBase
import org.arend.psi.ext.moduleTextRepresentationImpl
import org.arend.psi.ext.positionTextRepresentationImpl
import org.arend.term.group.AccessModifier
import org.arend.typechecking.TypeCheckingService


private data class Alias(val name: String, val precedence: Precedence)

open class DataLocatedReferable(
    private var psiElementPointer: SmartPsiElementPointer<PsiLocatedReferable>?,
    accessModifier: AccessModifier,
    referable: LocatedReferable,
    parent: LocatedReferable?
) : LocatedReferableImpl(accessModifier, if (referable is ArendCoClauseDef && referable.parentCoClause?.prec == null) null else referable.precedence, referable.textRepresentation(), parent, referable.kind), IntellijTCReferable, SourceInfo {

    private var alias = referable.aliasName?.let { Alias(it, referable.aliasPrecedence) }

    override fun getAliasName() = alias?.name

    override fun getAliasPrecedence(): Precedence = alias?.precedence ?: Precedence.DEFAULT

    override fun getData() = psiElementPointer

    override fun getPrecedence(): Precedence {
        if (isPrecedenceSet) {
            return super.getPrecedence()
        } else {
            val ref = underlyingReferable
            if (ref == this || ref !is ArendCoClauseDef) {
                return super.getPrecedence()
            }
            val prec = ref.prec ?: return super.getPrecedence()
            val result = ReferableBase.calcPrecedence(prec)
            precedence = result
            return result
        }
    }

    override fun isEquivalent(ref: LocatedReferable) =
        kind == ref.kind && precedence == ref.precedence && refName == ref.refName && aliasName == ref.aliasName && aliasPrecedence == ref.aliasPrecedence

    override val isConsistent: Boolean
        get() {
            val underlyingRef = psiElementPointer?.let { runReadAction { it.element } } as? LocatedReferable
            return underlyingRef != null && isEquivalent(underlyingRef)
        }

    override val displayName: String? = refLongName.toString()

    override fun getUnderlyingReferable() =
        psiElementPointer?.let { runReadAction { it.element }?.underlyingReferable } ?: this

    override fun moduleTextRepresentation(): String? =
        psiElementPointer?.let { runReadAction { it.element?.moduleTextRepresentationImpl() } } ?: location?.toString()

    override fun positionTextRepresentation(): String? =
        psiElementPointer?.let { runReadAction { it.element?.positionTextRepresentationImpl() } }

    fun setPointer(ref: PsiLocatedReferable) {
        psiElementPointer = SmartPointerManager.createPointer(ref)
    }

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
    accessModifier: AccessModifier,
    referable: FieldReferable,
    parent: LocatedReferable?
) : DataLocatedReferable(psiElementPointer, accessModifier, referable, parent), TCFieldReferable {

    private val isExplicit = referable.isExplicitField

    private val isParameter = referable.isParameterField

    override fun isExplicitField() = isExplicit

    override fun isParameterField() = isParameter

    override fun isEquivalent(ref: LocatedReferable) =
        ref is FieldReferable && isExplicitField == ref.isExplicitField && isParameterField == ref.isParameterField && super.isEquivalent(ref)
}
