package org.arend.typing

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.FieldReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.psi.ArendLongName
import org.arend.psi.ClassReferenceHolder


private fun getNotImplementedFields(classDef: ClassReferable, classRefHolder: ClassReferenceHolder?, superClassesFields: HashMap<ClassReferable, MutableSet<FieldReferable>>): Set<FieldReferable> {
    val result = ClassReferable.Helper.getNotImplementedFields(classDef, classRefHolder?.getClassReferenceData(false)?.argumentsExplicitness ?: emptyList(), superClassesFields)
    if (classRefHolder != null) {
        for (fieldImpl in classRefHolder.classFieldImpls) {
            (fieldImpl as? PsiElement)?.let {
                val resolved = PsiTreeUtil.getChildOfType(it, ArendLongName::class.java)?.refIdentifierList?.lastOrNull()?.reference?.resolve()
                if (resolved is FieldReferable) {
                    result.remove(resolved)
                    for (fields in superClassesFields.values) {
                        fields.remove(resolved)
                    }
                }
            }
        }
    }
    return result
}

class ModifiedClassFieldImplScope(referable: ClassReferable, private val classRefHolder: ClassReferenceHolder?) : ClassFieldImplScope(referable, true) {
    override fun getElements(): List<Referable> {
        val superClassesFields = HashMap<ClassReferable, MutableSet<FieldReferable>>()
        val fields = getNotImplementedFields(classReference, classRefHolder, superClassesFields)
        return fields.toList() + superClassesFields.mapNotNull { entry -> if (entry.value.isEmpty()) null else entry.key }
    }
}