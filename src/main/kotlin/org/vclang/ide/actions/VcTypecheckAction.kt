package org.vclang.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.vclang.ide.typecheck.TypecheckerFrontend
import org.vclang.lang.core.getPsiFor
import org.vclang.lang.core.psi.contentRoot
import org.vclang.lang.core.psi.sourceRoot
import java.nio.file.Paths

class VcTypecheckAction : AnAction() {
    var frontend: TypecheckerFrontend? = null

    init {
        templatePresentation.text = "Type check file"
        templatePresentation.description = "Type check file"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualModuleFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE)
        val moduleFile = project.getPsiFor(virtualModuleFile) ?: return
        val sourceRoot = moduleFile.sourceRoot ?: moduleFile.contentRoot ?: return
        val sourcePath = Paths.get(sourceRoot.path)
        val modulePath = sourcePath.relativize(Paths.get(virtualModuleFile.path))

        if (frontend == null) {
            frontend = TypecheckerFrontend(project, sourcePath)
        }
        frontend?.typecheck(modulePath, moduleFile.name)
    }
}
