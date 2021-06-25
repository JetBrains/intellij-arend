package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.psi.ArendFieldAcc
import org.arend.psi.ArendGoal
import org.arend.psi.parentOfType
import org.arend.util.ArendBundle

class GenerateFunctionIntention : BaseArendIntention(ArendBundle.message("arend.generate.function")) {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return element.parentOfType<ArendGoal>(false) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        // todo
    }
}
