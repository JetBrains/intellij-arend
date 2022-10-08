package org.arend.quickfix.replacers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.concrete.definition.FunctionKind
import org.arend.ext.concrete.definition.FunctionKind.*
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.util.ArendBundle

class ReplaceFunctionKindQuickFix(private val kwRef: SmartPsiElementPointer<ArendCompositeElement>, private val kind: FunctionKind) : IntentionAction {
    init {
        if (kind.isCoclause) throw IllegalArgumentException()
    }

    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = kwRef.element != null

    override fun getText(): String = ArendBundle.message("arend.replace.function.kind", kind.text)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val kw = kwRef.element ?: return
        val factory = ArendPsiFactory(project)
        kw.replaceWithNotification(if (kind == INSTANCE || kind == CONS) factory.createInstanceKeyword(kind.text) else factory.createFunctionKeyword(kind.text))
    }
}