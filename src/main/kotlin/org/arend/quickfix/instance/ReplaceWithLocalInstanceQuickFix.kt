package org.arend.quickfix.instance
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.param.DependentLink
import org.arend.core.expr.ReferenceExpression
import org.arend.psi.ArendPsiFactory
import org.arend.psi.getTeleType
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.splitTele
import org.arend.ext.error.InstanceInferenceError
import org.arend.ext.reference.DataContainer
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ext.*
import org.arend.util.ArendBundle

class ReplaceWithLocalInstanceQuickFix(val error: InstanceInferenceError, val cause: SmartPsiElementPointer<ArendLongName>): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.instance.replaceWithLocalInstance")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val classifyingExpression = error.classifyingExpression as ReferenceExpression
        val definition = (error.definition as? TCDefReferable)?.typechecked ?: return
        var index = DependentLink.Helper.toList(definition.parameters).indexOf(classifyingExpression.binding)
        val ambientDefinition = (error.definition as? DataContainer)?.data as? ArendCompositeElement
        val missingClassInstance = ((error.classRef as? TCDefReferable)?.data as? SmartPsiElementPointer<*>)?.element
        val l  = when (ambientDefinition) {
            is ArendFunctionDefinition<*> -> ambientDefinition.parameters.map { tele -> Pair(tele, tele.identifierOrUnknownList.size) }
            is ArendDefData -> ambientDefinition.parameters.map { tele -> Pair(tele, tele.typedExpr?.identifierOrUnknownList?.size ?: 1) }
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
                val className = ResolveReferenceAction.getTargetName(missingClassInstance, ambientDefinition).let {
                    it.second?.execute()
                    it.first.ifEmpty { missingClassInstance.defIdentifier?.refName }
                }
                val psiFactory = ArendPsiFactory(project)
                val telescope = tele.first
                splitTele(telescope, index)
                if (className != null) getTeleType(telescope)?.replace(psiFactory.createExpression(className))
            }
        }

    }

}