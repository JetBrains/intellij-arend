package org.arend.server

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findDirectory
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.module.ModuleLocation
import org.arend.module.config.ArendModuleConfigService
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.ReferableBase
import org.arend.term.abs.AbstractReferable
import org.arend.term.abs.AbstractReference
import org.arend.term.abs.ConcreteBuilder
import org.arend.util.FileUtils
import org.arend.util.findInternalLibrary
import org.arend.util.findLibrary
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

    override fun getFiles(libraryName: String, inTests: Boolean, prefix: List<String>): List<String>? {
        val library = project.findLibrary(libraryName) ?: return null
        var dir = (if (inTests) library.testsDirFile else library.sourcesDirFile) ?: return null
        for (name in prefix) {
            dir = dir.findDirectory(name) ?: return null
        }
        return dir.children.mapNotNull { when {
            it.isDirectory -> it.name
            it.name.endsWith(FileUtils.EXTENSION) -> it.name.removeSuffix(FileUtils.EXTENSION)
            else -> null
        } }
    }

    override fun runUnderReadLock(runnable: Runnable) {
        runReadAction {
            runnable.run()
        }
    }

    override fun addReference(module: ModuleLocation, reference: AbstractReference, referable: Referable) {
        (reference as? ArendReferenceElement)?.putResolved(referable)
    }

    override fun addReference(module: ModuleLocation, referable: AbstractReferable, tcReferable: TCDefReferable) {
        (referable as? ReferableBase<*>)?.tcReferable = tcReferable
    }

    override fun addModuleDependency(module: ModuleLocation, dependency: ModuleLocation) {}

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