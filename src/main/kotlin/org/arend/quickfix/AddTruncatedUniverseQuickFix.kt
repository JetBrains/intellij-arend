package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.addSiblingAfter
import org.arend.core.definition.Constructor
import org.arend.core.elimtree.IntervalElim
import org.arend.psi.ArendElementTypes.PROP_KW
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.ArendDefData
import org.arend.resolving.DataLocatedReferable
import org.arend.util.ArendBundle
import kotlin.math.max

class AddTruncatedUniverseQuickFix(private val cause: SmartPsiElementPointer<ArendDefData>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.truncated.universe.add")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val defData = cause.element

        var maxPatternMatching = 0
        for (constructor in (defData?.constructors ?: emptyList())) {
            val definition = (constructor.tcReferable as? DataLocatedReferable?)?.typechecked
            if (definition is Constructor) {
                if (definition.body is IntervalElim) {
                    maxPatternMatching = max(maxPatternMatching, (definition.body as IntervalElim).cases.size)
                }
            }
        }

        val psiFactory = ArendPsiFactory(project)
        val universeName = if (maxPatternMatching == 0) {
            PROP_KW.debugName
        } else {
            "\\${maxPatternMatching - 1}-Type"
        }
        val actualUniverse = psiFactory.createUniverse(universeName)
        val space = psiFactory.createWhitespace(" ")

        (if (defData?.dataBody != null) {
            defData.dataBody?.prevSibling?.prevSibling
        } else {
            defData?.lastChild
        })?.run {
            addSiblingAfter(actualUniverse)
            addSiblingAfter(space)
            addSiblingAfter(psiFactory.createColon())
            addSiblingAfter(space)
        }
    }
}
