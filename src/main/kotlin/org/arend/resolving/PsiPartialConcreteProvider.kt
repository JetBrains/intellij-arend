package org.arend.resolving

import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Reference
import org.arend.psi.*
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.typechecking.typecheckable.provider.PartialConcreteProvider


object PsiPartialConcreteProvider : PartialConcreteProvider {
    override fun getInstanceTypeReference(instance: GlobalReferable): Reference? =
        (PsiLocatedReferable.fromReferable(instance) as? ArendDefInstance)?.let { getInstanceReference(it) }

    override fun isRecord(classRef: ClassReferable) = (PsiLocatedReferable.fromReferable(classRef) as? ArendDefClass)?.recordKw != null

    private fun getInstanceReference(instance: ArendDefInstance): Reference? {
        val returnExpr = instance.returnExpr
        val argumentAppExpr = returnExpr?.expr as? ArendArgumentAppExpr
        val atomFieldsAcc = argumentAppExpr?.atomFieldsAcc ?: returnExpr?.atomFieldsAccList?.firstOrNull()
        return if (atomFieldsAcc != null) atomFieldsAcc.atom.literal?.longName else argumentAppExpr?.longNameExpr?.longName
    }

    override fun isInstance(ref: GlobalReferable) = PsiLocatedReferable.fromReferable(ref) is ArendDefInstance

    override fun isUse(ref: GlobalReferable) = (PsiLocatedReferable.fromReferable(ref) as? ArendDefFunction)?.useKw != null

    override fun isData(ref: GlobalReferable) = PsiLocatedReferable.fromReferable(ref) is ArendDefData
}