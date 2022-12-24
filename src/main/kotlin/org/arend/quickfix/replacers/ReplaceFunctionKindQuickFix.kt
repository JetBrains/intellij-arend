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

    override fun getText(): String = ArendBundle.message("arend.replace.function.kind", kindDescription)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val kw = kwRef.element ?: return
        val factory = ArendPsiFactory(project)
        kw.replace(if (kind == INSTANCE || kind == CONS) factory.createInstanceKeyword(kindDescription) else factory.createFunctionKeyword(kindDescription))
    }

    private val kindDescription = when (kind) {
        COERCE -> "\\use \\coerce"
        LEVEL -> "\\use \\level"
        SFUNC -> "\\sfunc"
        LEMMA -> "\\lemma"
        TYPE -> "\\type"
        FUNC -> "\\func"
        CONS -> "\\cons"
        INSTANCE -> "\\instance"
        AXIOM -> "\\axiom"
        FUNC_COCLAUSE, CLASS_COCLAUSE -> throw IllegalStateException()
    }
}