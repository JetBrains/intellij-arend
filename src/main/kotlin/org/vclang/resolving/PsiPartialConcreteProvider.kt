package org.vclang.resolving

import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.Reference
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.PartialConcreteProvider
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcDefInstance
import org.vclang.psi.ext.PsiLocatedReferable


object PsiPartialConcreteProvider : PartialConcreteProvider {
    override fun getInstanceClassReference(instance: GlobalReferable): Reference? = (PsiLocatedReferable.fromReferable(instance) as? VcDefInstance)?.let { getInstanceReference(it) }

    override fun isRecord(classRef: ClassReferable) = (PsiLocatedReferable.fromReferable(classRef) as? VcDefClass)?.recordKw != null

    fun getInstanceReference(instance: VcDefInstance): Reference? =
        instance.argumentAppExpr?.let { argumentAppExpr ->
            argumentAppExpr.longNameExpr?.longName ?: argumentAppExpr.atomFieldsAcc?.let { atomFieldsAcc ->
                if (atomFieldsAcc.fieldAccList.isEmpty()) atomFieldsAcc.atom.literal?.longName else null
            }
        }

    override fun isInstance(ref: GlobalReferable) = PsiLocatedReferable.fromReferable(ref) is VcDefInstance
}