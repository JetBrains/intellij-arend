package org.vclang.resolving

import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.Reference
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.PartialConcreteProvider
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcDefInstance
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.typing.ReferableExtractVisitor


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

    override fun getInstanceParameterReferences(ref: GlobalReferable): List<PartialConcreteProvider.InstanceParameter>? {
        val instance = PsiLocatedReferable.fromReferable(ref) as? VcDefInstance ?: return null
        val parameters = instance.nameTeleList
        return if (parameters.isEmpty()) emptyList() else {
            val scope = instance.scope
            val visitor = ReferableExtractVisitor(scope)
            parameters.mapNotNull { nameTele ->
                nameTele.expr?.let {
                    val isExplicit = nameTele.lbrace == null
                    PartialConcreteProvider.InstanceParameter(isExplicit, ExpressionResolveNameVisitor.resolve(it.accept(visitor, null), scope, true) as? GlobalReferable, if (isExplicit) nameTele else it)
                } ?: if (nameTele.lparen != null) PartialConcreteProvider.InstanceParameter(true, null, nameTele) else null
            }
        }
    }
}