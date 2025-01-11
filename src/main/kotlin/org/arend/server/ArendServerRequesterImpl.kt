package org.arend.server

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.module.ModuleLocation
import org.arend.module.config.ArendModuleConfigService
import org.arend.term.abs.ConcreteBuilder
import org.arend.util.findInternalLibrary
import org.arend.util.moduleConfigs

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

    private fun requestUpdate(server: ArendServer, modules: List<ModulePath>, library: String, inTests: Boolean) {
        for (module in modules) {
            val file = project.findInternalLibrary(library)?.findArendFile(module, inTests)
            if (file != null) server.updateModule(file.modificationStamp, ModuleLocation(library, if (inTests) ModuleLocation.LocationKind.TEST else ModuleLocation.LocationKind.SOURCE, module)) {
                ConcreteBuilder.convertGroup(file, DummyErrorReporter.INSTANCE)
            }
        }
    }

    private fun requestUpdate(server: ArendServer, config: ArendModuleConfigService, withTests: Boolean) {
        requestUpdate(server, config.findModules(false), config.name, false)
        if (withTests) {
            requestUpdate(server, config.findModules(true), config.name, true)
        }
    }

    fun requestUpdate(server: ArendServer, library: String?, withTests: Boolean) {
        runReadAction {
            if (library == null) {
                for (config in project.moduleConfigs) {
                    requestUpdate(server, config, withTests)
                }
            } else {
                requestUpdate(server, project.findInternalLibrary(library) ?: return@runReadAction, withTests)
            }
        }
    }
}