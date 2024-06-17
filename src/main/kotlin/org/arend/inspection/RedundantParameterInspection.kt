package org.arend.inspection

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.descendantsOfType
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.ArendDefIdentifier
import org.arend.psi.ext.ArendNameTele
import org.arend.psi.ext.ArendRefIdentifier
import org.arend.util.ArendBundle

class RedundantParameterInspection : ArendInspectionBase() {

    override fun buildArendVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        fun registerFix(element: ArendDefIdentifier) {
            val message = ArendBundle.message("arend.inspection.parameter.redundant", element.name)
            holder.registerProblem(element, message, RedundantParameterFix(element))
        }

        val file = holder.file
        val arendRefIdentifiers = file.descendantsOfType<ArendRefIdentifier>().map { it.resolve }.toList()

        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                if (element is ArendNameTele) {
                    val identifiers = element.identifierOrUnknownList
                        .filter { identifier -> identifier.defIdentifier != null }
                        .map { identifier -> identifier.defIdentifier!! }

                    for (identifier in identifiers) {
                        if (!arendRefIdentifiers.contains(identifier)) {
                            registerFix(identifier)
                        }
                    }
                }
            }
        }
    }

    companion object {
        class RedundantParameterFix(private val defIdentifier: ArendDefIdentifier): LocalQuickFixOnPsiElement(defIdentifier) {

            override fun getFamilyName(): String = text

            override fun getText(): String = ArendBundle.message("arend.inspection.redundant.parameter.fix")

            override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
                val psiFactory = ArendPsiFactory(project)
                val underlining = psiFactory.createUnderlining()
                defIdentifier.replace(underlining)
            }
        }
    }
}
