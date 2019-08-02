package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.icons.AllIcons
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.arend.highlight.BasePass
import org.arend.highlight.BasePass.Companion.isEmptyGoal
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.FieldReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.psi.*
import org.arend.psi.ext.ArendNewExprImplMixin
import org.arend.quickfix.AbstractEWCCAnnotator.Companion.moveCaretToEndOffset
import org.arend.quickfix.AbstractEWCCAnnotator.Companion.moveCaretToStartOffset

enum class InstanceQuickFixAnnotation {
    IMPLEMENT_FIELDS_ERROR,
    NO_ANNOTATION
}

abstract class AbstractEWCCAnnotator(private val classReferenceHolder: ClassReferenceHolder,
                                     private val rangeToReport: TextRange,
                                     val annotationToShow: InstanceQuickFixAnnotation = InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR,
                                     private val onlyCheckFields: Boolean = false) {
    abstract fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?)
    abstract fun coClausesList(): List<ArendCoClause>

    private fun findImplementedCoClauses(coClauseList: List<ArendCoClause>,
                                         holder: AnnotationHolder?,
                                         superClassesFields: HashMap<ClassReferable, MutableSet<FieldReferable>>,
                                         fields: MutableSet<FieldReferable>) {
        for (coClause in coClauseList) {
            val referable = coClause.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? LocatedReferable ?: continue

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

            if (!fields.remove(referable)) holder?.createErrorAnnotation(BasePass.getImprovedTextRange(null, coClause), "Field ${referable.textRepresentation()} is already implemented")?.registerFix(RemoveCoClause(coClause))

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
            val referable = coClause.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? LocatedReferable ?: continue
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
                        warningAnnotation.registerFix(RemoveCoClause(coClause))
                    }
                } else {
                    annotateCoClauses(subClauses, holder, superClassesFields, fields)
                }
                continue
            }

            if (clauseBlock || emptyGoal) {
                val severity = if (clauseBlock) InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR else InstanceQuickFixAnnotation.NO_ANNOTATION
                CoClauseAnnotator(coClause, rangeToReport, severity).doAnnotate(holder)
            }
        }
    }

    fun doAnnotate(holder: AnnotationHolder): List<Pair<LocatedReferable, Boolean>> {
        val superClassesFields = HashMap<ClassReferable, MutableSet<FieldReferable>>()
        val classReferenceData = classReferenceHolder.getClassReferenceData(true)
        if (classReferenceData != null) {
            val fields = ClassReferable.Helper.getNotImplementedFields(classReferenceData.classRef, classReferenceData.argumentsExplicitness, classReferenceData.withTailImplicits, superClassesFields)
            fields.removeAll(classReferenceData.implementedFields)

            findImplementedCoClauses(coClausesList(), holder, superClassesFields, fields)
            annotateCoClauses(coClausesList(), holder, superClassesFields, fields)

            if (!onlyCheckFields) {
                if (fields.isNotEmpty()) {
                    val fieldsList = ImplementFieldsQuickFix.makeFieldList(fields, classReferenceData.classRef)

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
                            holder.createErrorAnnotation(rangeToReport, message).registerFix(ImplementFieldsQuickFix(this, fieldsList))
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

    companion object {
        fun moveCaretToEndOffset(editor: Editor?, anchor: PsiElement) {
            if (editor != null) {
                editor.caretModel.moveToOffset(anchor.textRange.endOffset)
                IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
            }
        }

        fun moveCaretToStartOffset(editor: Editor?, anchor: PsiElement) {
            if (editor != null) {
                editor.caretModel.moveToOffset(anchor.textRange.startOffset)
                IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
            }
        }

        fun makeAnnotator(element: PsiElement?) = when (element) {
            is ArendNewExprImplMixin -> element.argumentAppExpr?.let { NewExprAnnotator(element, it) }
            is ArendDefInstance ->
                if (element.returnExpr != null && element.classReference?.isRecord == false && element.instanceBody.let { it == null || it.fatArrow == null && it.elim == null })
                    ArendInstanceAnnotator(element)
                else null
            is ArendDefFunction ->
                if (element.functionBody?.cowithKw != null)
                    FunctionDefinitionAnnotator(element)
                else null
            is CoClauseBase ->
                if (element.fatArrow == null)
                    CoClauseAnnotator(element, BasePass.getImprovedTextRange(null, element), InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR)
                else null
            else -> null
        }
    }
}

class CoClauseAnnotator(private val coClause: CoClauseBase, rangeToReport: TextRange, isError: InstanceQuickFixAnnotation) :
        AbstractEWCCAnnotator(coClause, rangeToReport, isError) {

    override fun coClausesList(): List<ArendCoClause> = coClause.coClauseList

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        coClause.fatArrow?.deleteWithNotification()
        coClause.expr?.deleteWithNotification()

        val anchor = coClause.lbrace ?: run {
            val longName = coClause.longName!!
            val braces = factory.createPairOfBraces()
            longName.parent.addAfter(braces.second, longName)
            longName.parent.addAfter(braces.first, longName)
            longName.parent.addAfter(factory.createWhitespace(" "), longName) //separator between lBrace and coClause name
            longName.nextSibling.nextSibling
        }

        val sampleCoClause = factory.createCoClause(name)
        anchor.parent.addAfterWithNotification(sampleCoClause, anchor)
        moveCaretToEndOffset(editor, anchor.nextSibling)

        anchor.parent.addAfter(factory.createWhitespace("\n"), anchor)
    }
}

class FunctionDefinitionAnnotator(private val functionDefinition: ArendDefFunction) :
        AbstractEWCCAnnotator(functionDefinition, BasePass.getImprovedTextRange(null, functionDefinition)) {

    override fun coClausesList(): List<ArendCoClause> = functionDefinition.functionBody?.coClauseList
            ?: emptyList()

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        var functionBody = functionDefinition.functionBody
        if (functionBody == null) {
            val functionBodySample = factory.createCoClauseInFunction(name).parent as ArendFunctionBody
            functionBody = functionDefinition.addAfterWithNotification(functionBodySample, functionDefinition.children.last()) as ArendFunctionBody
            functionDefinition.addBefore(factory.createWhitespace("\n"), functionBody)
            moveCaretToEndOffset(editor, functionBody.lastChild)
        } else {
            val sampleCoClause = factory.createCoClause(name)
            val anchor = when {
                functionBody.coClauseList.isNotEmpty() -> functionBody.coClauseList.last()
                functionBody.lbrace != null -> functionBody.lbrace
                functionBody.cowithKw != null -> functionBody.cowithKw
                else -> null
            }
            val insertedClause = if (anchor != null) functionBody.addAfterWithNotification(sampleCoClause, anchor) else functionBody.add(sampleCoClause)
            functionBody.addBefore(factory.createWhitespace("\n"), insertedClause)
            if (insertedClause != null) moveCaretToEndOffset(editor, insertedClause)
        }
    }
}

class ArendInstanceAnnotator(private val instance: ArendDefInstance) :
        AbstractEWCCAnnotator(instance, BasePass.getImprovedTextRange(null, instance)) {

    override fun coClausesList(): List<ArendCoClause> = instance.instanceBody?.coClauseList ?: emptyList()

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        var instanceBody = instance.instanceBody
        if (instanceBody == null) {
            val instanceBodySample = factory.createCoClause(name).parent as ArendInstanceBody
            val anchor = if (instance.returnExpr != null) instance.returnExpr else instance.defIdentifier
            instanceBody = instance.addAfterWithNotification(instanceBodySample, anchor) as ArendInstanceBody
            instance.addBefore(factory.createWhitespace("\n"), instanceBody)
            moveCaretToEndOffset(editor, instanceBody.lastChild)
        } else {
            val sampleCoClause = factory.createCoClause(name)
            val anchor = when {
                instanceBody.coClauseList.isNotEmpty() -> instanceBody.coClauseList.last()
                instanceBody.lbrace != null -> instanceBody.lbrace
                instanceBody.cowithKw != null -> instanceBody.cowithKw
                else -> null
            }

            val insertedClause = if (anchor != null) instanceBody.addAfterWithNotification(sampleCoClause, anchor) else instanceBody.add(sampleCoClause)
            instanceBody.addBefore(factory.createWhitespace("\n"), insertedClause)
            if (insertedClause != null) moveCaretToEndOffset(editor, insertedClause)
        }
    }
}

class NewExprAnnotator(private val newExpr: ArendNewExprImplMixin, private val argumentAppExpr: ArendArgumentAppExpr) :
        AbstractEWCCAnnotator(newExpr, BasePass.getImprovedTextRange(null, argumentAppExpr), InstanceQuickFixAnnotation.NO_ANNOTATION, newExpr.newKw == null) {
    override fun coClausesList(): List<ArendCoClause> = newExpr.coClauseList

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        val anchor = newExpr.lbrace ?: run {
            val braces = factory.createPairOfBraces()
            argumentAppExpr.parent.addAfter(braces.second, argumentAppExpr)
            argumentAppExpr.parent.addAfter(braces.first, argumentAppExpr)
            argumentAppExpr.parent.addAfter(factory.createWhitespace(" "), argumentAppExpr) //separator between name and lbrace
            argumentAppExpr.nextSibling.nextSibling
        }

        val sampleCoClause = factory.createCoClause(name)
        anchor.parent.addAfterWithNotification(sampleCoClause, anchor)

        moveCaretToEndOffset(editor, anchor.nextSibling)
        anchor.parent.addAfter(factory.createWhitespace("\n"), anchor)
    }
}

class RemoveCoClause(private val coClause: ArendCoClause) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

    override fun getText() = "Remove redundant coclause"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        moveCaretToStartOffset(editor, coClause)
        val parent = coClause.parent
        coClause.deleteWithNotification()
        if ((parent is ArendFunctionBody || parent is ArendInstanceBody) && parent.firstChild == null) parent.deleteWithNotification()
    }
}

class ImplementFieldsQuickFix(val instance: AbstractEWCCAnnotator, private val fieldsToImplement: List<Pair<LocatedReferable, Boolean>>) : IntentionAction, Iconable {
    private var caretMoved = false

    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

    override fun getText() = "Implement missing fields"

    companion object {
        fun makeFieldList(fields: Collection<FieldReferable>, classRef: ClassReferable): List<Pair<FieldReferable, Boolean>> {
            val scope = CachingScope.make(ClassFieldImplScope(classRef, false))
            return fields.map { field -> Pair(field, scope.resolveName(field.textRepresentation()) != field) }
        }
    }

    private fun addField(field: Referable, editor: Editor?, psiFactory: ArendPsiFactory, needQualifiedName: Boolean = false) {
        val coClauses = instance.coClausesList()
        val fieldClass = (field as? LocatedReferable)?.locatedReferableParent
        val name = if (needQualifiedName && fieldClass != null) "${fieldClass.textRepresentation()}.${field.textRepresentation()}" else field.textRepresentation()

        if (coClauses.isEmpty()) {
            instance.insertFirstCoClause(name, psiFactory, editor)
            caretMoved = true
        } else {
            val anchor = coClauses.last()
            val coClause = psiFactory.createCoClause(name)

            anchor.parent.addAfterWithNotification(coClause, anchor)
            if (!caretMoved && editor != null) {
                moveCaretToEndOffset(editor, anchor.nextSibling)
                caretMoved = true
            }
            anchor.parent.addAfter(psiFactory.createWhitespace("\n"), anchor)
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val psiFactory = ArendPsiFactory(project)
        for (f in fieldsToImplement) {
            addField(f.first, editor, psiFactory, f.second)
        }

        // Add CRLF after last coclause
        val lastCC = instance.coClausesList().lastOrNull() ?: return
        if (lastCC.nextSibling?.node?.elementType == ArendElementTypes.RBRACE) {
            lastCC.parent?.addAfter(psiFactory.createWhitespace("\n"), lastCC)
        }
    }

    override fun getIcon(flags: Int) = if (instance.annotationToShow == InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR) null else AllIcons.Actions.IntentionBulb
}

object CoClausesKey : Key<List<Pair<FieldReferable, Boolean>>>("coClausesInfo")
