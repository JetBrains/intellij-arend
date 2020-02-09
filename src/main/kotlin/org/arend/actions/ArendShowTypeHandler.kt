package org.arend.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
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

	override fun invoke(project: Project, editor: Editor, file: PsiFile) {
		val range = EditorUtil.getSelectionInAnyMode(editor)
		val parent = PsiTreeUtil.findCommonParent(
				file.findElementAt(range.startOffset),
				file.findElementAt(range.endOffset)
		) ?: return
		val psiDef = parent.ancestor<ArendDefinition>() ?: return
		val service = project.service<TypeCheckingService>()
		// Only work for functions right now
		val concreteDef = psiDef
				.computeConcrete(service.newReferableConverter(false), DummyErrorReporter.INSTANCE)
				as? Concrete.FunctionDefinition ?: return
		val def = service.getTypechecked(psiDef) as? FunctionDefinition ?: return
		// I suppose that all children are `ArendExpr`s
		val children = parent.childrenWithLeaves
				.filterIsInstance<ArendExpr>()
				.dropWhile { it.textRange.startOffset <= range.startOffset }
				.takeWhile { it.textRange.endOffset <= range.endOffset }
				.map { ConcreteBuilder.convertExpression(IdReferableConverter.INSTANCE, it) }
				.map { Concrete.BinOpSequenceElem(it) }
				.toList()

		val body = def.actualBody as? Expression ?: return
		// Only work for single clause right now
		val concreteBody = concreteDef.body.term ?: return

		val subExpr = BinOpParser(DummyErrorReporter.INSTANCE)
				.parse(Concrete.BinOpSequenceExpression(null, children))
		val visited = concreteBody.accept(CorrespondedSubExprVisitor(subExpr), body)
		val subCore = visited.proj1
		val subConcrete = visited.proj2
		val psi = subConcrete.data as? PsiElement ?: return
		editor.selectionModel.setSelection(psi.textRange.startOffset, psi.textRange.endOffset)
		displayHint(subCore.type.toString(), editor)
	}
}
