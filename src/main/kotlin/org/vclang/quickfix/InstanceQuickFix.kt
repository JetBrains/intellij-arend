package org.vclang.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.ClassFieldImplScope
import org.vclang.psi.VcArgumentAppExpr
import org.vclang.psi.VcPsiFactory
import org.vclang.psi.VcStatement
import org.vclang.psi.ext.impl.InstanceAdapter

class InstanceQuickFix {
    companion object {
        fun annotateClassInstance(instance: InstanceAdapter, holder: AnnotationHolder) {
            val cr = instance.classReference
            if (cr != null && instance.coClauses != null) {
                val fields = ClassFieldImplScope(cr, false).elements
                val implementedFields =  instance.coClauses?.coClauseList?.map { ExpressionResolveNameVisitor.resolve(it.longName.referent, it.longName.scope) }?.toSet()
                if (implementedFields != null) {
                    val unimplementedFields = fields.minus(implementedFields.asSequence())
                    if (unimplementedFields.isNotEmpty()) {
                        val builder = StringBuilder()
                        builder.append("The following fields are not implemented: ")
                        val iterator = unimplementedFields.iterator()
                        do {
                            builder.append(iterator.next().textRepresentation())
                            if (iterator.hasNext()) builder.append(", ")
                        } while (iterator.hasNext())
                        val annotation = holder.createErrorAnnotation(instance, builder.toString())
                        annotation.registerFix(ImplementFieldsQuickFix(instance, unimplementedFields))
                    }
                }
            }
        }
    }
}

class ImplementFieldsQuickFix(val instance: InstanceAdapter, val fieldsToImplement: List<Referable>): IntentionAction {
    override fun startInWriteAction() = true

    override fun getFamilyName() = "vclang.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = instance.isValid && fieldsToImplement.isNotEmpty()

    override fun getText() = "Implement missing fields"

    private fun getIndent(str : String, defaultIndent: String): String {
        var myStr = str
        if (myStr.indexOf('\n') == -1) return defaultIndent
        while (myStr.indexOf('\n') != -1) myStr = myStr.substring(myStr.indexOf('\n')+1)
        return myStr
    }

    private fun addField(field: Referable, project: Project, whitespace: String) {
        val psiFactory = VcPsiFactory(project)
        val sampleCoClauses = psiFactory.createCoClause(field.textRepresentation(), "{?}")
        val coClauses = instance.coClauses
        if (coClauses == null || coClauses.coClauseList.isEmpty()) {
            val anchor : VcArgumentAppExpr = instance.argumentAppExpr ?: error("Can't find anchor within class instance")
            anchor.parent.addAfter(sampleCoClauses, anchor)
            anchor.parent.addAfter(psiFactory.createWhitespace("\n"+whitespace), anchor)
        } else {
            val anchor = coClauses.coClauseList.last()
            val coClause = sampleCoClauses.coClauseList.first()!!
            val vBar = anchor.prevSibling.prevSibling
            val clauseWhitespace = when {
                vBar.prevSibling is PsiWhiteSpace -> getIndent(vBar.prevSibling.text, whitespace)
                coClauses.prevSibling is PsiWhiteSpace -> getIndent(coClauses.prevSibling.text, whitespace)
                else -> whitespace
            }

            anchor.parent.addAfter(coClause, anchor)
            anchor.parent.addAfter(psiFactory.createWhitespace(" "), anchor)
            anchor.parent.addAfter(vBar, anchor)
            anchor.parent.addAfter(psiFactory.createWhitespace("\n"+clauseWhitespace), anchor)
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val defaultWhitespace = "  "
        val whitespace =  if (instance.parent is VcStatement && instance.parent.prevSibling is PsiWhiteSpace)
            getIndent(instance.parent.prevSibling.text, defaultWhitespace)+" " else defaultWhitespace

        for (f in fieldsToImplement) addField(f, project, whitespace)
    }
}