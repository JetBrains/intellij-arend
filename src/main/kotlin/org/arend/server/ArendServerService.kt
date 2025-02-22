package org.arend.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.arend.ArendLanguage
import org.arend.error.DummyErrorReporter
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ArendPreludeLibrary.Companion.PRELUDE_FILE_NAME
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.server.impl.ArendServerImpl
import org.arend.term.abs.ConcreteBuilder
import org.arend.util.FileUtils
import java.nio.charset.StandardCharsets

@Service(Service.Level.PROJECT)
class ArendServerService(val project: Project) : Disposable {
    val prelude: ArendFile?
    val server: ArendServer =
        ArendServerImpl(ArendServerRequesterImpl(project), true, true, null)

    init {
        val preludeText = String(ArendPreludeLibrary::class.java.getResourceAsStream("/lib/Prelude" + FileUtils.EXTENSION)!!.readBytes(), StandardCharsets.UTF_8)
        prelude = runReadAction {
            val prelude = PsiFileFactory.getInstance(project).createFileFromText(PRELUDE_FILE_NAME, ArendLanguage.INSTANCE, preludeText) as? ArendFile
            if (prelude != null) {
                prelude.virtualFile?.isWritable = false
                prelude.generatedModuleLocation = Prelude.MODULE_LOCATION
                server.addReadOnlyModule(Prelude.MODULE_LOCATION, ConcreteBuilder.convertGroup(prelude, Prelude.MODULE_LOCATION, DummyErrorReporter.INSTANCE))
            }
            prelude
        }
    }

    override fun dispose() {}
}