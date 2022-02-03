package org.arend.intention

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.arend.codeInsight.ArendImportOptimizer
import org.arend.codeInsight.OptimizationResult
import org.arend.psi.ArendFile
import org.arend.util.ArendBundle

internal class ArendOptimizeImportsQuickFix(private val optimizationResult: OptimizationResult) : LocalQuickFix {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = ArendBundle.message("arend.optimize.imports.intention.family.name")

    override fun getName(): String = ArendBundle.message("arend.optimize.imports.intention.name")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        ArendImportOptimizer().psiModificationRunnable(descriptor.psiElement.containingFile as ArendFile, optimizationResult).run()
    }
}