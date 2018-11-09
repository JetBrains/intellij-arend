package org.arend.resolving

import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Reference
import org.arend.psi.ArendDefClass
import org.arend.psi.ArendDefData
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendDefInstance
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.typechecking.typecheckable.provider.PartialConcreteProvider


object PsiPartialConcreteProvider : PartialConcreteProvider {
    override fun getInstanceTypeReference(instance: GlobalReferable): Reference? =
        (PsiLocatedReferable.fromReferable(instance) as? ArendDefInstance)?.let { getInstanceReference(it) }

    override fun isRecord(classRef: ClassReferable) = (PsiLocatedReferable.fromReferable(classRef) as? ArendDefClass)?.recordKw != null

    private fun getInstanceReference(instance: ArendDefInstance): Reference? =
        instance.argumentAppExpr?.let { argumentAppExpr ->
            argumentAppExpr.longNameExpr?.longName ?: argumentAppExpr.atomFieldsAcc?.let { it.atom.literal?.longName }
        }

    override fun isInstance(ref: GlobalReferable) = PsiLocatedReferable.fromReferable(ref) is ArendDefInstance

    override fun isUse(ref: GlobalReferable) = (PsiLocatedReferable.fromReferable(ref) as? ArendDefFunction)?.useKw != null

    override fun isData(ref: GlobalReferable) = PsiLocatedReferable.fromReferable(ref) is ArendDefData
}