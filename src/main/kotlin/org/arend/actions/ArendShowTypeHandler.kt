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
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.visitor.CorrespondedSubExprVisitor


class ArendShowTypeHandler(val requestFocus: Boolean) : CodeInsightActionHandler {
	override fun startInWriteAction() = false

	private fun displayHint(typeInfo: String, editor: Editor) {
		ApplicationManager.getApplication().invokeLater {
			HintManager.getInstance()
					.apply { setRequestFocusForNextHint(requestFocus) }
					.showInformationHint(editor, typeInfo)
		}
	}

	fun doInvoke(project: Project, editor: Editor, file: PsiFile): Boolean {
		val range = EditorUtil.getSelectionInAnyMode(editor)
		val parent = PsiTreeUtil.findCommonParent(
				file.findElementAt(range.startOffset),
				file.findElementAt(range.endOffset)
		) ?: return false
		val psiDef = parent.ancestor<ArendDefinition>() ?: return false
		val service = project.service<TypeCheckingService>()
		// Only work for functions right now
		val concreteDef = psiDef
				.computeConcrete(service.newReferableConverter(false), DummyErrorReporter.INSTANCE)
				as? Concrete.FunctionDefinition ?: return false
		val def = service.getTypechecked(psiDef) as? FunctionDefinition
				?: return false
		// I suppose that all children are `ArendExpr`s

		val children = collectArendExprs(parent, range)
				.map { ConcreteBuilder.convertExpression(IdReferableConverter.INSTANCE, it) }
				.map { Concrete.BinOpSequenceElem(it) }
				.toList()
				.takeIf { it.isNotEmpty() } ?: return false
		val parser = BinOpParser(DummyErrorReporter.INSTANCE)
		val body = def.actualBody as? Expression ?: return false
		// Only work for single clause right now
		val concreteBody = concreteDef.body.term ?: return false

		val subExpr = if (children.size == 1) {
			val e = children.first().expression
			if (e is Concrete.BinOpSequenceExpression) parser.parse(e)
			else e
		} else parser.parse(Concrete.BinOpSequenceExpression(null, children))
		val visited = concreteBody.accept(CorrespondedSubExprVisitor(subExpr), body)
		val subCore = visited.proj1
		val subConcrete = visited.proj2
		val psi = subConcrete.data as? PsiElement ?: return false
		editor.selectionModel.setSelection(psi.textRange.startOffset, psi.textRange.endOffset)
		displayHint(subCore.type.toString(), editor)
		return true
	}

	private fun collectArendExprs(parent: PsiElement, range: TextRange): Sequence<ArendExpr> {
		val exprs = parent.childrenWithLeaves
				.dropWhile { it.textRange.startOffset < range.startOffset }
				.takeWhile { it.textRange.endOffset <= range.endOffset }
				.toList()
		if (exprs.isEmpty()) return emptySequence()
		if (exprs.size == 1) {
			val first = exprs.first()
			return if (first is ArendExpr) return sequenceOf(first)
			else collectArendExprs(first, range)
		} else return exprs.asSequence().filterIsInstance<ArendExpr>()
	}

	override fun invoke(project: Project, editor: Editor, file: PsiFile) {
		if (!doInvoke(project, editor, file)) {
			displayHint("Failed to obtain type", editor)
		}
	}
}
