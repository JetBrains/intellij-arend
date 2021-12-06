package org.arend.quickfix.replacers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.definition.Definition
import org.arend.intention.SplitAtomPatternIntention
import org.arend.psi.ext.ArendCompositeElement
import org.arend.util.ArendBundle

class ReplaceAbsurdPatternQuickFix(private val constructors: Collection<Definition>,
                                   private val causeRef: SmartPsiElementPointer<ArendCompositeElement>): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = causeRef.element != null

    override fun getText(): String  = ArendBundle.message("arend.pattern.replaceWithConstructors")

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val cause = causeRef.element ?: return
        SplitAtomPatternIntention.doSplitPattern(cause, project, constructors.map { SplitAtomPatternIntention.Companion.ConstructorSplitPatternEntry(it, null, null) }, generateBody = true)
    }
}