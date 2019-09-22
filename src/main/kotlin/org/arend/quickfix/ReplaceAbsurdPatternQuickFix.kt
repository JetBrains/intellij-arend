package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.definition.Constructor
import org.arend.intention.SplitAtomPatternIntention
import org.arend.psi.ext.ArendCompositeElement

class ReplaceAbsurdPatternQuickFix(private val constructors: Collection<Constructor>, private val causePointer: SmartPsiElementPointer<ArendCompositeElement>): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.pattern"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = causePointer.element != null

    override fun getText(): String  = "Replace with expected pattern constructors"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        SplitAtomPatternIntention.doSplitPattern(causePointer.element ?: return, project, editor, constructors, generateBody = true)
    }
}