package org.vclang.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.reference.TypedReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.ClassFieldImplScope
import org.vclang.codeInsight.completion.VclangCompletionContributor
import org.vclang.psi.*
import org.vclang.psi.ext.impl.InstanceAdapter

interface ExpressionWithCoClauses {
    fun getRangeToReport(): TextRange
    fun getCoClauseList(): List<VcCoClause>
    fun isValid(): Boolean
    fun calculateWhiteSpace(): String
    fun insertFirstCoClause(name: String, factory: VcPsiFactory)
    fun isError(): Boolean
}

class InstanceQuickFix {
    companion object {
        private const val IMPLEMENT_FIELDS_MSG = "The following fields are not implemented: "
        private const val CAN_BE_REPLACED_WITH_IMPLEMENTATION = "Goal can be replaced with class implementation"
        private const val REPLACE_WITH_IMPLEMENTATION = "Replace with the implementation of the class"
        private const val IMPLEMENT_MISSING_FIELDS = "Implement missing fields"

        fun getIndent(str : String, defaultIndent: String, increaseInIndent: String): String {
            var myStr = str
            if (myStr.indexOf('\n') == -1) return defaultIndent
            while (myStr.indexOf('\n') != -1) myStr = myStr.substring(myStr.indexOf('\n')+1)
            return myStr + increaseInIndent
        }

        fun isEmptyGoal(element: PsiElement): Boolean {
            val goal: VcGoal? = element.childOfType()
            return (goal != null) && VclangCompletionContributor.GOAL_IN_COPATTERN.accepts(goal) &&
                    goal.firstChild.node.elementType == VcElementTypes.LGOAL
        }


        fun doAnnotate(expression: ExpressionWithCoClauses, classReference: ClassReferable, coClauseList: List<VcCoClause>, holder: AnnotationHolder){
            val fields = ClassFieldImplScope(classReference, false).elements
            val implementedFields =  coClauseList.map { ExpressionResolveNameVisitor.resolve(it.longName.referent, it.longName.scope) }.toSet()
            val unimplementedFields = fields.minus(implementedFields.asSequence())
            if (unimplementedFields.isNotEmpty()) {
                val builder = StringBuilder()
                val actionText = if (expression.isError()) IMPLEMENT_MISSING_FIELDS else REPLACE_WITH_IMPLEMENTATION
                val annotation = if (expression.isError()) {
                    builder.append(IMPLEMENT_FIELDS_MSG)
                    val iterator = unimplementedFields.iterator()
                    do {
                        builder.append(iterator.next().textRepresentation())
                        if (iterator.hasNext()) builder.append(", ")
                    } while (iterator.hasNext())
                    holder.createErrorAnnotation(expression.getRangeToReport(), builder.toString())
                } else {
                    holder.createWeakWarningAnnotation(expression.getRangeToReport(), CAN_BE_REPLACED_WITH_IMPLEMENTATION)
                }

                annotation.registerFix(ImplementFieldsQuickFix(expression, unimplementedFields, actionText))
            }

            for (coClause in coClauseList) {
                val referable = ExpressionResolveNameVisitor.resolve(coClause.longName.referent, coClause.longName.scope)
                val expr = coClause.expr
                val fatArrow = coClause.fatArrow
                val clauseBlock = fatArrow == null && expr == null
                val emptyGoal = fatArrow != null && expr != null && isEmptyGoal(expr)
                if (referable is TypedReferable && (clauseBlock || emptyGoal)) {
                    val typeClassReference = referable.typeClassReference
                    if (typeClassReference != null) doAnnotate(object: ExpressionWithCoClauses {
                        override fun isError() = clauseBlock

                        override fun getRangeToReport() = if (emptyGoal && expr != null /* tautologically true*/) expr.textRange else coClause.longName.textRange

                        override fun getCoClauseList() = coClause.coClauseList

                        override fun isValid() = coClause.isValid

                        override fun calculateWhiteSpace() : String {
                            var anchor = coClause.prevSibling
                            val defaultIndent = "  "
                            if (anchor is PsiWhiteSpace) anchor = anchor.prevSibling
                            return if (anchor.node.elementType == VcElementTypes.PIPE && anchor.prevSibling is PsiWhiteSpace)
                                getIndent(anchor.prevSibling.text, defaultIndent, " ") else defaultIndent
                        }

                        override fun insertFirstCoClause(name: String, factory: VcPsiFactory) {
                            val whitespace = calculateWhiteSpace()
                            var anchor: PsiElement
                            val nestedCoClause = factory.createNestedCoClause("foo").coClauseList.first()

                            if (emptyGoal) coClause.deleteChildRange(fatArrow, expr)

                            val lbrace = coClause.lbrace
                            if (lbrace == null) {
                                anchor = coClause.longName
                                anchor.parent.addAfter(nestedCoClause.rbrace!!, anchor)
                                anchor.parent.addAfter(nestedCoClause.lbrace!!, anchor)
                                anchor.parent.addAfter(factory.createWhitespace(" "), anchor)
                                anchor = coClause.longName.nextSibling.nextSibling
                            } else {
                                anchor = lbrace
                            }

                            val sampleCoClause = factory.createCoClause(name, "{?}").coClauseList.first()
                            val vBarSample = sampleCoClause.prevSibling.prevSibling
                            anchor.parent.addAfter(sampleCoClause, anchor)
                            anchor.parent.addAfter(factory.createWhitespace(" "), anchor)
                            anchor.parent.addAfter(vBarSample, anchor)

                            anchor.parent.addAfter(factory.createWhitespace("\n"+whitespace), anchor)
                        }
                    }, typeClassReference, coClause.coClauseList, holder)
                }
            }
        }

        fun annotateClassInstance(instance: InstanceAdapter, holder: AnnotationHolder) {
            val classReference = instance.classReference
            if (classReference != null && instance.coClauses != null) {

                val coClauses = instance.coClauses
                val argumentAppExpr = instance.argumentAppExpr
                if (coClauses != null && argumentAppExpr != null) {
                    doAnnotate(object: ExpressionWithCoClauses {
                        override fun isError() = true

                        override fun getRangeToReport(): TextRange = TextRange(instance.instanceKw.textRange.startOffset, argumentAppExpr.textRange.endOffset)

                        override fun getCoClauseList(): List<VcCoClause> = coClauses.coClauseList

                        override fun isValid() = instance.isValid

                        override fun calculateWhiteSpace(): String {
                            val defaultWhitespace = "  "
                            return if (instance.parent is VcStatement && instance.parent.prevSibling is PsiWhiteSpace)
                                getIndent(instance.parent.prevSibling.text, defaultWhitespace, " ") else defaultWhitespace
                        }

                        override fun insertFirstCoClause(name: String, factory: VcPsiFactory) {
                            val whitespace = calculateWhiteSpace()
                            val anchor : VcArgumentAppExpr = instance.argumentAppExpr ?: error("Can't find anchor within class instance")

                            val sampleCoClauses = factory.createCoClause(name, "{?}")
                            anchor.parent.addAfter(sampleCoClauses, anchor)
                            anchor.parent.addAfter(factory.createWhitespace("\n"+whitespace), anchor)
                        }
                    }, classReference, coClauses.coClauseList, holder)
                }
            }
        }
    }
}

class ImplementFieldsQuickFix(val instance: ExpressionWithCoClauses, val fieldsToImplement: List<Referable>, val actionText: String): IntentionAction {
    private var lastAddedCoClause: VcCoClause? = null

    override fun startInWriteAction() = true

    override fun getFamilyName() = "vclang.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = instance.isValid() && fieldsToImplement.isNotEmpty()

    override fun getText() = actionText

    private fun addField(field: Referable, project: Project, whitespace: String) {
        val psiFactory = VcPsiFactory(project)
        val coClauses = instance.getCoClauseList()
        if (coClauses.isEmpty()) {
            instance.insertFirstCoClause(field.textRepresentation(), psiFactory)
        } else {
            val anchor = coClauses.last()

            val sampleCoClauses = psiFactory.createCoClause(field.textRepresentation(), "{?}")
            val coClause = sampleCoClauses.coClauseList.first()!!
            val vBarSample = coClause.prevSibling.prevSibling
            val vBarPreClause = anchor.prevSibling.prevSibling
            val clauseWhitespace = when {
                vBarPreClause.prevSibling is PsiWhiteSpace -> InstanceQuickFix.getIndent(vBarPreClause.prevSibling.text, whitespace, "")
                coClause.parent.prevSibling is PsiWhiteSpace -> InstanceQuickFix.getIndent(coClause.parent.prevSibling.text, whitespace, "")
                else -> whitespace
            }

            lastAddedCoClause = coClause
            anchor.parent.addAfter(coClause, anchor)
            anchor.parent.addAfter(psiFactory.createWhitespace(" "), anchor)
            anchor.parent.addAfter(vBarSample, anchor)
            anchor.parent.addAfter(psiFactory.createWhitespace("\n"+clauseWhitespace), anchor)
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val whitespace = instance.calculateWhiteSpace()
        for (f in fieldsToImplement) addField(f, project, whitespace)
        val lastCC = lastAddedCoClause
        if (editor != null && lastCC != null) editor.caretModel.moveToOffset(lastCC.textRange.endOffset)
    }
}