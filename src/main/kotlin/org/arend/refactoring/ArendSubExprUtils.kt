package org.arend.refactoring

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.arend.core.expr.Expression
import org.arend.core.expr.visitor.ToAbstractVisitor
import org.arend.error.DummyErrorReporter
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.reference.Precedence
import org.arend.naming.BinOpParser
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.psi.*
import org.arend.resolving.PsiConcreteProvider
import org.arend.settings.ArendProjectSettings
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.typechecking.ArendCancellationIndicator
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.computation.ComputationRunner
import org.arend.typechecking.subexpr.CorrespondedSubDefVisitor
import org.arend.typechecking.subexpr.SubExprError
import org.arend.util.appExprToConcrete
import java.util.function.Supplier

class SubExprException(message: String) : Throwable(message)

class LocatedReferableConverter(private val wrapped: ReferableConverter) : ReferableConverter {
    override fun toDataReferable(referable: Referable?) = referable
    override fun toDataLocatedReferable(referable: LocatedReferable?) = wrapped.toDataLocatedReferable(referable)
}

data class SubExprResult(
        val subCore: Expression,
        val subConcrete: Concrete.Expression,
        val subPsi: ArendExpr
)

@Throws(SubExprException::class)
fun correspondedSubExpr(range: TextRange, file: PsiFile, project: Project): SubExprResult {
    val possibleParent = (if (range.isEmpty)
        file.findElementAt(range.startOffset)
    else PsiTreeUtil.findCommonParent(
            file.findElementAt(range.startOffset),
            file.findElementAt(range.endOffset - 1)
    )) ?: throw SubExprException("selected expr in bad position")
    // if (possibleParent is PsiWhiteSpace) return "selected text are whitespaces"
    val exprAncestor = possibleParent.ancestor<ArendExpr>()
            ?: throw SubExprException("selected text is not an arend expression")
    val parent = exprAncestor.parent
    val psiDef = parent.ancestor<ArendDefinition>()
            ?: throw SubExprException("selected text is not in a definition")
    val service = project.service<TypeCheckingService>()
    val refConverter = LocatedReferableConverter(service.newReferableConverter(true))
    val concreteDef = PsiConcreteProvider(project, refConverter, DummyErrorReporter.INSTANCE, null)
            .getConcrete(psiDef)
            as? Concrete.Definition
            ?: throw SubExprException("selected text is not in a function definition")
    val def = service.getTypechecked(psiDef)
            ?: throw SubExprException("underlying definition is not type checked")

    val children = collectArendExprs(parent, range)
            .map { appExprToConcrete(it) ?: ConcreteBuilder.convertExpression(refConverter, it) }
            .map(Concrete::BinOpSequenceElem)
            .toList()
            .takeIf { it.isNotEmpty() }
            ?: throw SubExprException("cannot find a suitable subexpression")
    val parser = BinOpParser(DummyErrorReporter.INSTANCE)

    val subExpr = if (children.size == 1)
        children.first().expression
    else parser.parse(Concrete.BinOpSequenceExpression(null, children))
    val subExprVisitor = CorrespondedSubDefVisitor(subExpr)
    val result = concreteDef.accept(subExprVisitor, def)
            ?: throw SubExprException(buildString {
                appendln("cannot find a suitable subexpression, errors collected:")
                subExprVisitor.exprError.forEach { appendln(it) }
            })
    return SubExprResult(result.proj1, result.proj2, exprAncestor)
}

private fun everyExprOf(concrete: Concrete.Expression): Sequence<Concrete.Expression> = sequence {
    yield(concrete)
    if (concrete is Concrete.AppExpression)
        for (arg in concrete.arguments) yieldAll(everyExprOf(arg.expression))
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
    val left = siblings.firstOrNull { PsiTreeUtil.isAncestor(it, leftMost, false) } ?: leftMost
    val right = siblings.lastOrNull { PsiTreeUtil.isAncestor(it, rightMost, false) } ?: rightMost
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

fun prettyPopupExpr(
        project: Project,
        expression: Expression?,
        mode: NormalizationMode? = null
): String {
    val settings = project.service<ArendProjectSettings>()
    val builder = StringBuilder()
    ToAbstractVisitor.convert(expression, object : PrettyPrinterConfig {
        override fun getExpressionFlags() = settings.popupPrintingOptionsFilterSet
        override fun getNormalizationMode() = mode
    }).accept(PrettyPrintVisitor(builder, 2), Precedence(Concrete.Expression.PREC))
    return builder.toString()
}

inline fun normalizeExpr(
        project: Project,
        subCore: Expression,
        mode: NormalizationMode = NormalizationMode.RNF,
        crossinline after: (String) -> Unit
) {
    val title = "Running normalization"
    var result: String? = null
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
        override fun run(indicator: ProgressIndicator) {
            result = ComputationRunner<String>().run(ArendCancellationIndicator(indicator), Supplier {
                prettyPopupExpr(project, subCore, mode)
            })
        }

        override fun onFinished() = result?.let(after) ?: Unit
    })
}
