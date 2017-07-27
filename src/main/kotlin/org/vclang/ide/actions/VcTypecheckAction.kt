package org.vclang.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.vclang.ide.typecheck.VcTypecheckerFrontend
import java.nio.file.Paths

class VcTypecheckAction : AnAction() {
    var frontend: VcTypecheckerFrontend? = null

    init {
        templatePresentation.text = "Typecheck file"
        templatePresentation.description = "Typecheck file"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE)
        val sourceDir = Paths.get("${project.basePath}/test")
        val cacheDir = Paths.get("${project.basePath}/.cache/test")
        val filePath = sourceDir.relativize(Paths.get(virtualFile.path))

        if (frontend == null) {
            frontend = VcTypecheckerFrontend(project, sourceDir, cacheDir, false)
            frontend?.loadPrelude()
        }
        frontend?.run(listOf(filePath.toString()))
    }
}
