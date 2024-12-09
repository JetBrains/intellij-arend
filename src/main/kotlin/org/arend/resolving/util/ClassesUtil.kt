package org.arend.resolving.util

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.FieldReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.naming.scope.Scope
import org.arend.psi.ext.ArendLongName
import org.arend.psi.ClassReferenceHolder
import org.arend.term.abs.Abstract


private fun getNotImplementedFields(classDef: ClassReferable, classRefHolder: ClassReferenceHolder?, superClassesFields: HashMap<ClassReferable, MutableSet<FieldReferable>>): Set<FieldReferable> {
    val classRefData = classRefHolder?.getClassReferenceData(false)
    val result = ClassReferable.Helper.getNotImplementedFields(classDef, classRefData?.argumentsExplicitness ?: emptyList(), classRefData?.withTailImplicits ?: false, superClassesFields)
    if (classRefHolder != null && (classRefHolder as? Abstract.ClassDefinition)?.referable != classDef) {
        for (fieldImpl in classRefHolder.coClauseElements) {
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
    override fun getElements(context: Scope.ScopeContext?): List<Referable> {
        if (context != null && context != Scope.ScopeContext.STATIC) return emptyList()
        val superClassesFields = HashMap<ClassReferable, MutableSet<FieldReferable>>()
        val fields = getNotImplementedFields(classReference, classRefHolder, superClassesFields)
        return fields.toList() + superClassesFields.mapNotNull { entry -> if (entry.value.isEmpty()) null else entry.key }
    }
}