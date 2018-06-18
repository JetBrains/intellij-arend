package org.vclang.resolving

import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.Reference
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.PartialConcreteProvider
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcDefInstance


object PsiPartialConcreteProvider : PartialConcreteProvider {
    override fun getInstanceClassReference(instance: GlobalReferable?): Reference? = (instance as? VcDefInstance)?.longName

    override fun isRecord(classRef: ClassReferable?): Boolean = (classRef as? VcDefClass)?.recordKw != null
}