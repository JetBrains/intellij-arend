package org.arend.quickfix.implementCoClause

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.arend.ext.reference.ArendRef
import org.arend.highlight.BasePass.Companion.isEmptyGoal
import org.arend.naming.reference.*
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.psi.ClassReferenceHolder
import org.arend.psi.ext.*

enum class InstanceQuickFixAnnotation {
    IMPLEMENT_FIELDS_ERROR,
    NO_ANNOTATION
}

private fun findImplementedCoClauses(coClauseList: List<CoClauseBase>,
                                     superClassesFields: HashMap<ClassReferable, MutableSet<FieldReferable>>,
                                     fields: MutableSet<FieldReferable>) {
    for (coClause in coClauseList) {
        val referable = coClause.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? LocatedReferable
                ?: continue

        if (referable is ClassReferable) {
            val subClauses = if (coClause.fatArrow != null) {
                val superClassFields = superClassesFields[referable]
                if (!superClassFields.isNullOrEmpty()) {
                    fields.removeAll(superClassFields)
                    continue
                }
                emptyList()
            } else coClause.localCoClauseList

            if (subClauses.isNotEmpty()) findImplementedCoClauses(subClauses, superClassesFields, fields)
            continue
        }

        //if (!fields.remove(referable)) holder?.createErrorAnnotation(BasePass.getImprovedTextRange(null, coClause), "Field ${referable.textRepresentation()} is already implemented")?.registerFix(RemoveCoClauseQuickFix(SmartPointerManager.createPointer(coClause)))

        for (superClassFields in superClassesFields.values) superClassFields.remove(referable)
    }
}

private fun annotateCoClauses(coClauseList: List<CoClauseBase>,
                              superClassesFields: HashMap<ClassReferable, MutableSet<FieldReferable>>,
                              fields: MutableSet<FieldReferable>) {
    for (coClause in coClauseList) {
        val expr = coClause.expr
        val fatArrow = coClause.fatArrow
        val clauseBlock = fatArrow == null
        val emptyGoal = expr != null && isEmptyGoal(expr)
        val referable = coClause.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? LocatedReferable
                ?: continue
        //val rangeToReport = BasePass.getImprovedTextRange(null, coClause)

        if (referable is ClassReferable) {
            val subClauses = if (fatArrow != null) emptyList() else coClause.localCoClauseList

            val fieldToImplement = superClassesFields[referable]
            if (fieldToImplement != null) {
                coClause.putUserData(CoClausesKey, makeFieldList(fieldToImplement, referable))
            }

            if (subClauses.isEmpty() && fatArrow == null) {
                /*val warningAnnotation = holder?.createWeakWarningAnnotation(rangeToReport, "Coclause is redundant")
                if (warningAnnotation != null) {
                    warningAnnotation.highlightType = ProblemHighlightType.LIKE_UNUSED_SYMBOL
                    warningAnnotation.registerFix(RemoveCoClauseQuickFix(SmartPointerManager.createPointer(coClause)))
                }*/
            } else {
                annotateCoClauses(subClauses, superClassesFields, fields)
            }
            continue
        }

        if (clauseBlock || emptyGoal) {
            val severity = if (clauseBlock) InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR else InstanceQuickFixAnnotation.NO_ANNOTATION
            doAnnotateInternal(coClause, /*rangeToReport,*/ coClause.localCoClauseList, severity)
        }
    }
}

private fun doAnnotateInternal(classReferenceHolder: ClassReferenceHolder,
        //rangeToReport: TextRange,
                               coClausesList: List<CoClauseBase>,
                               annotationToShow: InstanceQuickFixAnnotation = InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR,
                               onlyCheckFields: Boolean = false): List<Pair<LocatedReferable, Boolean>> {
    val superClassesFields = HashMap<ClassReferable, MutableSet<FieldReferable>>()
    val classReferenceData = classReferenceHolder.getClassReferenceData(true)
    if (classReferenceData != null) {
        val fields = ClassReferable.Helper.getNotImplementedFields(classReferenceData.classRef, classReferenceData.argumentsExplicitness, classReferenceData.withTailImplicits, superClassesFields)
        fields.removeAll(classReferenceData.implementedFields)

        findImplementedCoClauses(coClausesList, superClassesFields, fields)
        annotateCoClauses(coClausesList, superClassesFields, fields)

        if (!onlyCheckFields) {
            if (fields.isNotEmpty()) {
                val fieldsList = makeFieldList(fields, classReferenceData.classRef)

                when (annotationToShow) {
                    InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR -> {
                        /*val message = buildString {
                            append("The following fields are not implemented: ")
                            val iterator = fields.iterator()
                            do {
                                append(iterator.next().textRepresentation())
                                if (iterator.hasNext()) {
                                    append(", ")
                                }
                            } while (iterator.hasNext())
                        }
                        holder?.createErrorAnnotation(rangeToReport, message)?.registerFix(ImplementFieldsQuickFix(SmartPointerManager.createPointer(classReferenceHolder), annotationToShow != InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR, fieldsList))*/
                    }
                    InstanceQuickFixAnnotation.NO_ANNOTATION ->
                        classReferenceHolder.putUserData(CoClausesKey, fieldsList)
                }

                return fieldsList
            } else if (annotationToShow == InstanceQuickFixAnnotation.NO_ANNOTATION) {
                classReferenceHolder.putUserData(CoClausesKey, null)
            }
        }
    }
    return emptyList()
}

private fun getTCRef(ref: Referable?) = (ref as? PsiLocatedReferable)?.tcReferable ?: ref

fun makeFieldList(fields: Collection<ArendRef>, classRef: ClassReferable): List<Pair<LocatedReferable, Boolean>> {
    val scope = CachingScope.make(ClassFieldImplScope(classRef, false))
    return fields.mapNotNull { field ->
        if (field is LocatedReferable) {
            val field2 = scope.resolveName(field.refName)
            Pair(field, if (field is TCFieldReferable || field2 is TCFieldReferable) getTCRef(field) != getTCRef(field2) else field != field2)
        } else null
    }
}

fun doAnnotate(element: PsiElement?) {
    when (element) {
        is ArendNewExpr -> element.argumentAppExpr?.let {
            doAnnotateInternal(element, /*BasePass.getImprovedTextRange(null, it),*/ element.localCoClauseList, InstanceQuickFixAnnotation.NO_ANNOTATION, element.appPrefix?.isNew != true)
        }
        is ArendDefInstance -> if (element.returnExpr != null && element.classReference?.isRecord == false && element.body.let { it == null || it.fatArrow == null && it.elim == null })
            doAnnotateInternal(element, /*BasePass.getImprovedTextRange(null, element),*/ element.body?.coClauseList
                    ?: emptyList())
        is ArendDefFunction -> if (element.body?.cowithKw != null)
            doAnnotateInternal(element, /*BasePass.getImprovedTextRange(null, element),*/ element.body?.coClauseList
                    ?: emptyList())
        is CoClauseBase -> if (element.fatArrow == null)
            doAnnotateInternal(element, /*BasePass.getImprovedTextRange(null, element),*/ element.localCoClauseList, InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR)
    }
}


object CoClausesKey : Key<List<Pair<LocatedReferable, Boolean>>>("coClausesInfo")
