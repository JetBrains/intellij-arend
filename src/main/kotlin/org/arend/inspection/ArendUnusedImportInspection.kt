package org.arend.inspection

import com.intellij.codeInspection.ProblemsHolder
import org.arend.psi.ArendVisitor

class ArendUnusedImportInspection : ArendInspectionBase() {

    companion object {
        const val ID = "ArendUnusedImportInspection"
    }

    override fun getID(): String = ID

    override fun buildArendVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): ArendVisitor {
        return ArendVisitor()
    }
}