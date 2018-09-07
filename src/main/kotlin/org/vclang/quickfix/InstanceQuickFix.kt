package org.vclang.quickfix

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
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.FieldReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.codeInsight.completion.VclangCompletionContributor
import org.vclang.psi.*
import org.vclang.psi.ext.VcNewExprImplMixin
import org.vclang.psi.ext.impl.InstanceAdapter
import org.vclang.quickfix.InstanceQuickFix.Companion.INCREASE_IN_INDENT
import org.vclang.quickfix.InstanceQuickFix.Companion.moveCaretToTheEnd

interface ExpressionWithCoClauses {
    fun getRangeToReport(): TextRange
    fun getClassReferenceHolder(): Abstract.ClassReferenceHolder
    fun getCoClauseList(): List<VcCoClause>
    fun calculateWhiteSpace(): String
    fun insertFirstCoClause(name: String, factory: VcPsiFactory, editor: Editor?)
    fun isError(): Boolean
    fun getPsiElement(): PsiElement
}

class InstanceQuickFix {
    companion object {
        private const val IMPLEMENT_FIELDS_MSG = "The following fields are not implemented: "
        private const val CAN_BE_REPLACED_WITH_IMPLEMENTATION = "Goal can be replaced with class implementation"
        private const val REPLACE_WITH_IMPLEMENTATION = "Replace with the implementation of the class"
        private const val IMPLEMENT_MISSING_FIELDS = "Implement missing fields"
        const val INCREASE_IN_INDENT  = "  "

        fun moveCaretToTheEnd(editor: Editor?, anchor: PsiElement) {
            if (editor != null) {
                editor.caretModel.moveToOffset(anchor.textRange.endOffset)
                IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
            }
        }

        fun getIndent(str : String, defaultIndent: String, increaseInIndent: String): String {
            var myStr = str
            if (myStr.indexOf('\n') == -1) return defaultIndent
            while (myStr.indexOf('\n') != -1) myStr = myStr.substring(myStr.indexOf('\n')+1)
            return myStr + increaseInIndent
        }

        private fun isEmptyGoal(element: PsiElement): Boolean {
            val goal: VcGoal? = element.childOfType()
            return goal != null && VclangCompletionContributor.GOAL_IN_COPATTERN.accepts(goal)
        }

        private fun doAnnotate(expression: ExpressionWithCoClauses, classReference: VcDefClass, holder: AnnotationHolder, onlyCheckFields: Boolean): Boolean {
            val superClassesFields = HashMap<ClassReferable, MutableSet<FieldReferable>>()
            val fields = ClassReferable.Helper.getNotImplementedFields(classReference, expression.getClassReferenceHolder().argumentsExplicitness, superClassesFields)

            annotateClauses(expression.getCoClauseList(), holder, superClassesFields, fields.keys)

            if (fields.isNotEmpty() && !onlyCheckFields) {
                val builder = StringBuilder()
                val actionText = if (expression.isError()) IMPLEMENT_MISSING_FIELDS else REPLACE_WITH_IMPLEMENTATION
                val annotation = if (expression.isError()) {
                    builder.append(IMPLEMENT_FIELDS_MSG)
                    val iterator = fields.iterator()
                    do {
                        builder.append(iterator.next().key.textRepresentation())
                        if (iterator.hasNext()) builder.append(", ")
                    } while (iterator.hasNext())
                    holder.createErrorAnnotation(expression.getRangeToReport(), builder.toString())
                } else {
                    holder.createWeakWarningAnnotation(expression.getRangeToReport(), CAN_BE_REPLACED_WITH_IMPLEMENTATION)
                }

                annotation.registerFix(ImplementFieldsQuickFix(expression, fields.values.mapNotNull { it.firstOrNull() }, actionText))
                return true
            } else {
                return false
            }
        }

        private fun annotateClauses(coClauseList: List<VcCoClause>, holder: AnnotationHolder, superClassesFields: HashMap<ClassReferable, MutableSet<FieldReferable>>, fields: MutableSet<FieldReferable>){
            val classClauses = ArrayList<Pair<VcDefClass,VcCoClause>>()
            for (coClause in coClauseList) {
                val referable = coClause.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? LocatedReferable ?: continue
                val underlyingRef = referable.underlyingReference ?: referable

                if (underlyingRef is VcDefClass) {
                    classClauses.add(Pair(underlyingRef,coClause))
                    continue
                }

                if (!fields.remove(underlyingRef)) {
                    holder.createErrorAnnotation(coClause, "Field ${underlyingRef.textRepresentation()} is already implemented")
                }
                for (superClassFields in superClassesFields.values) {
                    superClassFields.remove(underlyingRef)
                }

                val expr = coClause.expr
                val fatArrow = coClause.fatArrow
                val clauseBlock = fatArrow == null
                val emptyGoal = expr != null && isEmptyGoal(expr)

                if (clauseBlock || emptyGoal) {
                    val typeClassReference = underlyingRef.typeClassReference
                    if (typeClassReference is VcDefClass) doAnnotate(object : CoClauseAdapter(coClause) {
                        override fun isError() = clauseBlock

                        override fun getRangeToReport() = if (emptyGoal) coClause.textRange else coClause.longName?.textRange ?: coClause.textRange

                        override fun insertFirstCoClause(name: String, factory: VcPsiFactory, editor: Editor?) {
                            if (emptyGoal) coClause.deleteChildRange(fatArrow, expr)
                            super.insertFirstCoClause(name, factory, editor)
                        }
                    }, typeClassReference, holder, false)
                }
            }

            for ((underlyingRef,coClause) in classClauses) {
                val subClauses =
                    if (coClause.fatArrow != null) {
                        val superClassFields = superClassesFields[underlyingRef]
                        if (superClassFields != null && !superClassFields.isEmpty()) {
                            fields.removeAll(superClassFields)
                            continue
                        }
                        emptyList()
                    } else {
                        coClause.coClauseList
                    }

                if (subClauses.isEmpty()) {
                    val warningAnnotation = holder.createWeakWarningAnnotation(coClause, "Clause is redundant")
                    warningAnnotation.highlightType = ProblemHighlightType.LIKE_UNUSED_SYMBOL
                    warningAnnotation.registerFix(object: IntentionAction {
                        override fun startInWriteAction() = true

                        override fun getFamilyName() = "vclang.instance"

                        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

                        override fun getText() = "Remove redundant clause"

                        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                            var startChild: PsiElement = coClause
                            if (startChild.prevSibling is PsiWhiteSpace) startChild = startChild.prevSibling
                            if (startChild.prevSibling != null) moveCaretToTheEnd(editor, startChild.prevSibling)

                            coClause.parent.deleteChildRange(startChild, coClause)
                        }
                    })
                    val fieldToImplement = superClassesFields[underlyingRef]
                    if (coClause.fatArrow == null && coClause.expr == null && fieldToImplement != null) {
                        warningAnnotation.registerFix(ImplementFieldsQuickFix(object: CoClauseAdapter(coClause){
                            override fun getRangeToReport(): TextRange = coClause.longName?.textRange ?: coClause.textRange

                            override fun isError() = false
                        }, fieldToImplement.toSet(), "Implement fields"))
                    }
                } else {
                    annotateClauses(subClauses, holder, superClassesFields, fields)
                }
            }
        }

        fun annotateFunctionDefinitionWithCoWith(functionDefinition: VcDefFunction, holder: AnnotationHolder): Boolean {
            val classReference = functionDefinition.classReference
            val coWithKw = functionDefinition.functionBody?.cowithKw
            if (classReference is VcDefClass && coWithKw != null) {
                return doAnnotate(object: ExpressionWithCoClauses {
                    override fun getRangeToReport() = TextRange(functionDefinition.textRange.startOffset, coWithKw.textRange.endOffset)

                    override fun getClassReferenceHolder() = functionDefinition

                    override fun getPsiElement(): PsiElement = functionDefinition

                    override fun getCoClauseList(): List<VcCoClause> = functionDefinition.functionBody?.coClauses?.coClauseList ?: emptyList()

                    override fun calculateWhiteSpace(): String {
                        val defaultWhitespace = INCREASE_IN_INDENT
                        return if (functionDefinition.parent is VcStatement && functionDefinition.parent.prevSibling is PsiWhiteSpace)
                            getIndent(functionDefinition.parent.prevSibling.text, defaultWhitespace, INCREASE_IN_INDENT) else defaultWhitespace
                    }

                    override fun insertFirstCoClause(name: String, factory: VcPsiFactory, editor: Editor?) {
                        val whitespace = calculateWhiteSpace()
                        var nodeCoClauses = functionDefinition.functionBody?.coClauses
                        if (nodeCoClauses == null) {
                            val sampleCoClauses = factory.createCoClause(name, "{?}")
                            val functionBody = functionDefinition.functionBody!!
                            val pOB = factory.createPairOfBraces()
                            functionBody.addAfter(sampleCoClauses, coWithKw)
                            nodeCoClauses = functionDefinition.functionBody?.coClauses!!
                            val firstCoClause = nodeCoClauses.coClauseList.first()
                            nodeCoClauses.addBefore(factory.createWhitespace(" "), firstCoClause)
                            nodeCoClauses.addBefore(pOB.first, firstCoClause)
                            nodeCoClauses.addBefore(factory.createWhitespace("\n"+whitespace), firstCoClause) // add first clause and crlf
                            nodeCoClauses.addAfter(pOB.second, firstCoClause)
                            moveCaretToTheEnd(editor, nodeCoClauses.coClauseList.last())
                        } else if (nodeCoClauses.lbrace != null) {
                            val sampleCoClause = factory.createCoClause(name, "{?}").coClauseList[0]!!
                            val anchor = nodeCoClauses.lbrace
                            nodeCoClauses.addAfter(sampleCoClause, anchor)
                            nodeCoClauses.addAfter(factory.createWhitespace("\n"+whitespace), anchor)
                            val caretAnchor = functionDefinition.functionBody?.coClauses?.coClauseList?.first()
                            if (caretAnchor != null)
                                moveCaretToTheEnd(editor, caretAnchor)
                        }
                    }

                    override fun isError() = true

                }, classReference, holder, false)
            }
            return false
        }

        fun annotateClassInstance(instance: InstanceAdapter, holder: AnnotationHolder): Boolean {
            val classReference = instance.classReference
            if (classReference is VcDefClass && classReference.recordKw == null) {
                val argumentAppExpr = instance.argumentAppExpr
                if (argumentAppExpr != null) {
                    return doAnnotate(object: ExpressionWithCoClauses {
                        override fun isError() = true

                        override fun getRangeToReport(): TextRange = TextRange(instance.instanceKw.textRange.startOffset, argumentAppExpr.textRange.endOffset)

                        override fun getCoClauseList(): List<VcCoClause> = instance.coClauses?.coClauseList ?: emptyList()

                        override fun getClassReferenceHolder() = instance

                        override fun getPsiElement() = instance

                        override fun calculateWhiteSpace(): String {
                            val defaultWhitespace = INCREASE_IN_INDENT
                            return if (instance.parent is VcStatement && instance.parent.prevSibling is PsiWhiteSpace)
                                getIndent(instance.parent.prevSibling.text, defaultWhitespace, INCREASE_IN_INDENT) else defaultWhitespace
                        }

                        override fun insertFirstCoClause(name: String, factory: VcPsiFactory, editor: Editor?) {
                            val whitespace = calculateWhiteSpace()
                            var nodeCoClauses = instance.coClauses
                            if (nodeCoClauses == null) {
                                val sampleCoClauses = factory.createCoClause(name, "{?}")
                                instance.addAfter(sampleCoClauses, instance.argumentAppExpr)
                                nodeCoClauses = instance.coClauses!!
                                nodeCoClauses.parent.addBefore(factory.createWhitespace("\n"+whitespace), nodeCoClauses)
                                moveCaretToTheEnd(editor, nodeCoClauses.lastChild)
                            } else if (nodeCoClauses.lbrace != null) {
                                val sampleCoClause = factory.createCoClause(name, "{?}").coClauseList[0]!!
                                val anchor = nodeCoClauses.lbrace
                                nodeCoClauses.addAfter(sampleCoClause, anchor)
                                nodeCoClauses.addAfter(factory.createWhitespace("\n"+whitespace), anchor)
                                val caretAnchor = instance.coClauses?.coClauseList?.first()
                                if (caretAnchor != null)
                                    moveCaretToTheEnd(editor, caretAnchor)
                            }
                        }
                    }, classReference, holder, false)
                }
            }
            return false
        }

        fun annotateNewExpr(newExpr: VcNewExprImplMixin, holder: AnnotationHolder): Boolean {
            val classReference = newExpr.classReference
            if (classReference is VcDefClass) {
                val argumentAppExpr = newExpr.getArgumentAppExpr()
                if (argumentAppExpr != null) {
                    return doAnnotate(object: ExpressionWithCoClauses {
                        override fun isError() = true

                        override fun getRangeToReport(): TextRange = argumentAppExpr.textRange

                        override fun getCoClauseList(): List<VcCoClause> = newExpr.getCoClauseList()

                        override fun getClassReferenceHolder() = newExpr

                        override fun getPsiElement() = newExpr

                        override fun calculateWhiteSpace(): String {
                            val defaultWhitespace = "  "
                            return if (newExpr.prevSibling is PsiWhiteSpace)
                                getIndent(newExpr.prevSibling.text, defaultWhitespace, INCREASE_IN_INDENT) else defaultWhitespace
                        }

                        override fun insertFirstCoClause(name: String, factory: VcPsiFactory, editor: Editor?) {
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

                            moveCaretToTheEnd(editor, anchor.nextSibling)
                            anchor.parent.addAfter(factory.createWhitespace("\n"+whitespace), anchor)
                        }
                    }, classReference, holder, newExpr.getNewKw() == null)
                }
            }
            return false
        }
    }
}

abstract class CoClauseAdapter(private val coClause: VcCoClause) : ExpressionWithCoClauses{
    override fun getCoClauseList(): List<VcCoClause> = coClause.coClauseList

    override fun getClassReferenceHolder() = coClause

    override fun getPsiElement(): PsiElement = coClause

    override fun calculateWhiteSpace() : String {
        val anchor = coClause.prevSibling
        val defaultIndent = "  "
        return if (anchor is PsiWhiteSpace) InstanceQuickFix.getIndent(anchor.text, defaultIndent, INCREASE_IN_INDENT) else defaultIndent
    }

    override fun insertFirstCoClause(name: String, factory: VcPsiFactory, editor: Editor?) {
        val whitespace = calculateWhiteSpace()
        var anchor: PsiElement

        val lbrace = coClause.lbrace
        if (lbrace == null) {
            anchor = coClause.longName!!
            val pOB = factory.createPairOfBraces()
            anchor.parent.addAfter(pOB.second, anchor)
            anchor.parent.addAfter(pOB.first, anchor)
            anchor.parent.addAfter(factory.createWhitespace(" "), anchor) //separator between lbrace and coClause name
            anchor = anchor.nextSibling.nextSibling
        } else {
            anchor = lbrace
        }

        val sampleCoClause = factory.createCoClause(name, "{?}").coClauseList.first()
        anchor.parent.addAfter(sampleCoClause, anchor)
        moveCaretToTheEnd(editor, anchor.nextSibling)

        anchor.parent.addAfter(factory.createWhitespace("\n$whitespace"), anchor)
    }
}

class ImplementFieldsQuickFix(val instance: ExpressionWithCoClauses, private val fieldsToImplement: Collection<Referable>, private val actionText: String): IntentionAction, Iconable {
    private var caretMoved = false

    override fun startInWriteAction() = true

    override fun getFamilyName() = "vclang.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = instance.getPsiElement().isValid && !fieldsToImplement.isEmpty()

    override fun getText() = actionText

    private fun addField(field: Referable, whitespace: String, editor: Editor?, psiFactory: VcPsiFactory) {
        val coClauses = instance.getCoClauseList()
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
                moveCaretToTheEnd(editor, anchor.nextSibling)
                caretMoved = true
            }
            anchor.parent.addAfter(psiFactory.createWhitespace("\n"+clauseWhitespace), anchor)
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val whitespace = instance.calculateWhiteSpace()
        val psiFactory = VcPsiFactory(project)
        for (f in fieldsToImplement) addField(f, whitespace, editor, psiFactory)

        if (instance.getCoClauseList().isNotEmpty()) { // Add CRLF + indent after the last coclause
            val lastCC = instance.getCoClauseList().last()
            if (lastCC.nextSibling != null &&
                    lastCC.nextSibling.node.elementType == VcElementTypes.RBRACE) {
                lastCC.parent.addAfter(psiFactory.createWhitespace("\n"+whitespace), lastCC)
            } else if (lastCC.nextSibling != null &&
                    lastCC.nextSibling.node is PsiWhiteSpace && !lastCC.nextSibling.text.contains('\n') &&
                    (lastCC.nextSibling.text.length < whitespace.length)) {
                lastCC.nextSibling.delete()
                lastCC.parent.addAfter(psiFactory.createWhitespace("\n"+whitespace), lastCC)
            }
        }

    }

    override fun getIcon(flags: Int) = if (instance.isError()) null else AllIcons.Actions.IntentionBulb
}