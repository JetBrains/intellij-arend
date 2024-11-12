package org.arend.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.arend.psi.ArendFile
import org.arend.util.checkArcFile

abstract class ArendInspectionBase : LocalInspectionTool() {
    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return if (checkArcFile(holder.file.virtualFile) || (holder.file as? ArendFile)?.isInjected == true) {
            PsiElementVisitor.EMPTY_VISITOR
        } else {
            buildArendVisitor(holder, isOnTheFly)
        }
    }


    abstract fun buildArendVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor
}