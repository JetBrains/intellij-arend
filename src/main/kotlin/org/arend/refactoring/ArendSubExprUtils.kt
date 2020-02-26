package org.arend.refactoring

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.arend.core.definition.FunctionDefinition
import org.arend.core.expr.Expression
import org.arend.core.expr.visitor.ToAbstractVisitor
import org.arend.error.DummyErrorReporter
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.reference.Precedence
import org.arend.naming.BinOpParser
import org.arend.naming.reference.converter.IdReferableConverter
import org.arend.psi.*
import org.arend.resolving.PsiConcreteProvider
import org.arend.settings.ArendProjectSettings
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.subexpr.CorrespondedSubExprVisitor
import org.arend.util.appExprToConcrete

class SubExprError(message: String) : Throwable(message)

@Throws(SubExprError::class)
fun correspondedSubExpr(
        range: TextRange, file: PsiFile, project: Project
): Triple<Expression, Concrete.Expression, ArendExpr> {
    val possibleParent = (if (range.isEmpty)
        file.findElementAt(range.startOffset)
    else PsiTreeUtil.findCommonParent(
            file.findElementAt(range.startOffset),
            file.findElementAt(range.endOffset - 1)
    )) ?: throw SubExprError("selected expr in bad position")
    // if (possibleParent is PsiWhiteSpace) return "selected text are whitespaces"
    val exprAncestor = possibleParent.ancestor<ArendExpr>()
            ?: throw SubExprError("selected text is not an arend expression")
    val parent = exprAncestor.parent
    val psiDef = parent.ancestor<ArendDefinition>()
            ?: throw SubExprError("selected text is not in a definition")
    val service = project.service<TypeCheckingService>()
    // Only work for functions right now
    val concreteDef = PsiConcreteProvider(
            project,
            service.newReferableConverter(false),
            DummyErrorReporter.INSTANCE,
            null
    ).getConcrete(psiDef)
            as? Concrete.FunctionDefinition
            ?: throw SubExprError("selected text is not in a function definition")
    val def = service.getTypechecked(psiDef)
            as? FunctionDefinition
            ?: throw SubExprError("underlying definition is not type checked")

    val children = collectArendExprs(parent, range)
            .map {
                appExprToConcrete(it)
                        ?: ConcreteBuilder.convertExpression(IdReferableConverter.INSTANCE, it)
            }
            .map(Concrete::BinOpSequenceElem)
            .toList()
            .takeIf { it.isNotEmpty() }
            ?: throw SubExprError("cannot find a suitable subexpression")
    val parser = BinOpParser(DummyErrorReporter.INSTANCE)
    val body = def.actualBody as? Expression
            ?: throw SubExprError("function body is not an expression")
    // Only work for single clause right now
    val concreteBody = concreteDef.body.term
            ?: throw SubExprError("does not yet support multiple clauses")

    val subExpr = if (children.size == 1)
        children.first().expression
    else parser.parse(Concrete.BinOpSequenceExpression(null, children))
    val subExprVisitor = CorrespondedSubExprVisitor(subExpr)
    val result = concreteBody
            .accept(subExprVisitor, body)
            ?: throw SubExprError("cannot find a suitable subexpression")
    return Triple(result.proj1, result.proj2, exprAncestor)
}

private fun everyExprOf(concrete: Concrete.Expression): Sequence<Concrete.Expression> = sequence {
    yield(concrete)
    if (concrete is Concrete.AppExpression) {
        val arguments = concrete.arguments
        val expression = arguments.firstOrNull()?.expression
        if (expression != null) {
            yieldAll(everyExprOf(expression))
            if (arguments.size > 1) yieldAll(everyExprOf(arguments.last().expression))
        }
    }
}

fun rangeOfConcrete(subConcrete: Concrete.Expression): TextRange {
    val exprs = everyExprOf(subConcrete)
            .map { it.data }
            .filterIsInstance<PsiElement>()
            .toList()
    if (exprs.size == 1) return exprs.first().textRange
    // exprs is guaranteed to be empty
    val leftMost = exprs.minBy { it.textRange.startOffset }!!
    val rightMost = exprs.maxBy { it.textRange.endOffset }!!
    val siblings = PsiTreeUtil
            .findCommonParent(leftMost, rightMost)
            ?.childrenWithLeaves
            ?.toList()
            ?: return (subConcrete.data as PsiElement).textRange
    val left = siblings.first { PsiTreeUtil.isAncestor(it, leftMost, false) }
    val right = siblings.last { PsiTreeUtil.isAncestor(it, rightMost, false) }
    return TextRange.create(
            minOf(left.textRange.startOffset, right.textRange.startOffset),
            maxOf(left.textRange.endOffset, right.textRange.endOffset)
    )
}

private fun collectArendExprs(
        parent: PsiElement,
        range: TextRange
): List<Abstract.Expression> {
    if (range.isEmpty && range.startOffset == parent.textRange.startOffset
            || parent.textRange == range) {
        val firstExpr = parent.linearDescendants.filterIsInstance<Abstract.Expression>().firstOrNull()
        if (firstExpr != null) return listOf(firstExpr)
    }
    val exprs = parent.childrenWithLeaves
            .dropWhile { it.textRange.endOffset < range.startOffset }
            .takeWhile { it.textRange.startOffset < range.endOffset }
            .toList()
    if (exprs.isEmpty()) return emptyList()
    return if (exprs.size == 1) {
        val first = exprs.first()
        val subCollected = collectArendExprs(first, range)
        when {
            subCollected.isNotEmpty() -> subCollected
            first is ArendExpr -> listOf(first)
            else -> emptyList()
        }
    } else exprs.asSequence().mapNotNull {
        it.linearDescendants.filterIsInstance<Abstract.Expression>().firstOrNull()
    }.toList()
}

fun prettyPopupExpr(project: Project, expression: Expression?): String {
    val settings = project.service<ArendProjectSettings>()
    val builder = StringBuilder()
    ToAbstractVisitor.convert(expression, object : PrettyPrinterConfig {
        override fun getExpressionFlags() = settings.popupPrintingOptionsFilterSet
        override fun getNormalizationMode(): NormalizationMode? = null
    }).accept(PrettyPrintVisitor(builder, 2), Precedence(Concrete.Expression.PREC))
    return builder.toString()
}

