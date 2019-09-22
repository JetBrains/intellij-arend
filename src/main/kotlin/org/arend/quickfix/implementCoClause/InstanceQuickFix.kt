package org.arend.quickfix.implementCoClause

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.highlight.BasePass
import org.arend.highlight.BasePass.Companion.isEmptyGoal
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.FieldReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.psi.*
import org.arend.psi.ext.ArendNewExprImplMixin
import org.arend.quickfix.removers.RemoveCoClauseQuickFix

enum class InstanceQuickFixAnnotation {
    IMPLEMENT_FIELDS_ERROR,
    NO_ANNOTATION
}

private fun findImplementedCoClauses(coClauseList: List<ArendCoClause>,
                                     holder: AnnotationHolder?,
                                     superClassesFields: HashMap<ClassReferable, MutableSet<FieldReferable>>,
                                     fields: MutableSet<FieldReferable>) {
    for (coClause in coClauseList) {
        val referable = coClause.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? LocatedReferable
                ?: continue

        if (referable is ClassReferable) {
            val subClauses = if (coClause.fatArrow != null) {
                val superClassFields = superClassesFields[referable]
                if (superClassFields != null && superClassFields.isNotEmpty()) {
                    fields.removeAll(superClassFields)
                    continue
                }
                emptyList()
            } else coClause.coClauseList

            if (subClauses.isNotEmpty()) findImplementedCoClauses(subClauses, holder, superClassesFields, fields)
            continue
        }

        if (!fields.remove(referable)) holder?.createErrorAnnotation(BasePass.getImprovedTextRange(null, coClause), "Field ${referable.textRepresentation()} is already implemented")?.registerFix(RemoveCoClauseQuickFix(coClause))

        for (superClassFields in superClassesFields.values) superClassFields.remove(referable)
    }
}

private fun annotateCoClauses(coClauseList: List<ArendCoClause>,
                              holder: AnnotationHolder,
                              superClassesFields: HashMap<ClassReferable, MutableSet<FieldReferable>>,
                              fields: MutableSet<FieldReferable>) {
    for (coClause in coClauseList) {
        val expr = coClause.expr
        val fatArrow = coClause.fatArrow
        val clauseBlock = fatArrow == null
        val emptyGoal = expr != null && isEmptyGoal(expr)
        val referable = coClause.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? LocatedReferable
                ?: continue
        val rangeToReport = BasePass.getImprovedTextRange(null, coClause)

        if (referable is ClassReferable) {
            val subClauses = if (fatArrow != null) emptyList() else coClause.coClauseList

            val fieldToImplement = superClassesFields[referable]
            if (fieldToImplement != null) {
                val scope = CachingScope.make(ClassFieldImplScope(referable, false))
                val fieldsList = fieldToImplement.map { Pair(it, scope.resolveName(it.textRepresentation()) != it) }
                coClause.putUserData(CoClausesKey, fieldsList)
            }

            if (subClauses.isEmpty() && fatArrow == null) {
                val warningAnnotation = holder.createWeakWarningAnnotation(rangeToReport, "Coclause is redundant")
                if (warningAnnotation != null) {
                    warningAnnotation.highlightType = ProblemHighlightType.LIKE_UNUSED_SYMBOL
                    warningAnnotation.registerFix(RemoveCoClauseQuickFix(coClause))
                }
            } else {
                annotateCoClauses(subClauses, holder, superClassesFields, fields)
            }
            continue
        }

        if (clauseBlock || emptyGoal) {
            val severity = if (clauseBlock) InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR else InstanceQuickFixAnnotation.NO_ANNOTATION
            doAnnotateInternal(coClause, rangeToReport, coClause.coClauseList, holder, severity)
        }
    }
}

private fun doAnnotateInternal(classReferenceHolder: ClassReferenceHolder,
                               rangeToReport: TextRange,
                               coClausesList: List<ArendCoClause>,
                               holder: AnnotationHolder,
                               annotationToShow: InstanceQuickFixAnnotation = InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR,
                               onlyCheckFields: Boolean = false): List<Pair<LocatedReferable, Boolean>> {
    val superClassesFields = HashMap<ClassReferable, MutableSet<FieldReferable>>()
    val classReferenceData = classReferenceHolder.getClassReferenceData(true)
    if (classReferenceData != null) {
        val fields = ClassReferable.Helper.getNotImplementedFields(classReferenceData.classRef, classReferenceData.argumentsExplicitness, classReferenceData.withTailImplicits, superClassesFields)
        fields.removeAll(classReferenceData.implementedFields)

        findImplementedCoClauses(coClausesList, holder, superClassesFields, fields)
        annotateCoClauses(coClausesList, holder, superClassesFields, fields)

        if (!onlyCheckFields) {
            if (fields.isNotEmpty()) {
                val fieldsList = makeFieldList(fields, classReferenceData.classRef)

                when (annotationToShow) {
                    InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR -> {
                        val message = buildString {
                            append("The following fields are not implemented: ")
                            val iterator = fields.iterator()
                            do {
                                append(iterator.next().textRepresentation())
                                if (iterator.hasNext()) {
                                    append(", ")
                                }
                            } while (iterator.hasNext())
                        }
                        holder.createErrorAnnotation(rangeToReport, message).registerFix(ImplementFieldsQuickFix(classReferenceHolder, annotationToShow != InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR, fieldsList))
                    }
                    InstanceQuickFixAnnotation.NO_ANNOTATION -> classReferenceHolder.putUserData(CoClausesKey, fieldsList)
                }

                return fieldsList
            } else if (annotationToShow == InstanceQuickFixAnnotation.NO_ANNOTATION) {
                classReferenceHolder.putUserData(CoClausesKey, null)
            }
        }
    }
    return emptyList()
}

fun makeFieldList(fields: Collection<FieldReferable>, classRef: ClassReferable): List<Pair<FieldReferable, Boolean>> {
    val scope = CachingScope.make(ClassFieldImplScope(classRef, false))
    return fields.map { field -> Pair(field, scope.resolveName(field.textRepresentation()) != field) }
}

fun doAnnotate(element: PsiElement?, holder: AnnotationHolder) {
    when (element) {
        is ArendNewExprImplMixin -> element.argumentAppExpr?.let {
            doAnnotateInternal(element, BasePass.getImprovedTextRange(null, it), element.coClauseList, holder, InstanceQuickFixAnnotation.NO_ANNOTATION, element.newKw == null)
        }
        is ArendDefInstance -> if (element.returnExpr != null && element.classReference?.isRecord == false && element.instanceBody.let { it == null || it.fatArrow == null && it.elim == null })
            doAnnotateInternal(element, BasePass.getImprovedTextRange(null, element), element.instanceBody?.coClauseList
                    ?: emptyList(), holder)
        is ArendDefFunction -> if (element.functionBody?.cowithKw != null)
            doAnnotateInternal(element, BasePass.getImprovedTextRange(null, element), element.functionBody?.coClauseList
                    ?: emptyList(), holder)
        is CoClauseBase -> if (element.fatArrow == null)
            doAnnotateInternal(element, BasePass.getImprovedTextRange(null, element), element.coClauseList, holder, InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR)
    }
}


object CoClausesKey : Key<List<Pair<FieldReferable, Boolean>>>("coClausesInfo")
