package com.jetbrains.arend.ide.resolving

import com.jetbrains.arend.ide.psi.ArdDefClass
import com.jetbrains.arend.ide.psi.ArdDefData
import com.jetbrains.arend.ide.psi.ArdDefFunction
import com.jetbrains.arend.ide.psi.ArdDefInstance
import com.jetbrains.arend.ide.psi.ext.PsiLocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.Reference
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.PartialConcreteProvider


object PsiPartialConcreteProvider : PartialConcreteProvider {
    override fun getInstanceTypeReference(instance: GlobalReferable): Reference? =
            (PsiLocatedReferable.fromReferable(instance) as? ArdDefInstance)?.let { getInstanceReference(it) }

    override fun isRecord(classRef: ClassReferable) = (PsiLocatedReferable.fromReferable(classRef) as? ArdDefClass)?.recordKw != null

    private fun getInstanceReference(instance: ArdDefInstance): Reference? =
            instance.argumentAppExpr?.let { argumentAppExpr ->
                argumentAppExpr.longNameExpr?.longName
                        ?: argumentAppExpr.atomFieldsAcc?.let { it.atom.literal?.longName }
            }

    override fun isInstance(ref: GlobalReferable) = PsiLocatedReferable.fromReferable(ref) is ArdDefInstance

    override fun isCoerce(ref: GlobalReferable) = (PsiLocatedReferable.fromReferable(ref) as? ArdDefFunction)?.coerceKw != null

    override fun isData(ref: GlobalReferable) = PsiLocatedReferable.fromReferable(ref) is ArdDefData
}