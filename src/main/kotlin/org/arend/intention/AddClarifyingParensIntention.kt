package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.intention.binOp.BinOpIntentionUtil
import org.arend.intention.binOp.BinOpSeqProcessor
import org.arend.intention.binOp.CaretHelper
import org.arend.psi.ext.ArendArgumentAppExpr
import org.arend.psi.parentOfType
import org.arend.term.concrete.Concrete
import org.arend.util.ArendBundle

class AddClarifyingParensIntention : BaseArendIntention(ArendBundle.message("arend.expression.addClarifyingParentheses")) {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        editor ?: return false
        val binOp = BinOpIntentionUtil.findBinOp(element) ?: return false
        val binOpSeqAbs = binOp.parentOfType<ArendArgumentAppExpr>() ?: return false
        val binOpSeq = BinOpIntentionUtil.toConcreteBinOpInfixApp(binOpSeqAbs) ?: return false
        return needClarifyingParens(binOpSeq)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        editor ?: return
        val binOp = BinOpIntentionUtil.findBinOp(element) ?: return
        val binOpSeqAbs = binOp.parentOfType<ArendArgumentAppExpr>() ?: return
        val binOpSeq = BinOpIntentionUtil.toConcreteBinOpInfixApp(binOpSeqAbs) ?: return
        AddClarifyingParensProcessor().run(project, editor, binOp, binOpSeq)
    }
}

private fun needClarifyingParens(binOpSeq: Concrete.AppExpression): Boolean {
    return binOpSeq.arguments.any {
        it.isExplicit && it.expression.let { e -> e is Concrete.AppExpression && BinOpIntentionUtil.isBinOpInfixApp(e) }
    }
}

class AddClarifyingParensProcessor : BinOpSeqProcessor() {
    override fun mapArgument(arg: Concrete.Argument,
                             parentBinOp: Concrete.AppExpression,
                             editor: Editor,
                             caretHelper: CaretHelper): String? {
        if (!arg.isExplicit) {
            return implicitArgumentText(arg, editor)
        }
        val expression = arg.expression
        if (expression is Concrete.AppExpression && BinOpIntentionUtil.isBinOpInfixApp(expression)) {
            return mapBinOp(expression, editor, caretHelper)?.let { "($it)" }
        }
        return text(expression, editor)
    }
}