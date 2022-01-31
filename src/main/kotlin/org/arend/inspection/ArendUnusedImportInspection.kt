package org.arend.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool

class ArendUnusedImportInspection : LocalInspectionTool(), UnfairLocalInspectionTool {
    companion object {
        const val ID = "ArendUnusedImportInspection"
    }
}