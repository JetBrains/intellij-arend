package org.arend.refactoring

import com.intellij.codeInsight.hint.HintManager
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.arend.core.expr.*
import org.arend.error.DummyErrorReporter
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.prettyprinting.DefinitionRenamer
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.injection.PsiInjectionTextFile
import org.arend.naming.resolving.ResolverListener
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.CachingScope
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.settings.ArendProjectSettings
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.ProgressCancellationIndicator
import org.arend.typechecking.computation.ComputationRunner
import org.arend.typechecking.subexpr.CorrespondedSubDefVisitor
import org.arend.typechecking.subexpr.CorrespondedSubExprVisitor
import org.arend.typechecking.subexpr.FindBinding
import org.arend.typechecking.subexpr.SubExprError
import org.arend.typechecking.visitor.SyntacticDesugarVisitor
import org.arend.resolving.util.parseBinOp

/**
 * @param def for storing function-level elim/clauses bodies
 */
class SubExprException(
    message: String,
    val def: Pair<Concrete.Definition, TCDefinition>? = null
) : Throwable(message)

fun binding(p: PsiElement, selected: TextRange) = SyntaxTraverser
        .psiTraverser(p)
        .onRange { selected in it.textRange }
        .filter(ArendDefIdentifier::class.java)
        .firstOrNull()

data class SubExprResult(
        val subCore: Expression,
        val subConcrete: Concrete.Expression,
        val subPsi: ArendExpr
) {
    fun findBinding(selected: TextRange) = if (subPsi is ArendLetExprImplMixin
            && subConcrete is Concrete.LetExpression
            && subCore is LetExpression) binding(subPsi, selected)?.let {
        it to FindBinding.visitLet(it, subConcrete, subCore)
    } else findTeleBinding(selected)?.let { (id, link) -> id to link?.typeExpr }

    @Suppress("MemberVisibilityCanBePrivate")
    fun findTeleBinding(selected: TextRange) = if (subPsi is ArendLamExprImplMixin
            && subConcrete is Concrete.LamExpression
            && subCore is LamExpression) binding(subPsi, selected)?.let {
        it to FindBinding.visitLam(it, subConcrete, subCore)
    } else if (subPsi is ArendPiExprImplMixin
            && subConcrete is Concrete.PiExpression
            && subCore is PiExpression) binding(subPsi, selected)?.let {
        it to FindBinding.visitPi(it, subConcrete, subCore)
    } else if (subPsi is ArendSigmaExprImplMixin
            && subConcrete is Concrete.SigmaExpression
            && subCore is SigmaExpression) binding(subPsi, selected)?.let {
        it to FindBinding.visitSigma(it, subConcrete, subCore)
    } else if (subPsi is ArendCaseExprImplMixin
            && subConcrete is Concrete.CaseExpression
            && subCore is CaseExpression) binding(subPsi, selected)?.let {
        it to FindBinding.visitCase(it, subConcrete, subCore)
    } else null
}

private class MyResolverListener(private val data: Any) : ResolverListener {
    var result: Concrete.Expression? = null
    var originalExpr: Concrete.Expression? = null

    override fun metaResolved(expression: Concrete.ReferenceExpression, args: List<Concrete.Argument>, result: Concrete.Expression, coclauses: Concrete.Coclauses?, clauses: Concrete.FunctionClauses?) {
        if (expression.data == data) {
            this.result = result
            var expr = if (clauses == null) Concrete.AppExpression.make(data, expression, args) else
                Concrete.BinOpSequenceExpression(data, listOf(Concrete.BinOpSequenceElem(expression)) + args.map { Concrete.BinOpSequenceElem(it.expression, Fixity.NONFIX, it.isExplicit) }, clauses)
            if (coclauses != null) {
                expr = Concrete.ClassExtExpression.make(data, expr, coclauses)
            }
            originalExpr = expr
        }
    }
}

@Throws(SubExprException::class)
fun correspondedSubExpr(range: TextRange, file: PsiFile, project: Project): SubExprResult {
    val startElement = file.findElementAt(range.startOffset) ?: throw SubExprException("selected expr in bad position")
    val possibleParent = if (range.isEmpty) startElement
    else file.findElementAt(range.endOffset - 1)?.let {
        PsiTreeUtil.findCommonParent(startElement, it)
    } ?: throw SubExprException("selected expr in bad position")
    val exprAncestor = possibleParent.ancestor<ArendExpr>()
        ?: throw SubExprException("selected text is not an arend expression")
    val parent = exprAncestor.parent

    val (head, tail) = collectArendExprs(parent, range)
        ?: throw SubExprException("cannot find a suitable concrete expression")
    val subExpr =
        if (tail.isNotEmpty()) parseBinOp(head, tail)
        else ConcreteBuilder.convertExpression(head).accept(SyntacticDesugarVisitor(DummyErrorReporter.INSTANCE), null)
    val resolver = subExpr.underlyingReferenceExpression?.let { refExpr -> refExpr.data?.let { MyResolverListener(it) } }

    // if (possibleParent is PsiWhiteSpace) return "selected text are whitespaces"
    val concreteProvider = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null, true, resolver)
    val psiDef = possibleParent.ancestor<TCDefinition>()
        ?: throw SubExprException("selected text is not in a definition")
    val concreteDef = concreteProvider.getConcrete(psiDef) as? Concrete.Definition
    val body = concreteDef?.let { it to psiDef }

    val injectionContext = (file as? ArendFile)?.injectionContext
    val injectionHost = injectionContext?.containingFile as? PsiInjectionTextFile
    val errors: List<SubExprError>
    val result = (if (injectionHost != null) {
        val index = when (injectionHost.injectedExpressions.size) {
            0 -> null
            1 -> 0
            else -> InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(injectionContext)?.indexOfFirst { it.first == file }
        }
        val injectedExpr = if (index != null) injectionHost.injectedExpressions[index] else null
        val cExpr = (psiDef as? ArendDefFunction)?.functionBody?.expr?.let { ConcreteBuilder.convertExpression(it) }
        if (injectedExpr != null && cExpr != null && index != null && index < injectionHost.injectedExpressions.size) {
            val scope = CachingScope.make(injectionHost.scope)
            val subExprVisitor = CorrespondedSubExprVisitor(resolver?.result ?: subExpr)
            errors = subExprVisitor.errors
            cExpr.accept(ExpressionResolveNameVisitor(ArendReferableConverter, scope, null, DummyErrorReporter.INSTANCE, null), null)
                .accept(SyntacticDesugarVisitor(DummyErrorReporter.INSTANCE), null)
                .accept(subExprVisitor, injectedExpr)
        } else {
            errors = emptyList()
            null
        }
    } else {
        concreteDef ?: throw SubExprException("selected text is not in a definition")
        val def = psiDef.tcReferable?.typechecked
            ?: throw SubExprException("underlying definition is not type checked")
        val subDefVisitor = CorrespondedSubDefVisitor(resolver?.result ?: subExpr)
        errors = subDefVisitor.exprError
        concreteDef.accept(subDefVisitor, def)
    }) ?: throw SubExprException(buildString {
        append("cannot find a suitable subexpression")

        if (errors.any { it.kind == SubExprError.Kind.MetaRef })
            append(" (maybe because you're using meta defs)")
    }, body)

    return SubExprResult(result.proj1, resolver?.originalExpr ?: result.proj2, exprAncestor)
}

fun tryCorrespondedSubExpr(range: TextRange, file: PsiFile, project: Project, editor: Editor, showError : Boolean = true): SubExprResult? =
    try {
        correspondedSubExpr(range, file, project)
    } catch (e: SubExprException) {
        if (showError) ApplicationManager.getApplication().invokeLater {
            HintManager.getInstance().showErrorHint(editor, "Failed because ${e.message}")
        }
        null
    }

private fun everyExprOf(concrete: Concrete.Expression): Sequence<Any?> = sequence {
    yield(concrete.data)
    if (concrete is Concrete.AppExpression)
        for (arg in concrete.arguments) yieldAll(everyExprOf(arg.expression))
    if (concrete is Concrete.BinOpSequenceExpression) {
        for (arg in concrete.sequence) yieldAll(everyExprOf(arg.expression))
        concrete.clauses?.let { yield(it.data) }
    }
}

fun rangeOfConcrete(subConcrete: Concrete.Expression): TextRange {
    val exprs = everyExprOf(subConcrete)
            .filterIsInstance<PsiElement>()
            .toList()
    if (exprs.size == 1) return exprs.first().textRange
    // exprs is guaranteed to be empty
    val leftMost = exprs.minByOrNull { it.textRange.startOffset }!!
    val rightMost = exprs.maxByOrNull { it.textRange.endOffset }!!
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
): Pair<Abstract.Expression, List<Abstract.BinOpSequenceElem>>? {
    val head: PsiElement
    val tail: Sequence<PsiElement>
    if (range.isEmpty) {
        when (val firstExpr = parent.linearDescendants.filterIsInstance<Abstract.Expression>().lastOrNull()
            ?: parent.childrenWithLeaves.filterIsInstance<Abstract.Expression>().toList().takeIf { it.size == 1 }?.first()) {
            is ArendTuple -> {
                val tupleExprList = firstExpr.tupleExprList
                head = tupleExprList.firstOrNull() ?: return null
                tail = tupleExprList.asSequence().drop(1)
            }
            is ArendTupleExpr -> {
                val exprList = firstExpr.exprList
                head = exprList.firstOrNull() ?: return null
                tail = exprList.asSequence().drop(1)
            }
            null -> return null
            else -> return firstExpr to emptyList()
        }
    } else {
        val exprSeq = parent.childrenWithLeaves
            .dropWhile { it.textRange.endOffset < range.startOffset }
            .takeWhile { it.textRange.startOffset < range.endOffset }
            .filter { it !is PsiWhiteSpace && it !is LeafPsiElement }
        head = (exprSeq.firstOrNull() ?: return null)
        tail = exprSeq.drop(1)
    }
    if (tail.none()) {
        val subCollected = collectArendExprs(head, range)
        if (subCollected != null) {
            val (_, tails) = subCollected
            if (tails.isNotEmpty()) return subCollected
        }
        return if (head is Abstract.Expression) Pair<Abstract.Expression, List<Abstract.BinOpSequenceElem>>(head, emptyList())
        else null
    } else return when (val headExpr = head.linearDescendants.filterIsInstance<Abstract.Expression>().lastOrNull()) {
        null -> null
        else -> headExpr to tail.mapNotNull {
            it.linearDescendants.filterIsInstance<Abstract.BinOpSequenceElem>().firstOrNull()
        }.toList()
    }
}

fun exprToConcrete(
        project: Project,
        expression: Expression,
        mode: NormalizationMode? = null,
        renamer: DefinitionRenamer? = null
): Concrete.Expression {
    val settings = project.service<ArendProjectSettings>()
    return ToAbstractVisitor.convert(expression, object : PrettyPrinterConfig {
        override fun getExpressionFlags() = settings.popupPrintingOptionsFilterSet
        override fun getNormalizationMode() = mode
        override fun getDefinitionRenamer() = renamer
    })
}

inline fun normalizeExpr(
        project: Project,
        subCore: Expression,
        mode: NormalizationMode = NormalizationMode.RNF,
        renamer: DefinitionRenamer? = null,
        crossinline after: (Concrete.Expression) -> Unit
) {
    val title = "Running normalization"
    var result: Concrete.Expression? = null
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, true) {
        override fun run(indicator: ProgressIndicator) {
            result = ComputationRunner<Concrete.Expression>().run(ProgressCancellationIndicator(indicator)) {
                runReadAction {
                    exprToConcrete(project, subCore, mode, renamer)
                }
            }
        }

        override fun onFinished() = result?.let(after) ?: Unit
    })
}
