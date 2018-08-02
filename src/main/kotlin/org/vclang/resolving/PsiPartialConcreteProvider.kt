package org.vclang.resolving

import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.Reference
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.PartialConcreteProvider
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcDefData
import org.vclang.psi.VcDefFunction
import org.vclang.psi.VcDefInstance
import org.vclang.psi.ext.PsiLocatedReferable


object PsiPartialConcreteProvider : PartialConcreteProvider {
    override fun getInstanceTypeReference(instance: GlobalReferable): Reference? =
        (PsiLocatedReferable.fromReferable(instance) as? VcDefInstance)?.let { getInstanceReference(it) }

    override fun isRecord(classRef: ClassReferable) = (PsiLocatedReferable.fromReferable(classRef) as? VcDefClass)?.recordKw != null

    private fun getInstanceReference(instance: VcDefInstance): Reference? =
        instance.argumentAppExpr?.let { argumentAppExpr ->
            argumentAppExpr.longNameExpr?.longName ?: argumentAppExpr.atomFieldsAcc?.let { it.atom.literal?.longName }
        }

    override fun isInstance(ref: GlobalReferable) = PsiLocatedReferable.fromReferable(ref) is VcDefInstance

    override fun isCoerce(ref: GlobalReferable) = (PsiLocatedReferable.fromReferable(ref) as? VcDefFunction)?.coerceKw != null

    override fun isData(ref: GlobalReferable) = PsiLocatedReferable.fromReferable(ref) is VcDefData
}