package org.arend.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.error.SourceInfo
import org.arend.naming.reference.*
import org.arend.prelude.Prelude
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.moduleTextRepresentationImpl
import org.arend.psi.ext.positionTextRepresentationImpl
import org.arend.typechecking.TypeCheckingService


open class DataLocatedReferable(
    private var psiElementPointer: SmartPsiElementPointer<PsiElement>?,
    referable: LocatedReferable,
    parent: LocatedReferable?,
    typeClassReference: TCClassReferable?)
    : DataLocatedReferableImpl(referable.precedence, referable.textRepresentation(), parent, typeClassReference, referable.kind), SourceInfo {

    override fun getData() = psiElementPointer

    override fun getUnderlyingReferable() =
        psiElementPointer?.let { (runReadAction { it.element } as? Referable)?.underlyingReferable } ?: this

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
    psiElementPointer: SmartPsiElementPointer<PsiElement>?,
    referable: FieldReferable,
    parent: LocatedReferable?,
    typeClassReference: TCClassReferable?)
    : DataLocatedReferable(psiElementPointer, referable, parent, typeClassReference), TCFieldReferable {

    private val isExplicit = referable.isExplicitField

    private val isParameter = referable.isParameterField

    override fun isExplicitField() = isExplicit

    override fun isParameterField() = isParameter
}

class ClassDataLocatedReferable(
    psiElementPointer: SmartPsiElementPointer<PsiElement>?,
    referable: LocatedReferable,
    parent: LocatedReferable?,
    var isRecordFlag: Boolean,
    val superClasses: MutableList<TCClassReferable>,
    val fieldReferables: MutableList<TCFieldReferable>,
    val implementedFields: MutableList<TCReferable>)
    : DataLocatedReferable(psiElementPointer, referable, parent, null), TCClassReferable {

    var filledIn = false

    override fun isRecord() = isRecordFlag

    override fun getSuperClassReferences(): List<TCClassReferable> = superClasses

    override fun getFieldReferables(): Collection<TCFieldReferable> = fieldReferables

    override fun getImplementedFields(): Collection<TCReferable> = implementedFields

    override fun getUnresolvedSuperClassReferences(): List<Reference> = emptyList()
}
