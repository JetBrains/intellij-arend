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
import org.arend.codeInsight.completion.withAncestors
import org.arend.core.definition.ClassDefinition
import org.arend.naming.reference.*
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.psi.*
import org.arend.psi.ext.ArendNewExprImplMixin
import org.arend.psi.ext.impl.InstanceAdapter
import org.arend.quickfix.AbstractEWCCAnnotator.Companion.IMPLEMENT_MISSING_FIELDS
import org.arend.quickfix.AbstractEWCCAnnotator.Companion.REMOVE_COCLAUSE
import org.arend.quickfix.AbstractEWCCAnnotator.Companion.moveCaretToEndOffset
import org.arend.quickfix.AbstractEWCCAnnotator.Companion.moveCaretToStartOffset
import org.arend.typechecking.TypeCheckingService

enum class InstanceQuickFixAnnotation {
    IMPLEMENT_FIELDS_ERROR,
    REPLACE_WITH_IMPLEMENTATION_INFO,
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
            val referable = coClause.getLongName()?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? LocatedReferable ?: continue

            if (referable is ClassReferable) {
                val subClauses = if (coClause.fatArrow != null) {
                    val superClassFields = superClassesFields[referable]
                    if (superClassFields != null && !superClassFields.isEmpty()) {
                        fields.removeAll(superClassFields)
                        continue
                    }
                    emptyList()
                } else coClause.getCoClauseList()

                if (!subClauses.isEmpty()) findImplementedCoClauses(subClauses, holder, superClassesFields, fields)
                continue
            }

            if (!fields.remove(referable)) holder?.createErrorAnnotation(coClause, "Field ${referable.textRepresentation()} is already implemented")?.registerFix(RemoveCoClause(coClause))

            for (superClassFields in superClassesFields.values) superClassFields.remove(referable)
        }
    }

    private fun annotateCoClauses(coClauseList: List<ArendCoClause>,
                                  holder: AnnotationHolder?,
                                  superClassesFields: HashMap<ClassReferable, MutableSet<FieldReferable>>,
                                  fields: MutableSet<FieldReferable>) {
        for (coClause in coClauseList) {
            val expr = coClause.expr
            val fatArrow = coClause.fatArrow
            val clauseBlock = fatArrow == null
            val emptyGoal = expr != null && isEmptyGoal(expr)
            val referable = coClause.getLongName()?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? LocatedReferable ?: continue
            val rangeToReport = if (emptyGoal) coClause.textRange else coClause.getLongName()?.textRange ?: coClause.textRange

            if (referable is ClassReferable) {
                val subClauses = if (fatArrow != null) emptyList() else coClause.getCoClauseList()

                val fieldToImplement = superClassesFields[referable]
                if (fieldToImplement != null) {
                    val scope = CachingScope.make(ClassFieldImplScope(referable, false))
                    val fieldsList = fieldToImplement.map { Pair(it, scope.resolveName(it.textRepresentation()) != it) }
                    coClause.putUserData(CoClausesKey.INSTANCE, fieldsList)
                }

                if (subClauses.isEmpty() && fatArrow == null) {
                    val warningAnnotation = holder?.createWeakWarningAnnotation(rangeToReport, "Coclause is redundant")
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
                val message = if (clauseBlock) IMPLEMENT_MISSING_FIELDS else REPLACE_WITH_IMPLEMENTATION
                val severity = if (clauseBlock) InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR else InstanceQuickFixAnnotation.REPLACE_WITH_IMPLEMENTATION_INFO
                (object : CoClauseAnnotator(coClause, rangeToReport, severity) {
                    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
                        if (emptyGoal) {
                            fatArrow?.deleteWithNotification()
                            expr?.deleteWithNotification()
                        }
                        super.insertFirstCoClause(name, factory, editor)
                    }
                }).doAnnotate(holder, message)
            }
        }
    }

    fun doAnnotate(holder: AnnotationHolder?, actionText: String): List<Pair<LocatedReferable, Boolean>> { //Map<FieldReferable, List<Pair<LocatedReferable, Boolean>>> {
        val superClassesFields = HashMap<ClassReferable, MutableSet<FieldReferable>>()
        val classReferenceData = classReferenceHolder.getClassReferenceData(true)
        if (classReferenceData != null) {
            val fields = ClassReferable.Helper.getNotImplementedFields(classReferenceData.classRef, classReferenceData.argumentsExplicitness, classReferenceData.withTailImplicits, superClassesFields)
            fields.removeAll(classReferenceData.implementedFields)

            findImplementedCoClauses(coClausesList(), holder, superClassesFields, fields)
            annotateCoClauses(coClausesList(), holder, superClassesFields, fields)

            if (!onlyCheckFields) {
                if (fields.isNotEmpty()) {
                    val scope = CachingScope.make(ClassFieldImplScope(classReferenceData.classRef, false))
                    val fieldsList = fields.map { field -> Pair(field, scope.resolveName(field.textRepresentation()) != field) }

                    val builder = StringBuilder()
                    val annotation = when (annotationToShow) {
                        InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR -> {
                            builder.append(IMPLEMENT_FIELDS_MSG)
                            val iterator = fields.iterator()
                            do {
                                builder.append(iterator.next().textRepresentation())
                                if (iterator.hasNext()) builder.append(", ")
                            } while (iterator.hasNext())
                            holder?.createErrorAnnotation(rangeToReport, builder.toString())
                        }
                        InstanceQuickFixAnnotation.REPLACE_WITH_IMPLEMENTATION_INFO -> {
                            holder?.createInfoAnnotation(rangeToReport, CAN_BE_REPLACED_WITH_IMPLEMENTATION)
                        }
                        InstanceQuickFixAnnotation.NO_ANNOTATION -> {
                            if (classReferenceHolder is PsiElement) classReferenceHolder.putUserData(CoClausesKey.INSTANCE, fieldsList)
                            null
                        }
                    }

                    val quickFix = ImplementFieldsQuickFix(this, fieldsList, actionText)
                    annotation?.registerFix(quickFix)
                    return fieldsList
                } else if (annotationToShow == InstanceQuickFixAnnotation.NO_ANNOTATION &&
                        classReferenceHolder is PsiElement) classReferenceHolder.putUserData(CoClausesKey.INSTANCE, null)
            }
        }
        return emptyList()
    }

    companion object {
        private const val IMPLEMENT_FIELDS_MSG = "The following fields are not implemented: "
        private const val CAN_BE_REPLACED_WITH_IMPLEMENTATION = "Goal can be replaced with class implementation"
        private const val REPLACE_WITH_IMPLEMENTATION = "Replace {?} with the implementation of the class"
        const val IMPLEMENT_MISSING_FIELDS = "Implement missing fields"
        const val REMOVE_COCLAUSE = "Remove redundant coclause"

        private val GOAL_IN_COPATTERN = withAncestors(ArendLiteral::class.java, ArendAtom::class.java, ArendAtomFieldsAcc::class.java,
                ArendArgumentAppExpr::class.java, ArendNewExpr::class.java, ArendCoClause::class.java)

        private fun isEmptyGoal(element: PsiElement): Boolean {
            val goal: ArendGoal? = element.childOfType()
            return goal != null && GOAL_IN_COPATTERN.accepts(goal)
        }

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
    }
}

class InstanceQuickFix {
    companion object {
        fun annotateFunctionDefinitionWithCoWith(functionDefinition: ArendDefFunction, holder: AnnotationHolder): Boolean {
            val coWithKw = functionDefinition.functionBody?.cowithKw
            return coWithKw != null && !FunctionDefinitionAnnotator(functionDefinition, coWithKw).doAnnotate(holder, IMPLEMENT_MISSING_FIELDS).isEmpty()
        }

        fun annotateClassInstance(instance: InstanceAdapter, holder: AnnotationHolder): Boolean {
            val classReference = instance.classReference
            val needsChecking = instance.instanceBody.let { it == null || it.fatArrow == null && it.elim == null }
            if (needsChecking && classReference is ArendDefClass && classReference.recordKw == null || classReference is TCReferable && (TypeCheckingService.getInstance(instance.project).typecheckerState.getTypechecked(classReference) as? ClassDefinition)?.isRecord == false) {
                val returnExpr = instance.returnExpr
                if (returnExpr != null)
                    return !ArendInstanceAnnotator(instance, returnExpr).doAnnotate(holder, IMPLEMENT_MISSING_FIELDS).isEmpty()
            }
            return false
        }

        fun annotateClassImplement(classImpl: ArendClassImplement, holder: AnnotationHolder): Boolean {
            val classReference = classImpl.classReference
            if (classReference is ArendDefClass && classImpl.fatArrow == null) {
                return !ClassImplementAnnotator(classImpl).doAnnotate(holder, IMPLEMENT_MISSING_FIELDS).isEmpty()
            }
            return false
        }

        fun annotateNewExpr(newExpr: ArendNewExprImplMixin, holder: AnnotationHolder): Boolean {
            val argumentAppExpr = newExpr.getArgumentAppExpr()
            return argumentAppExpr != null && !NewExprAnnotator(newExpr, argumentAppExpr).doAnnotate(holder, "").isEmpty()
        }
    }
}

class ClassImplementAnnotator(classImplement: ArendClassImplement) :
        CoClauseBaseAnnotator(classImplement, classImplement.parent?.prevSibling, classImplement.getLongName().textRange, InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR)

open class CoClauseAnnotator(coClause: ArendCoClause,
                             rangeToReport: TextRange,
                             isError: InstanceQuickFixAnnotation) :
        CoClauseBaseAnnotator(coClause, coClause.prevSibling, rangeToReport, isError)

abstract class CoClauseBaseAnnotator(private val coClause: CoClauseBase,
                                     private val anchor: PsiElement?,
                                     rangeToReport: TextRange,
                                     isError: InstanceQuickFixAnnotation) :
        AbstractEWCCAnnotator(coClause, rangeToReport, isError) {
    override fun coClausesList(): List<ArendCoClause> = coClause.getCoClauseList()

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        var anchor: PsiElement

        val lBrace = coClause.getLbrace()
        if (lBrace == null) {
            anchor = coClause.getLongName()!!
            val braces = factory.createPairOfBraces()
            anchor.parent.addAfter(braces.second, anchor)
            anchor.parent.addAfter(braces.first, anchor)
            anchor.parent.addAfter(factory.createWhitespace(" "), anchor) //separator between lBrace and coClause name
            anchor = anchor.nextSibling.nextSibling
        } else {
            anchor = lBrace
        }

        val sampleCoClause = factory.createCoClause(name, "{?}")
        anchor.parent.addAfterWithNotification(sampleCoClause, anchor)
        moveCaretToEndOffset(editor, anchor.nextSibling)

        anchor.parent.addAfter(factory.createWhitespace("\n"), anchor)
    }
}

class FunctionDefinitionAnnotator(private val functionDefinition: ArendDefFunction, coWithKw: PsiElement) :
        AbstractEWCCAnnotator(functionDefinition,
                TextRange(functionDefinition.textRange.startOffset, coWithKw.textRange.endOffset)) {

    override fun coClausesList(): List<ArendCoClause> = functionDefinition.functionBody?.coClauseList
            ?: emptyList()

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        var functionBody = functionDefinition.functionBody
        if (functionBody == null) {
            val functionBodySample = factory.createCoClauseInFunction(name, "{?}").parent as ArendFunctionBody
            functionBody = functionDefinition.addAfterWithNotification(functionBodySample, functionDefinition.children.last()) as ArendFunctionBody
            functionDefinition.addBefore(factory.createWhitespace("\n"), functionBody)
            moveCaretToEndOffset(editor, functionBody.lastChild)
        } else {
            val sampleCoClause = factory.createCoClause(name, "{?}")
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

class ArendInstanceAnnotator(private val instance: InstanceAdapter, returnExpr: ArendReturnExpr) :
        AbstractEWCCAnnotator(instance, TextRange(instance.instanceKw.textRange.startOffset, returnExpr.textRange.endOffset)) {

    override fun coClausesList(): List<ArendCoClause> = instance.instanceBody?.coClauseList ?: emptyList()

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        var instanceBody = instance.instanceBody
        if (instanceBody == null) {
            val instanceBodySample = factory.createCoClause(name, "{?}").parent as ArendInstanceBody
            val anchor = if (instance.returnExpr != null) instance.returnExpr else instance.defIdentifier
            instanceBody = instance.addAfterWithNotification(instanceBodySample, anchor) as ArendInstanceBody
            instance.addBefore(factory.createWhitespace("\n"), instanceBody)
            moveCaretToEndOffset(editor, instanceBody.lastChild)
        } else {
            val sampleCoClause = factory.createCoClause(name, "{?}")
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
        AbstractEWCCAnnotator(newExpr, argumentAppExpr.textRange, InstanceQuickFixAnnotation.NO_ANNOTATION, newExpr.getNewKw() == null) {
    override fun coClausesList(): List<ArendCoClause> = newExpr.getCoClauseList()

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        val lbrace = newExpr.getLbrace()
        val anchor: PsiElement = if (lbrace != null) lbrace else {
            val braces = factory.createPairOfBraces()
            argumentAppExpr.parent.addAfter(braces.second, argumentAppExpr)
            argumentAppExpr.parent.addAfter(braces.first, argumentAppExpr)
            argumentAppExpr.parent.addAfter(factory.createWhitespace(" "), argumentAppExpr) //separator between name and lbrace
            argumentAppExpr.nextSibling.nextSibling
        }

        val sampleCoClause = factory.createCoClause(name, "{?}")
        anchor.parent.addAfterWithNotification(sampleCoClause, anchor)

        moveCaretToEndOffset(editor, anchor.nextSibling)
        anchor.parent.addAfter(factory.createWhitespace("\n"), anchor)
    }
}

class RemoveCoClause(private val coClause: ArendCoClause) : IntentionAction {
    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

    override fun getText() = REMOVE_COCLAUSE

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        moveCaretToStartOffset(editor, coClause)
        val parent = coClause.parent
        coClause.deleteWithNotification()
        if ((parent is ArendFunctionBody || parent is ArendInstanceBody) && parent.firstChild == null) parent.deleteWithNotification()
    }
}

class ImplementFieldsQuickFix(val instance: AbstractEWCCAnnotator, private val fieldsToImplement: List<Pair<LocatedReferable, Boolean>>, private val actionText: String) : IntentionAction, Iconable {
    private var caretMoved = false

    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

    override fun getText() = actionText

    private fun addField(field: Referable, editor: Editor?, psiFactory: ArendPsiFactory, needQualifiedName: Boolean = false) {
        val coClauses = instance.coClausesList()
        val fieldClass = (field as? LocatedReferable)?.locatedReferableParent
        val name = if (needQualifiedName && fieldClass != null) "${fieldClass.textRepresentation()}.${field.textRepresentation()}" else field.textRepresentation()

        if (coClauses.isEmpty()) {
            instance.insertFirstCoClause(name, psiFactory, editor)
            caretMoved = true
        } else {
            val anchor = coClauses.last()
            val coClause = psiFactory.createCoClause(name, "{?}")

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
        for (f in fieldsToImplement) addField(f.first, editor, psiFactory, f.second)

        if (instance.coClausesList().isNotEmpty()) { // Add CRLF after last coclause
            val lastCC = instance.coClausesList().last()
            if (lastCC.nextSibling != null &&
                    lastCC.nextSibling.node.elementType == ArendElementTypes.RBRACE) {
                lastCC.parent.addAfter(psiFactory.createWhitespace("\n"), lastCC)
            }
        }

    }

    override fun getIcon(flags: Int) = if (instance.annotationToShow == InstanceQuickFixAnnotation.IMPLEMENT_FIELDS_ERROR) null else AllIcons.Actions.IntentionBulb
}

class CoClausesKey: Key<List<Pair<FieldReferable, Boolean>>>("coClausesInfo") {
    companion object {
        val INSTANCE = CoClausesKey()
    }
}