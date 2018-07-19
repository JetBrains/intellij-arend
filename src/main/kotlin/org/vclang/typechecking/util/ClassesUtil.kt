package org.vclang.typechecking.util

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.scope.ClassFieldImplScope
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcLongName
import org.vclang.psi.ext.impl.ClassDefinitionAdapter


fun getNotImplementedFields(classDef: VcDefClass, numberOfFieldsToSkip: Int, superClassesFields: HashMap<ClassReferable, MutableSet<LocatedReferable>>): HashMap<LocatedReferable, List<LocatedReferable>> {
    val result = getNotImplementedFields(classDef, HashSet(), superClassesFields)
    if (numberOfFieldsToSkip != 0) {
        val it = result.iterator()
        for (i in 1..numberOfFieldsToSkip) {
            if (!it.hasNext()) {
                break
            }
            val field = it.next().key
            for (fields in superClassesFields.values) {
                fields.remove(field)
            }
            it.remove()
        }
    }
    return result
}

fun getNotImplementedFields(classDef: VcDefClass, classRefHolder: Abstract.ClassReferenceHolder?, superClassesFields: HashMap<ClassReferable, MutableSet<LocatedReferable>>): HashMap<LocatedReferable, List<LocatedReferable>> {
    val result = getNotImplementedFields(classDef, classRefHolder?.numberOfArguments ?: 0, superClassesFields)
    if (classRefHolder != null) {
        for (fieldImpl in classRefHolder.classFieldImpls) {
            (fieldImpl as? PsiElement)?.let {
                val resolved = PsiTreeUtil.getChildOfType(it, VcLongName::class.java)?.refIdentifierList?.lastOrNull()?.reference?.resolve()
                if (resolved is LocatedReferable) {
                    val field = resolved.underlyingReference ?: resolved
                    result.remove(field)
                    for (fields in superClassesFields.values) {
                        fields.remove(field)
                    }
                }
            }
        }
    }
    return result
}

private fun getNotImplementedFields(classDef: VcDefClass, visited: MutableSet<VcDefClass>, superClassesFields: HashMap<ClassReferable, MutableSet<LocatedReferable>>): HashMap<LocatedReferable, List<LocatedReferable>> {
    if (!visited.add(classDef)) {
        return HashMap()
    }

    val result = LinkedHashMap<LocatedReferable, List<LocatedReferable>>()
    for (superClass in classDef.superClassReferences) {
        if (superClass is VcDefClass) {
            val superClassMap = getNotImplementedFields(superClass, visited, superClassesFields)
            superClassesFields.compute(superClass) { _,oldFields ->
                if (oldFields == null) {
                    superClassMap.keys
                } else {
                    oldFields.retainAll(superClassMap.keys)
                    oldFields
                }
            }
            for (entry in superClassMap) {
                result.compute(entry.key) { _,list -> if (list == null) entry.value else list + entry.value }
            }
        }
    }

    val underlyingClass = classDef.underlyingReference
    val renamings: Map<LocatedReferable, List<LocatedReferable>> = if (underlyingClass == null) emptyMap() else {
        val map = HashMap<LocatedReferable, List<LocatedReferable>>()
        for (field in classDef.fieldReferables) {
            val underlyingField = field.underlyingReference
            if (underlyingField != null) {
                map.compute(underlyingField) { _,list -> if (list == null) listOf(field) else list + field }
            }
        }
        map
    }
    for (field in (underlyingClass ?: classDef).fieldReferables) {
        result.compute(field) { _,list ->
            val list2 = renamings[field] ?: listOf(field)
            if (list == null) list2 else list + list2
        }
    }

    if (classDef is ClassDefinitionAdapter) {
        for (fieldImpl in classDef.classFieldImpls) {
            val resolved = fieldImpl.longName.refIdentifierList.lastOrNull()?.reference?.resolve()
            if (resolved is ClassReferable) {
                val superClassFields = superClassesFields.remove(resolved)
                if (superClassFields != null) {
                    result.keys.removeAll(superClassFields)
                    for (fields in superClassesFields.values) {
                        fields.removeAll(superClassFields)
                    }
                }
            } else if (resolved is LocatedReferable) {
                val field = resolved.underlyingReference ?: resolved
                result.remove(field)
                for (fields in superClassesFields.values) {
                    fields.remove(field)
                }
            }
        }
    }

    return result
}

class ModifiedClassFieldImplScope(referable: VcDefClass, private val classRefHolder: Abstract.ClassReferenceHolder?) : ClassFieldImplScope(referable, true) {
    override fun getElements(): List<Referable> {
        val superClassesFields = HashMap<ClassReferable, MutableSet<LocatedReferable>>()
        val fields = getNotImplementedFields(classReference as VcDefClass, classRefHolder, superClassesFields).values.flatten()
        return fields + superClassesFields.mapNotNull { entry -> if (entry.value.isEmpty()) null else entry.key }
    }
}