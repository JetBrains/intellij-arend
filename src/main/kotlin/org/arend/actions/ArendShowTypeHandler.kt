package org.arend.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.arend.core.definition.FunctionDefinition
import org.arend.core.expr.Expression
import org.arend.error.DummyErrorReporter
import org.arend.naming.BinOpParser
import org.arend.naming.reference.converter.IdReferableConverter
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendExpr
import org.arend.psi.ancestor
import org.arend.psi.childrenWithLeaves
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.visitor.CorrespondedSubExprVisitor
import org.arend.util.BinOpExpansionVisitor


class ArendShowTypeHandler(val requestFocus: Boolean) : CodeInsightActionHandler {
	override fun startInWriteAction() = false

	private fun displayHint(typeInfo: String, editor: Editor) {
		ApplicationManager.getApplication().invokeLater {
			HintManager.getInstance()
					.apply { setRequestFocusForNextHint(requestFocus) }
					.showInformationHint(editor, typeInfo)
		}
	}

	fun doInvoke(project: Project, editor: Editor, file: PsiFile): String? {
		val range = EditorUtil.getSelectionInAnyMode(editor)
		val possibleParent = PsiTreeUtil.findCommonParent(
				file.findElementAt(range.startOffset),
				file.findElementAt(range.endOffset - 1)
		) ?: return "selected expr in bad position"
		val parent = possibleParent.ancestor<ArendExpr>()?.parent
				?: return "selected text is not an arend expression"
		val psiDef = parent.ancestor<ArendDefinition>()
				?: return "selected text is not in a definition"
		val service = project.service<TypeCheckingService>()
		// Only work for functions right now
		val concreteDef = PsiConcreteProvider(
				project,
				service.newReferableConverter(false),
				DummyErrorReporter.INSTANCE,
				null
		).getConcrete(psiDef)
				as? Concrete.FunctionDefinition
				?: return "selected text is not in a function definition"
		val def = service.getTypechecked(psiDef) as FunctionDefinition

		val children = collectArendExprs(parent, range)
				.map { ConcreteBuilder.convertExpression(IdReferableConverter.INSTANCE, it) }
				.map(Concrete::BinOpSequenceElem)
				.toList()
				.takeIf { it.isNotEmpty() }
				?: return "cannot find a suitable subexpression"
		val parser = BinOpParser(DummyErrorReporter.INSTANCE)
		val body = def.actualBody as? Expression
				?: return "function body is not an expression"
		// Only work for single clause right now
		val concreteBody = concreteDef.body.term
				?: return "does not yet support multiple clauses"

		val subExpr = if (children.size == 1)
			children.first().expression
		else parser.parse(Concrete.BinOpSequenceExpression(null, children))
		val subExprVisitor = CorrespondedSubExprVisitor(subExpr.accept(BinOpExpansionVisitor, parser))
		val visited = concreteBody
				.accept(BinOpExpansionVisitor, parser)
				.accept(subExprVisitor, body)
				?: return "cannot find a suitable subexpression"
		val subCore = visited.proj1
		val textRange = rangeOf(visited.proj2)
		editor.selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
		displayHint(subCore.type.toString(), editor)
		return null
	}

	private fun rangeOf(subConcrete: Concrete.Expression): TextRange {
		val expr = if (subConcrete is Concrete.AppExpression) {
			val function = subConcrete.data as PsiElement
			PsiTreeUtil.findCommonParent(
					function,
					subConcrete.arguments.first().expression.data as PsiElement
			) ?: function
		} else subConcrete.data as PsiElement
		return expr.textRange
	}

	private fun collectArendExprs(parent: PsiElement, range: TextRange): Sequence<ArendExpr> {
		val exprs = parent.childrenWithLeaves
				.dropWhile { it.textRange.endOffset < range.startOffset }
				.takeWhile { it.textRange.startOffset <= range.endOffset }
				.toList()
		if (exprs.isEmpty()) return emptySequence()
		if (exprs.size == 1) {
			val first = exprs.first()
			return if (first is ArendExpr) return sequenceOf(first)
			else collectArendExprs(first, range)
		} else return exprs.asSequence().filterIsInstance<ArendExpr>()
	}

	override fun invoke(project: Project, editor: Editor, file: PsiFile) {
		val s = doInvoke(project, editor, file)
		if (s != null) displayHint("Failed to obtain type because $s", editor)
	}
}
