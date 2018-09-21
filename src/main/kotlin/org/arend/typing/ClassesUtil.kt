package org.arend.typing

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.FieldReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.term.abs.Abstract
import org.arend.psi.ArendLongName


private fun getNotImplementedFields(classDef: ClassReferable, classRefHolder: Abstract.ClassReferenceHolder?, superClassesFields: HashMap<ClassReferable, MutableSet<FieldReferable>>): HashMap<FieldReferable, List<LocatedReferable>> {
    val result = ClassReferable.Helper.getNotImplementedFields(classDef, classRefHolder?.argumentsExplicitness ?: emptyList(), superClassesFields)
    if (classRefHolder != null) {
        for (fieldImpl in classRefHolder.classFieldImpls) {
            (fieldImpl as? PsiElement)?.let {
                val resolved = PsiTreeUtil.getChildOfType(it, ArendLongName::class.java)?.refIdentifierList?.lastOrNull()?.reference?.resolve()
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

class ModifiedClassFieldImplScope(referable: ClassReferable, private val classRefHolder: Abstract.ClassReferenceHolder?) : ClassFieldImplScope(referable, true) {
    override fun getElements(): List<Referable> {
        val superClassesFields = HashMap<ClassReferable, MutableSet<FieldReferable>>()
        val fields = getNotImplementedFields(classReference, classRefHolder, superClassesFields).values.flatten()
        return fields + superClassesFields.mapNotNull { entry -> if (entry.value.isEmpty()) null else entry.key }
    }
}