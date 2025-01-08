package org.arend.server

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import org.arend.error.DummyErrorReporter
import org.arend.module.ModuleLocation
import org.arend.term.abs.ConcreteBuilder
import org.arend.util.findInternalLibrary

class ArendServerRequesterImpl(private val project: Project) : ArendServerRequester {
    override fun requestModuleUpdate(server: ArendServer, module: ModuleLocation) {
        if (module.locationKind == ModuleLocation.LocationKind.GENERATED) return
        runReadAction {
            val file = project.findInternalLibrary(module.libraryName)?.findArendFile(module.modulePath, module.locationKind == ModuleLocation.LocationKind.TEST)
            if (file != null) server.updateModule(file.modificationStamp, module) {
                ConcreteBuilder.convertGroup(file, DummyErrorReporter.INSTANCE)
            }
        }
    }
}