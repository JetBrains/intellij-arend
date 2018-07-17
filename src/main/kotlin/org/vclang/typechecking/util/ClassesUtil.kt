package org.vclang.typechecking.util

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.scope.ClassFieldImplScope
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcLongName
import org.vclang.psi.ext.impl.ClassDefinitionAdapter


fun getNotImplementedFields(classDef: VcDefClass, numberOfFieldsToSkip: Int): HashMap<LocatedReferable, List<LocatedReferable>> {
    val result = getNotImplementedFields(classDef, HashSet())
    if (numberOfFieldsToSkip != 0) {
        val it = result.iterator()
        for (i in 1..numberOfFieldsToSkip) {
            if (!it.hasNext()) {
                break
            }
            it.next()
            it.remove()
        }
    }
    return result
}

fun getNotImplementedFields(classDef: VcDefClass, classRefHolder: Abstract.ClassReferenceHolder?): HashMap<LocatedReferable, List<LocatedReferable>> {
    val result = getNotImplementedFields(classDef, classRefHolder?.numberOfArguments ?: 0)
    if (classRefHolder != null) {
        for (fieldImpl in classRefHolder.classFieldImpls) {
            (fieldImpl as? PsiElement)?.let {
                val resolved = PsiTreeUtil.getChildOfType(it, VcLongName::class.java)?.refIdentifierList?.lastOrNull()?.reference?.resolve()
                if (resolved is LocatedReferable) {
                    result.remove(resolved.underlyingReference ?: resolved)
                }
            }
        }
    }
    return result
}

private fun getNotImplementedFields(classDef: VcDefClass, visited: MutableSet<VcDefClass>): HashMap<LocatedReferable, List<LocatedReferable>> {
    if (!visited.add(classDef)) {
        return HashMap()
    }

    val result = LinkedHashMap<LocatedReferable, List<LocatedReferable>>()
    for (superClass in classDef.superClassReferences) {
        if (superClass is VcDefClass) {
            for (entry in getNotImplementedFields(superClass, visited)) {
                result.compute(entry.key) { _,list -> if (list == null) entry.value else list + entry.value }
            }
        }
    }

    for (field in classDef.fieldReferables) {
        val underlyingField = field.underlyingReference ?: field
        result.compute(underlyingField) { _,list -> if (list == null) listOf(field) else list + field }
    }

    if (classDef is ClassDefinitionAdapter) {
        for (fieldImpl in classDef.classFieldImpls) {
            val resolved = fieldImpl.longName.refIdentifierList.lastOrNull()?.reference?.resolve()
            if (resolved is LocatedReferable) {
                result.remove(resolved.underlyingReference ?: resolved)
            }
        }
    }

    return result
}

class ModifiedClassFieldImplScope(referable: VcDefClass, private val classRefHolder: Abstract.ClassReferenceHolder?) : ClassFieldImplScope(referable, true) {
    override fun getElements(): List<Referable> = getNotImplementedFields(classReference as VcDefClass, classRefHolder).values.flatten()
}