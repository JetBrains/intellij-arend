package org.arend.inspection

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.castSafelyTo
import org.arend.intention.binOp.BinOpIntentionUtil
import org.arend.psi.*
import org.arend.psi.ext.ArendFunctionalBody
import org.arend.psi.parentArgumentAppExpr
import org.arend.refactoring.unwrapParens
import org.arend.util.ArendBundle
import org.arend.util.isBinOp

class RedundantParensInspection : ArendInspectionBase() {
    override fun buildArendVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): ArendVisitor {
        return object : ArendVisitor() {
            override fun visitTuple(tuple: ArendTuple) {
                super.visitTuple(tuple)
                val expression = unwrapParens(tuple) ?: return
                if (neverNeedsParens(expression) ||
                        neverNeedsParensInParent(tuple) ||
                        isApplicationUsedAsBinOpArgument(tuple, expression)) {
                    val message = ArendBundle.message("arend.inspection.redundant.parentheses.message")
                    holder.registerProblem(tuple, message, UnwrapParensFix(tuple))
                }
            }
        }
    }
}

private fun neverNeedsParens(expression: ArendExpr): Boolean {
    val childAppExpr = if (expression is ArendNewExpr && isAtomic(expression)) expression.argumentAppExpr else null
    return childAppExpr != null && isAtomic(childAppExpr) && !isBinOp(childAppExpr.atomFieldsAcc!!)
}

private fun isAtomic(expression: ArendNewExpr) =
        // Excludes cases like `f (\new Unit) 1`
        expression.appPrefix == null &&
                // Excludes cases like `f (Pair { | x => 1 }) 1`
                expression.localCoClauseList.isEmpty() &&
                // Excludes cases like `f (mcases \with {}) 1`
                expression.withBody == null

private fun isAtomic(argumentAppExpr: ArendArgumentAppExpr): Boolean {
    if (argumentAppExpr.argumentList.isNotEmpty()) {
        return false
    }
    val longNameExpr = argumentAppExpr.longNameExpr
    if (longNameExpr != null) {
        // Excludes cases like `f (Path \levels 0 0) 1`, `f (Path \lp \lh) 1`
        return longNameExpr.levelsExpr == null && longNameExpr.atomOnlyLevelExprList.isEmpty()
    }
    return argumentAppExpr.atomFieldsAcc != null
}

private fun isBinOp(atomFieldsAcc: ArendAtomFieldsAcc): Boolean {
    if (atomFieldsAcc.fieldAccList.isNotEmpty()) {
        return false
    }
    val literal = atomFieldsAcc.atom.literal ?: return false
    return isBinOp(literal.longName) || isBinOp(literal.ipName)
}

private fun neverNeedsParensInParent(tuple: ArendTuple): Boolean {
    val parentNewExpr = getParentAtomFieldsAcc(tuple)
            ?.parent.castSafelyTo<ArendArgumentAppExpr>()
            // Excludes cases like `(f a) b`
            ?.takeIf { it.argumentList.isEmpty() }
            ?.parent.castSafelyTo<ArendNewExpr>()
    // Examples of the parent new expression: (f a), \new (f a), (f a) { x => 1 }
    val parent = parentNewExpr?.parent
    return parent is ArendReturnExpr ||
            // Parameter types
            parent is ArendNameTele ||
            parent is ArendFieldTele ||
            parent is ArendTypedExpr ||
            // Bodies, Clauses, CoClauses
            parent is ArendFunctionalBody ||
            parent is ArendDefMeta ||
            parent is ArendClause ||
            parent is CoClauseBase ||
            // Clause patterns
            parent is ArendPattern ||
            parent is ArendAsPattern ||
            // Expressions
            parent is ArendPiExpr ||
            parent is ArendLamExpr ||
            parent is ArendLamTele ||
            parent is ArendLetExpr ||
            parent is ArendLetClause ||
            parent is ArendTypeAnnotation ||
            // Tuples
            parent is ArendTupleExpr && (parent.colon != null || parent.parent.let { it is ArendTuple && it.tupleExprList.size > 1 || it is ArendImplicitArgument })
}

private fun getParentAtomFieldsAcc(tuple: ArendTuple) =
        tuple.parent.castSafelyTo<ArendAtom>()
                ?.parent.castSafelyTo<ArendAtomFieldsAcc>()
                // Excludes cases like `(f a).1`
                ?.takeIf { it.fieldAccList.isEmpty() }

private fun isApplicationUsedAsBinOpArgument(tuple: ArendTuple, expression: ArendExpr): Boolean {
    val parentAppExpr = getParentAtomFieldsAcc(tuple)?.let { parentArgumentAppExpr(it) }
    if (parentAppExpr != null && BinOpIntentionUtil.toConcreteBinOpApp(parentAppExpr) != null) {
        val childAppExpr = if (expression is ArendNewExpr && isAtomic(expression)) expression.argumentAppExpr else null
        return childAppExpr != null && BinOpIntentionUtil.toConcreteBinOpApp(childAppExpr) == null
    }
    return false
}

private class UnwrapParensFix(tuple: ArendTuple) : LocalQuickFixOnPsiElement(tuple) {
    override fun getFamilyName(): String = ArendBundle.message("arend.unwrap.parentheses.fix")

    override fun getText(): String = ArendBundle.message("arend.unwrap.parentheses.fix")

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val tuple = startElement as ArendTuple
        val unwrapped = unwrapParens(tuple) ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        document.replaceString(tuple.startOffset, tuple.endOffset, unwrapped.text)
    }
}