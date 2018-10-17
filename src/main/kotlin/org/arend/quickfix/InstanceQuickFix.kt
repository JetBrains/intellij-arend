package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.icons.AllIcons
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import org.arend.codeInsight.completion.ArendCompletionContributor
import org.arend.core.definition.ClassDefinition
import org.arend.naming.reference.*
import org.arend.psi.*
import org.arend.psi.ext.ArendNewExprImplMixin
import org.arend.psi.ext.impl.InstanceAdapter
import org.arend.quickfix.AbstractEWCCAnnotator.Companion.IMPLEMENT_MISSING_FIELDS
import org.arend.quickfix.AbstractEWCCAnnotator.Companion.moveCaretToEndOffset
import org.arend.typechecking.TypeCheckingService

enum class AnnotationSeverity {
    ERROR,
    WEAK_WARNING,
    NO_ANNOTATION
}

abstract class AbstractEWCCAnnotator(private val classReferenceHolder: ClassReferenceHolder,
                                     private val rangeToReport: TextRange,
                                     val severity: AnnotationSeverity = AnnotationSeverity.ERROR,
                                     private val onlyCheckFields: Boolean = false) {
    abstract fun calculateWhiteSpace(): String
    abstract fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?)
    abstract fun coClausesList(): List<ArendCoClause>

    private fun annotateClauses(coClauseList: List<ArendCoClause>, holder: AnnotationHolder?, superClassesFields: HashMap<ClassReferable, MutableSet<FieldReferable>>, fields: MutableSet<FieldReferable>) {
        for (coClause in coClauseList) {
            val referable = coClause.getLongName()?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? LocatedReferable
                    ?: continue
            val underlyingRef = referable.underlyingReference ?: referable

            if (underlyingRef is ClassReferable) {
                if (!underlyingRef.isSynonym) {
                    val subClauses = if (coClause.fatArrow != null) {
                        val superClassFields = superClassesFields[underlyingRef]
                        if (superClassFields != null && !superClassFields.isEmpty()) {
                            fields.removeAll(superClassFields)
                            continue
                        }
                        emptyList()
                    } else {
                        coClause.getCoClauseList()
                    }

                    if (subClauses.isEmpty()) {
                        val warningAnnotation = holder?.createWeakWarningAnnotation(coClause, "Clause is redundant")
                        if (warningAnnotation != null) {
                            warningAnnotation.highlightType = ProblemHighlightType.LIKE_UNUSED_SYMBOL
                            warningAnnotation.registerFix(object : IntentionAction {
                                override fun startInWriteAction() = true

                                override fun getFamilyName() = "arend.instance"

                                override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

                                override fun getText() = REMOVE_CLAUSE

                                override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                                    var startChild: PsiElement = coClause
                                    if (startChild.prevSibling is PsiWhiteSpace) startChild = startChild.prevSibling
                                    if (startChild.prevSibling != null) moveCaretToEndOffset(editor, startChild.prevSibling)

                                    coClause.parent.deleteChildRange(startChild, coClause)
                                }
                            })

                            val fieldToImplement = superClassesFields[underlyingRef]
                            if (coClause.fatArrow == null && coClause.expr == null && fieldToImplement != null && !fieldToImplement.isEmpty()) {
                                val rangeToReport = coClause.getLongName()?.textRange ?: coClause.textRange
                                val renamings = ClassReferable.Helper.getRenamings(underlyingRef)
                                warningAnnotation.registerFix(ImplementFieldsQuickFix(
                                    CoClauseAnnotator(coClause, rangeToReport, AnnotationSeverity.WEAK_WARNING),
                                    fieldToImplement.map { renamings[it]?.firstOrNull() ?: it }, IMPLEMENT_MISSING_FIELDS))
                            }
                        }
                    } else {
                        annotateClauses(subClauses, holder, superClassesFields, fields)
                    }
                }
                continue
            }

            if (!fields.remove(underlyingRef)) {
                holder?.createErrorAnnotation(coClause, "Field ${referable.textRepresentation()} is already implemented")
            }
            for (superClassFields in superClassesFields.values) {
                superClassFields.remove(underlyingRef)
            }

            val expr = coClause.expr
            val fatArrow = coClause.fatArrow
            val clauseBlock = fatArrow == null
            val emptyGoal = expr != null && isEmptyGoal(expr)

            if (clauseBlock || emptyGoal) {
                val rangeToReport = if (emptyGoal) coClause.textRange else coClause.getLongName()?.textRange ?: coClause.textRange
                val message = if (clauseBlock) IMPLEMENT_MISSING_FIELDS else REPLACE_WITH_IMPLEMENTATION
                val severity = if (clauseBlock) AnnotationSeverity.ERROR else AnnotationSeverity.WEAK_WARNING
                (object : CoClauseAnnotator(coClause, rangeToReport, severity) {
                    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
                        if (emptyGoal) coClause.deleteChildRange(fatArrow, expr)
                        super.insertFirstCoClause(name, factory, editor)
                    }
                }).doAnnotate(holder, message)
            }
        }
    }

    fun doAnnotate(holder: AnnotationHolder?, actionText: String): Map<FieldReferable, List<LocatedReferable>> {
        val superClassesFields = HashMap<ClassReferable, MutableSet<FieldReferable>>()
        val classReferenceData = classReferenceHolder.getClassReferenceData()
        if (classReferenceData != null) {
            val fields = ClassReferable.Helper.getNotImplementedFields(classReferenceData.classRef, classReferenceData.argumentsExplicitness, superClassesFields)
            for (field in classReferenceData.implementedFields) {
                fields.remove(field.underlyingReference ?: field)
            }

            annotateClauses(coClausesList(), holder, superClassesFields, fields.keys)

            if (fields.isNotEmpty() && !onlyCheckFields) {
                val builder = StringBuilder()
                val annotation = when (severity) {
                    AnnotationSeverity.ERROR -> {
                        builder.append(IMPLEMENT_FIELDS_MSG)
                        val iterator = fields.iterator()
                        do {
                            builder.append(iterator.next().value[0].textRepresentation())
                            if (iterator.hasNext()) builder.append(", ")
                        } while (iterator.hasNext())
                        holder?.createErrorAnnotation(rangeToReport, builder.toString())
                    }
                    AnnotationSeverity.WEAK_WARNING -> {
                        holder?.createWeakWarningAnnotation(rangeToReport, CAN_BE_REPLACED_WITH_IMPLEMENTATION)
                    }
                    AnnotationSeverity.NO_ANNOTATION -> null
                }

                val quickFix = ImplementFieldsQuickFix(this, fields.values.mapNotNull { it.firstOrNull() }, actionText)
                annotation?.registerFix(quickFix)
            }
            return fields
        }
        return emptyMap()
    }

    companion object {
        private const val IMPLEMENT_FIELDS_MSG = "The following fields are not implemented: "
        private const val CAN_BE_REPLACED_WITH_IMPLEMENTATION = "Goal can be replaced with class implementation"
        private const val REPLACE_WITH_IMPLEMENTATION = "Replace {?} with the implementation of the class"
        const val IMPLEMENT_MISSING_FIELDS = "Implement missing fields"
        const val INCREASE_IN_INDENT = "  "
        const val REMOVE_CLAUSE = "Remove redundant clause"

        private fun isEmptyGoal(element: PsiElement): Boolean {
            val goal: ArendGoal? = element.childOfType()
            return goal != null && ArendCompletionContributor.GOAL_IN_COPATTERN.accepts(goal)
        }

        fun moveCaretToEndOffset(editor: Editor?, anchor: PsiElement) {
            if (editor != null) {
                editor.caretModel.moveToOffset(anchor.textRange.endOffset)
                IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
            }
        }
    }
}

class InstanceQuickFix {
    companion object {
        fun getIndent(str : String, defaultIndent: String, increaseInIndent: String): String {
            var myStr = str
            if (myStr.indexOf('\n') == -1) return defaultIndent
            while (myStr.indexOf('\n') != -1) myStr = myStr.substring(myStr.indexOf('\n')+1)
            return myStr + increaseInIndent
        }

        fun annotateFunctionDefinitionWithCoWith(functionDefinition: ArendDefFunction, holder: AnnotationHolder): Boolean {
            val coWithKw = functionDefinition.functionBody?.cowithKw
            return coWithKw != null && !FunctionDefinitionAnnotator(functionDefinition, coWithKw).doAnnotate(holder, IMPLEMENT_MISSING_FIELDS).isEmpty()
        }

        fun annotateClassInstance(instance: InstanceAdapter, holder: AnnotationHolder): Boolean {
            val classReference = instance.classReference
            if (classReference is ArendDefClass && classReference.recordKw == null || classReference is TCReferable && (TypeCheckingService.getInstance(instance.project).typecheckerState.getTypechecked(classReference) as? ClassDefinition)?.isRecord == false) {
                val argumentAppExpr = instance.argumentAppExpr
                if (argumentAppExpr != null)
                    return !ArendInstanceAnnotator(instance, argumentAppExpr).doAnnotate(holder, IMPLEMENT_MISSING_FIELDS).isEmpty()
            }
            return false
        }

        fun annotateClassImplement(classImpl: ArendClassImplement, holder: AnnotationHolder): Boolean {
            val classReference = classImpl.classReference
            if (classReference is ArendDefClass) {
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


class ClassImplementAnnotator(classImplement: ArendClassImplement):
        CoClauseBaseAnnotator(classImplement, classImplement.parent.prevSibling, classImplement.getLongName().textRange, AnnotationSeverity.ERROR)

open class CoClauseAnnotator(coClause: ArendCoClause,
                              rangeToReport: TextRange,
                              isError: AnnotationSeverity):
        CoClauseBaseAnnotator(coClause, coClause.prevSibling, rangeToReport, isError)


abstract class CoClauseBaseAnnotator(private val coClause: CoClauseBase,
                                     private val anchor: PsiElement?,
                                     rangeToReport: TextRange,
                                     isError: AnnotationSeverity):
        AbstractEWCCAnnotator(coClause, rangeToReport, isError) {
    override fun calculateWhiteSpace() : String {
        val defaultIndent = "  "
        return if (anchor is PsiWhiteSpace) InstanceQuickFix.getIndent(anchor.text, defaultIndent, INCREASE_IN_INDENT) else defaultIndent
    }

    override fun coClausesList(): List<ArendCoClause> = coClause.getCoClauseList()

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        val whitespace = calculateWhiteSpace()
        var anchor: PsiElement

        val lBrace = coClause.getLbrace()
        if (lBrace == null) {
            anchor = coClause.getLongName()!!
            val pOB = factory.createPairOfBraces()
            anchor.parent.addAfter(pOB.second, anchor)
            anchor.parent.addAfter(pOB.first, anchor)
            anchor.parent.addAfter(factory.createWhitespace(" "), anchor) //separator between lBrace and coClause name
            anchor = anchor.nextSibling.nextSibling
        } else {
            anchor = lBrace
        }

        val sampleCoClause = factory.createCoClause(name, "{?}").coClauseList.first()
        anchor.parent.addAfter(sampleCoClause, anchor)
        moveCaretToEndOffset(editor, anchor.nextSibling)

        anchor.parent.addAfter(factory.createWhitespace("\n$whitespace"), anchor)
    }
}

class FunctionDefinitionAnnotator(private val functionDefinition: ArendDefFunction, private val coWithKw: PsiElement):
        AbstractEWCCAnnotator(functionDefinition,
                TextRange(functionDefinition.textRange.startOffset, coWithKw.textRange.endOffset)) {
    override fun calculateWhiteSpace(): String {
        val defaultWhitespace = INCREASE_IN_INDENT
        return if (functionDefinition.parent is ArendStatement && functionDefinition.parent.prevSibling is PsiWhiteSpace)
            InstanceQuickFix.getIndent(functionDefinition.parent.prevSibling.text, defaultWhitespace, INCREASE_IN_INDENT) else defaultWhitespace
    }

    override fun coClausesList(): List<ArendCoClause> = functionDefinition.functionBody?.coClauses?.coClauseList ?: emptyList()

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        val whitespace = calculateWhiteSpace()
        var nodeCoClauses = functionDefinition.functionBody?.coClauses
        if (nodeCoClauses == null) {
            val sampleCoClauses = factory.createCoClause(name, "{?}")
            val functionBody = functionDefinition.functionBody!!
            functionBody.addAfter(sampleCoClauses, coWithKw)
            nodeCoClauses = functionDefinition.functionBody?.coClauses!!
            val firstCoClause = nodeCoClauses.coClauseList.first()
            nodeCoClauses.addBefore(factory.createWhitespace(" "), firstCoClause)
            nodeCoClauses.addBefore(factory.createWhitespace("\n"+whitespace), firstCoClause) // add first clause and crlf
            moveCaretToEndOffset(editor, nodeCoClauses.coClauseList.last())
        } else if (nodeCoClauses.lbrace != null) {
            val sampleCoClause = factory.createCoClause(name, "{?}").coClauseList[0]!!
            val anchor = nodeCoClauses.lbrace
            nodeCoClauses.addAfter(sampleCoClause, anchor)
            nodeCoClauses.addAfter(factory.createWhitespace("\n"+whitespace), anchor)
            val caretAnchor = functionDefinition.functionBody?.coClauses?.coClauseList?.first()
            if (caretAnchor != null)
                moveCaretToEndOffset(editor, caretAnchor)
        }
    }
}

class ArendInstanceAnnotator(private val instance: InstanceAdapter, argumentAppExpr: ArendArgumentAppExpr):
        AbstractEWCCAnnotator(instance,
                TextRange(instance.instanceKw.textRange.startOffset, argumentAppExpr.textRange.endOffset)) {
    override fun calculateWhiteSpace(): String {
        val defaultWhitespace = INCREASE_IN_INDENT
        return if (instance.parent is ArendStatement && instance.parent.prevSibling is PsiWhiteSpace)
            InstanceQuickFix.getIndent(instance.parent.prevSibling.text, defaultWhitespace, INCREASE_IN_INDENT) else defaultWhitespace
    }

    override fun coClausesList(): List<ArendCoClause> = instance.coClauses?.coClauseList ?: emptyList()

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        val whitespace = calculateWhiteSpace()
        var nodeCoClauses = instance.coClauses
        if (nodeCoClauses == null) {
            val sampleCoClauses = factory.createCoClause(name, "{?}")
            instance.addAfter(sampleCoClauses, instance.argumentAppExpr)
            nodeCoClauses = instance.coClauses!!
            nodeCoClauses.parent.addBefore(factory.createWhitespace("\n"+whitespace), nodeCoClauses)
            moveCaretToEndOffset(editor, nodeCoClauses.lastChild)
        } else if (nodeCoClauses.lbrace != null) {
            val sampleCoClause = factory.createCoClause(name, "{?}").coClauseList[0]!!
            val anchor = nodeCoClauses.lbrace
            nodeCoClauses.addAfter(sampleCoClause, anchor)
            nodeCoClauses.addAfter(factory.createWhitespace("\n"+whitespace), anchor)
            val caretAnchor = instance.coClauses?.coClauseList?.first()
            if (caretAnchor != null)
                moveCaretToEndOffset(editor, caretAnchor)
        }
    }
}

class NewExprAnnotator(private val newExpr: ArendNewExprImplMixin, private val argumentAppExpr: ArendArgumentAppExpr):
        AbstractEWCCAnnotator(newExpr, argumentAppExpr.textRange, AnnotationSeverity.NO_ANNOTATION, newExpr.getNewKw() == null) {
    override fun coClausesList(): List<ArendCoClause> = newExpr.getCoClauseList()

    override fun calculateWhiteSpace(): String {
        val defaultWhitespace = "  "
        return if (newExpr.prevSibling is PsiWhiteSpace)
            InstanceQuickFix.getIndent(newExpr.prevSibling.text, defaultWhitespace, INCREASE_IN_INDENT) else defaultWhitespace
    }

    override fun insertFirstCoClause(name: String, factory: ArendPsiFactory, editor: Editor?) {
        val whitespace = calculateWhiteSpace()
        val lbrace = newExpr.getLbrace()
        val anchor : PsiElement = if (lbrace != null) lbrace else {
            val pOB = factory.createPairOfBraces()
            argumentAppExpr.parent.addAfter(pOB.second, argumentAppExpr)
            argumentAppExpr.parent.addAfter(pOB.first, argumentAppExpr)
            argumentAppExpr.parent.addAfter(factory.createWhitespace(" "), argumentAppExpr) //separator between name and lbrace
            argumentAppExpr.nextSibling.nextSibling
        }

        val sampleCoClause = factory.createCoClause(name, "{?}").coClauseList.first()
        anchor.parent.addAfter(sampleCoClause, anchor)

        moveCaretToEndOffset(editor, anchor.nextSibling)
        anchor.parent.addAfter(factory.createWhitespace("\n"+whitespace), anchor)
    }
}

class ImplementFieldsQuickFix(val instance: AbstractEWCCAnnotator, private val fieldsToImplement: Collection<Referable>, private val actionText: String): IntentionAction, Iconable {
    private var caretMoved = false

    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true
    //instance.getPsiElement().isValid && !fieldsToImplement.isEmpty()

    override fun getText() = actionText

    private fun addField(field: Referable, whitespace: String, editor: Editor?, psiFactory: ArendPsiFactory) {
        val coClauses = instance.coClausesList()
        if (coClauses.isEmpty()) {
            instance.insertFirstCoClause(field.textRepresentation(), psiFactory, editor)
            caretMoved = true
        } else {
            val anchor = coClauses.last()

            val sampleCoClauses = psiFactory.createCoClause(field.textRepresentation(), "{?}")
            val coClause = sampleCoClauses.coClauseList.first()!!
            val clauseWhitespace = when {
                anchor.prevSibling is PsiWhiteSpace -> InstanceQuickFix.getIndent(anchor.prevSibling.text, whitespace, "")
                anchor.parent.prevSibling is PsiWhiteSpace -> InstanceQuickFix.getIndent(anchor.parent.prevSibling.text, whitespace, "")
                else -> whitespace
            }

            anchor.parent.addAfter(coClause, anchor)
            if (!caretMoved && editor != null) {
                moveCaretToEndOffset(editor, anchor.nextSibling)
                caretMoved = true
            }
            anchor.parent.addAfter(psiFactory.createWhitespace("\n"+clauseWhitespace), anchor)
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val whitespace = instance.calculateWhiteSpace()
        val psiFactory = ArendPsiFactory(project)
        for (f in fieldsToImplement) addField(f, whitespace, editor, psiFactory)

        if (instance.coClausesList().isNotEmpty()) { // Add CRLF + indent after the last coclause
            val lastCC = instance.coClausesList().last()
            if (lastCC.nextSibling != null &&
                    lastCC.nextSibling.node.elementType == ArendElementTypes.RBRACE) {
                lastCC.parent.addAfter(psiFactory.createWhitespace("\n"+whitespace), lastCC)
            } else if (lastCC.nextSibling != null &&
                    lastCC.nextSibling.node is PsiWhiteSpace && !lastCC.nextSibling.text.contains('\n') &&
                    (lastCC.nextSibling.text.length < whitespace.length)) {
                lastCC.nextSibling.delete()
                lastCC.parent.addAfter(psiFactory.createWhitespace("\n"+whitespace), lastCC)
            }
        }

    }

    override fun getIcon(flags: Int) = if (instance.severity == AnnotationSeverity.ERROR) null else AllIcons.Actions.IntentionBulb
}