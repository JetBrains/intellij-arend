package org.arend.quickfix.instance
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.param.DependentLink
import org.arend.core.expr.ReferenceExpression
import org.arend.ext.error.LocalError
import org.arend.psi.*
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.splitTele
import org.arend.resolving.DataLocatedReferable
import org.arend.typechecking.error.local.inference.InstanceInferenceError

class ReplaceWithLocalInstanceQuickFix(val error: InstanceInferenceError, val cause: SmartPsiElementPointer<ArendLongName>): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = "Replace local parameter with a local instance"

    override fun getFamilyName(): String = "arend.instance"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val classifyingExpression = this.error.classifyingExpression as ReferenceExpression
        var index = DependentLink.Helper.toList(((error as LocalError).definition as DataLocatedReferable).typechecked.parameters).indexOf(classifyingExpression.binding)
        val ambientDefinition = (error.definition as DataLocatedReferable).data?.element
        val missingClassInstance = (error.classRef.data as? SmartPsiElementPointer<*>)?.element
        val l  = when (ambientDefinition) {
            is ArendFunctionalDefinition -> ambientDefinition.nameTeleList.map { tele -> Pair(tele, tele.identifierOrUnknownList.size) }
            is ArendDefData -> ambientDefinition.typeTeleList.map { tele -> Pair(tele, tele.typedExpr?.identifierOrUnknownList?.size ?: 1) }
            else -> null
        }

        if (l != null) {
            val iterator = l.iterator()
            var tele: Pair<PsiElement, Int>? = null
            while (iterator.hasNext()) {
                tele = iterator.next()
                if (index < tele.second) break
                index -= tele.second
            }

            if (tele != null && index < tele.second && ambientDefinition != null && missingClassInstance is PsiLocatedReferable) {
                val className = ResolveReferenceAction.getTargetName(missingClassInstance, ambientDefinition).let { if (it.isNullOrEmpty()) missingClassInstance.defIdentifier?.textRepresentation() else it }
                val psiFactory = ArendPsiFactory(project)
                val telescope = tele.first
                splitTele(telescope, index)
                if (className != null) when (telescope) {
                    is ArendNameTele -> telescope.expr
                    is ArendTypeTele -> telescope.typedExpr?.expr
                    else -> null
                }?.replaceWithNotification(psiFactory.createExpression(className))
            }
        }

    }

}