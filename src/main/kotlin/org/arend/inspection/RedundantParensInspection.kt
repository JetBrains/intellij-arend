package org.arend.inspection

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.arend.codeInsight.completion.withAncestors
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.refactoring.changeSignature.performTextModification
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
                if (element !is ArendTuple && element !is ArendTypeTele && element !is ArendMaybeAtomLevelExprs) return
                if (element is ArendMaybeAtomLevelExprs) {
                    if (isRedundantParensInArendMaybeAtomLevelExprs(element)) {
                        registerFix(element)
                    }
                    return
                }
                if (element is ArendTypeTele && !(element.isExplicit && element.referableList == listOf(null))) return
                if (element is ArendTuple && element.tupleExprList.size > 1 &&
                        withAncestors(ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java, ArendTupleExpr::class.java, ArendImplicitArgument::class.java).accepts(element) &&
                        element.parentOfType<ArendArgumentAppExpr>()?.children?.size == 1) {
                    registerFix(element)
                    return
                }
                val expression = unwrapParens(element) ?: return
                if (expression is ArendArrExpr && withAncestors(ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java, ArendNewExpr::class.java, ArendReturnExpr::class.java, ArendCaseExpr::class.java).accepts(element)) return
                if (element is ArendTuple && (neverNeedsParens(expression) || isCommonRedundantParensPattern(element, expression)/* || isApplicationUsedAsBinOpArgument(element, expression)*/) ||
                    element is ArendTypeTele && typeTeleDoesntNeedParens(expression)) {
                    registerFix(element)
                }
            }
        }
    }
}

private fun isRedundantParensInArendMaybeAtomLevelExprs(element: ArendMaybeAtomLevelExprs): Boolean {
    return element.childrenOfType<ArendAtomLevelExpr>().size == 1 &&
            element.childOfType(LPAREN) != null &&
            element.childOfType(RPAREN) != null
}

private fun neverNeedsParens(expression: ArendExpr): Boolean {
    val childAppExpr = if (expression is ArendNewExpr && isAtomic(expression)) expression.argumentAppExpr else null
    return childAppExpr != null && isAtomic(childAppExpr) && !isBinOp(childAppExpr.atomFieldsAcc!!)
}

private fun typeTeleDoesntNeedParens(expression: ArendExpr): Boolean {
    return expression.descendantOfType<ArendLiteral>()?.textRange == expression.textRange ||
           expression.descendantOfType<ArendUniverseAppExpr>()?.let { it.childOfType(UNIVERSE)?.textRange == expression.textRange } == true ||
           expression.descendantOfType<ArendSetUniverseAppExpr>()?.let { it.childOfType(SET)?.textRange == expression.textRange } == true
}

fun isAtomic(expression: ArendNewExpr) =
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

fun hasNoLevelArguments(argumentAppExpr: ArendArgumentAppExpr): Boolean {
    val longNameExpr = argumentAppExpr.longNameExpr
    // Excludes cases like `f (Path \levels 0 0) 1`, `f (Path \lp \lh) 1`
    return longNameExpr == null || longNameExpr.levelsExpr == null && longNameExpr.pLevelExpr == null
}

private fun isBinOp(atomFieldsAcc: ArendAtomFieldsAcc): Boolean {
    if (atomFieldsAcc.fieldAccList.isNotEmpty()) {
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
    return isRedundantParensForAnyChild(parent, tuple) ||
            parent is ArendTupleExpr && isRedundantParensInTupleParent(parent, expression) ||
            parent is ArendArrExpr && (parent.codomain == parentNewExpr || tuple.tupleExprList.size == 1 && tuple.tupleExprList.first().expr is ArendNewExpr)
}

fun getParentAtomFieldsAcc(tuple: ArendTuple) =
    ((tuple.parent as? ArendAtom)
        ?.parent as? ArendAtomFieldsAcc)
        // Excludes cases like `(f a).1`
        ?.takeIf { it.fieldAccList.isEmpty() }

private fun isRedundantParensForAnyChild(element: PsiElement?, tuple: ArendTuple): Boolean {
    val checkElement = element is ArendReturnExpr ||
            // Parameter types
            element is ArendNameTele ||
            element is ArendFieldTele ||
            element is ArendTypedExpr ||
            // Bodies, Clauses, CoClauses
            element is ArendFunctionBody ||
            element is ArendDefMeta ||
            element is ArendClause ||
            element is CoClauseBase ||
            // Clause patterns
            element is ArendPattern ||
            element is ArendAsPattern ||
            // Expressions
            element is ArendPiExpr ||
            element is ArendLamExpr ||
            element is ArendLetExpr ||
            element is ArendLetClause ||
            element is ArendCaseArg

    val parent = element?.parent
    if (parent is ArendTupleExpr) {
        val parentArendTuple = parent.parent as? ArendTuple?
        val tupleList = parentArendTuple?.tupleExprList
        val parentIndex = tupleList?.indexOf(parent) ?: -1
        if (parentIndex != tupleList?.lastIndex && tuple.tupleExprList.last().exprIfSingle is ArendCaseExpr) {
            return false
        }
    }
    return checkElement
}


private fun isRedundantParensInTupleParent(parent: ArendTupleExpr, expression: ArendExpr): Boolean {
    if (parent.colon != null) {
        return true
    }
    val grand = parent.parent
    return grand is ArendTuple && grand.tupleExprList.size > 1 &&
            (expression !is ArendCaseExpr || expression.withBody != null || expression.returnKw != null) ||
            grand is ArendImplicitArgument
}

internal fun isBinOpApp(app: ArendArgumentAppExpr): Boolean {
    val binOpSeq = appExprToConcrete(app, true)
    return binOpSeq is Concrete.AppExpression && isBinOp(binOpSeq.function.data as? ArendReferenceContainer)
}

private class UnwrapParensFix(element: PsiElement) : LocalQuickFixOnPsiElement(element) {
    override fun getFamilyName(): String = ArendBundle.message("arend.unwrap.parentheses.fix")

    override fun getText(): String = ArendBundle.message("arend.unwrap.parentheses.fix")

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        doUnwrapParens(startElement)
    }
}

fun doUnwrapParens(startElement: PsiElement) {
    var parent: PsiElement? = startElement
    var spaceLeft = ""
    var parenLeft = false
    var spaceRight = ""
    var parenRight = false
    var contents: String

    while (parent?.textRange?.startOffset == startElement.startOffset) {
        parenLeft = parenLeft || parent.findPrevSibling()?.elementType in listOf(LPAREN, LBRACE)
        val w = parent.getWhitespace(SpaceDirection.LeadingSpace)
        if (w != null && w != "") {
            spaceLeft = w
            break
        }
        parent = parent.parent
    }

    parent = startElement

    while (parent?.textRange?.endOffset == startElement.endOffset) {
        parenRight = parenRight || parent.findNextSibling()?.elementType in listOf(RPAREN, RBRACE)
        val v = parent.getWhitespace(SpaceDirection.TrailingSpace)
        if (v != null && v != "") {
            spaceRight = v
            break
        }
        parent = parent.parent
    }

    when (startElement) {
        is ArendTuple -> {
            spaceLeft += startElement.tupleExprList.first().getWhitespace(SpaceDirection.LeadingSpace)
            spaceRight = startElement.tupleExprList.last().getWhitespace(SpaceDirection.TrailingSpace) + spaceRight
            contents = startElement.containingFile.text.substring(startElement.tupleExprList.first().startOffset, startElement.tupleExprList.last().endOffset)
        }
        is ArendMaybeAtomLevelExprs -> {
            contents = startElement.childOfType<ArendAtomLevelExpr>()?.text ?: ""
        }
        else -> {
            contents = unwrapParens(startElement)?.text ?: ""
        }
    }

    if (spaceLeft == "" && !parenLeft) contents = " $contents"
    if (spaceRight == "" && !parenRight) contents += " "

    performTextModification(startElement, contents)
}
