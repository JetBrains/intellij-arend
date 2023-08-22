package org.arend.inspection

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.elementType
import org.arend.codeInsight.completion.withAncestors
import org.arend.ext.concrete.ConcreteSourceNode
import org.arend.intention.binOp.BinOpIntentionUtil
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.refactoring.psiOfConcrete
import org.arend.refactoring.unwrapParens
import org.arend.term.concrete.Concrete
import org.arend.util.ArendBundle
import org.arend.util.appExprToConcrete
import org.arend.util.isBinOp

class RedundantParensInspection : ArendInspectionBase() {
    override fun buildArendVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        fun registerFix(element: PsiElement) {
            val message = ArendBundle.message("arend.inspection.redundant.parentheses.message")
            holder.registerProblem(element, message, UnwrapParensFix(element))
        }

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                if (element !is ArendTuple && element !is ArendTypeTele) return
                if (element is ArendTypeTele && !(element.isExplicit && element.referableList == listOf(null))) return
                if (element is ArendTuple && element.tupleExprList.size > 1 && withAncestors(ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java, ArendTupleExpr::class.java, ArendImplicitArgument::class.java).accepts(element)) {
                    registerFix(element)
                    return
                }
                val expression = unwrapParens(element) ?: return
                if (expression is ArendArrExpr && withAncestors(ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java, ArendReturnExpr::class.java, ArendCaseExpr::class.java).accepts(element)) return
                if (element is ArendTuple && (neverNeedsParens(expression) || isCommonRedundantParensPattern(element, expression) || isApplicationUsedAsBinOpArgument(element, expression)) ||
                    element is ArendTypeTele && typeTeleDoesntNeedParens(expression)) {
                    registerFix(element)
                }
            }
        }
    }
}

private fun neverNeedsParens(expression: ArendExpr): Boolean {
    val childAppExpr = if (expression is ArendNewExpr && isAtomic(expression)) expression.argumentAppExpr else null
    return childAppExpr != null && isAtomic(childAppExpr) && !isBinOp(childAppExpr.atomFieldsAcc!!)
}

private fun typeTeleDoesntNeedParens(expression: ArendExpr): Boolean {
    return expression.descendantOfType<ArendLiteral>()?.textRange == expression.textRange ||
           expression.descendantOfType<ArendUniverseAppExpr>()?.let { it.childOfType(UNIVERSE)?.textRange == expression.textRange } == true
}

private fun isAtomic(expression: ArendNewExpr) =
    // Excludes cases like `f (\new Unit) 1`
    expression.appPrefix == null &&
            // Excludes cases like `f (Pair { | x => 1 }) 1`
            expression.localCoClauseList.isEmpty() &&
            // Excludes cases like `f (mcases \with {}) 1`
            expression.withBody == null

private fun isAtomic(argumentAppExpr: ArendArgumentAppExpr): Boolean =
    argumentAppExpr.argumentList.isEmpty() &&
            hasNoLevelArguments(argumentAppExpr) &&
            argumentAppExpr.atomFieldsAcc != null

private fun hasNoLevelArguments(argumentAppExpr: ArendArgumentAppExpr): Boolean {
    val longNameExpr = argumentAppExpr.longNameExpr
    // Excludes cases like `f (Path \levels 0 0) 1`, `f (Path \lp \lh) 1`
    return longNameExpr == null || longNameExpr.levelsExpr == null && longNameExpr.pLevelExpr == null
}

private fun isBinOp(atomFieldsAcc: ArendAtomFieldsAcc): Boolean {
    if (atomFieldsAcc.numberList.isNotEmpty()) {
        return false
    }
    val literal = atomFieldsAcc.atom.literal ?: return false
    return isBinOp(literal.longName) || isBinOp(literal.ipName)
}

private fun isCommonRedundantParensPattern(tuple: ArendTuple, expression: ArendExpr): Boolean {
    val parentNewExpr = (getParentAtomFieldsAcc(tuple)
        ?.parent as? ArendArgumentAppExpr)
        // Excludes cases like `(f a) b`
        ?.takeIf { it.argumentList.isEmpty() }
        ?.parent as? ArendNewExpr
    // Examples of the parent new expression: (f a), \new (f a), (f a) { x => 1 }
    val parent = parentNewExpr?.parent
    return isRedundantParensForAnyChild(parent) ||
            parent is ArendTupleExpr && isRedundantParensInTupleParent(parent, expression) ||
            parent is ArendArrExpr && (parent.codomain == parentNewExpr || tuple.tupleExprList.size == 1 && tuple.tupleExprList.first().expr is ArendNewExpr)
}

private fun getParentAtomFieldsAcc(tuple: ArendTuple) =
    ((tuple.parent as? ArendAtom)
        ?.parent as? ArendAtomFieldsAcc)
        // Excludes cases like `(f a).1`
        ?.takeIf { it.numberList.isEmpty() }

private fun isRedundantParensForAnyChild(parent: PsiElement?) =
    parent is ArendReturnExpr ||
            // Parameter types
            parent is ArendNameTele ||
            parent is ArendFieldTele ||
            parent is ArendTypedExpr ||
            // Bodies, Clauses, CoClauses
            parent is ArendFunctionBody ||
            parent is ArendDefMeta ||
            parent is ArendClause ||
            parent is CoClauseBase ||
            // Clause patterns
            parent is ArendPattern ||
            parent is ArendAsPattern ||
            // Expressions
            parent is ArendPiExpr ||
            parent is ArendLamExpr ||
            parent is ArendLetExpr ||
            parent is ArendLetClause

private fun isRedundantParensInTupleParent(parent: ArendTupleExpr, expression: ArendExpr): Boolean {
    if (parent.colon != null) {
        return true
    }
    val grand = parent.parent
    return grand is ArendTuple && grand.tupleExprList.size > 1 &&
            (expression !is ArendCaseExpr || expression.withBody != null || expression.returnKw != null) ||
            grand is ArendImplicitArgument
}

private fun isApplicationUsedAsBinOpArgument(tuple: ArendTuple, tupleExpression: ArendExpr): Boolean {
    val parentAtomFieldsAcc = getParentAtomFieldsAcc(tuple) ?: return false
    val parentAppExprPsi = parentArgumentAppExpr(parentAtomFieldsAcc) ?: return false
    val parentAppExpr = appExprToConcrete(parentAppExprPsi, true) ?: return false
    var result = false
    parentAppExpr.accept(object : ArendInspectionConcreteVisitor() {
        override fun visitHole(expr: Concrete.HoleExpression?, params: Void?): Void? {
            super.visitHole(expr, params)
            if (expr != null && psiOfConcrete(expr) == tuple) {
                result = isApplicationUsedAsBinOpArgument(parent, tupleExpression)
            }
            return null
        }
    }, null)
    return result
}

private fun isApplicationUsedAsBinOpArgument(tupleParent: ConcreteSourceNode?, tupleExpression: ArendExpr): Boolean {
    if (tupleParent is Concrete.AppExpression && BinOpIntentionUtil.isBinOpInfixApp(tupleParent)) {
        val childAppExpr =
            if (tupleExpression is ArendNewExpr && isAtomic(tupleExpression)) tupleExpression.argumentAppExpr
            else null
        return childAppExpr != null && hasNoLevelArguments(childAppExpr) && !isBinOpApp(childAppExpr)
    }
    return false
}

internal fun isBinOpApp(app: ArendArgumentAppExpr): Boolean {
    val binOpSeq = appExprToConcrete(app, true)
    return binOpSeq is Concrete.AppExpression && isBinOp(binOpSeq.function.data as? ArendReferenceContainer)
}

private class UnwrapParensFix(tuple: PsiElement) : LocalQuickFixOnPsiElement(tuple) {
    override fun getFamilyName(): String = ArendBundle.message("arend.unwrap.parentheses.fix")

    override fun getText(): String = ArendBundle.message("arend.unwrap.parentheses.fix")

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        when (startElement) {
            is ArendTypeTele -> {
                val unwrapped = unwrapParens(startElement) ?: return
                val literal = unwrapped.descendantOfType<ArendLiteral>()
                val universeAppExpr = unwrapped.descendantOfType<ArendUniverseAppExpr>()
                if (literal != null && literal.textRange == unwrapped.textRange) {
                    startElement.childrenWithLeaves.firstOrNull { it.elementType == RPAREN }?.delete()
                    startElement.lparen?.delete()
                    startElement.typedExpr?.replace(literal)
                } else if (universeAppExpr != null && universeAppExpr.textRange == unwrapped.textRange) {
                    val factory = ArendPsiFactory(project)
                    val typeTele = factory.createFromText("\\func test => \\Sigma ${universeAppExpr.text}")?.descendantOfType<ArendTypeTele>()
                    if (typeTele != null) startElement.replace(typeTele)
                }
            }
            is ArendTuple -> {
                if (startElement.tupleExprList.size > 1 && withAncestors(ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java, ArendTupleExpr::class.java, ArendImplicitArgument::class.java).accepts(startElement)) {
                    startElement.parent.parent.parent.parent.parent.delete()
                    val implicitArg = (startElement.parent as ArendImplicitArgument)
                    val lbrace = implicitArg.childrenWithLeaves.first { it.elementType == ArendElementTypes.LBRACE }
                    implicitArg.addRangeAfter(startElement.tupleExprList.first(), startElement.tupleExprList.last(), lbrace)
                } else {
                    val unwrapped = unwrapParens(startElement) ?: return
                    if (withAncestors(ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java).accepts(startElement) &&
                        startElement.parent.parent.parent.parent.textRange == startElement.textRange) startElement.parent.parent.parent.parent.replace(unwrapped)
                    else if (unwrapped.descendantOfType<ArendAtomFieldsAcc>()?.let { it.textRange == unwrapped.textRange } == true && withAncestors(ArendAtom::class.java, ArendAtomFieldsAcc::class.java).accepts(startElement))
                        startElement.parent.parent.replace(unwrapped.descendantOfType<ArendAtomFieldsAcc>()!!)
                    else
                        startElement.replace(unwrapped)
                }
            }
        }

    }
}