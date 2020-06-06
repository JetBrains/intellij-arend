package org.arend.module

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFileFactory
import org.arend.ArendLanguage
import org.arend.ext.error.ErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.Precedence
import org.arend.ext.ui.ArendUI
import org.arend.library.LibraryHeader
import org.arend.library.LibraryManager
import org.arend.library.SourceLibrary
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.ExternalLibraryConfig
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.EmptyGlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.MetaReferable
import org.arend.naming.scope.Scope
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ext.impl.ModuleAdapter
import org.arend.source.BinarySource
import org.arend.source.FileBinarySource
import org.arend.source.GZIPStreamBinarySource
import org.arend.term.group.Group
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.order.listener.TypecheckingOrderingListener
import org.arend.ui.impl.ArendGeneralUI
import org.arend.util.FileUtils
import java.lang.StringBuilder

class ArendRawLibrary(val config: LibraryConfig)
    : SourceLibrary(config.project.service<TypeCheckingService>().typecheckerState) {

    constructor(module: Module): this(ArendModuleConfigService.getConfig(module))

    override fun isExternal() = config is ExternalLibraryConfig

    override fun getName() = config.name

    override fun getModuleGroup(modulePath: ModulePath, inTests: Boolean) =
        config.findArendFile(modulePath, inTests)

    override fun mustBeLoaded() = !isExternal

    override fun getUI(): ArendUI? = ArendGeneralUI(config.project)

    override fun loadHeader(errorReporter: ErrorReporter) =
        LibraryHeader(config.findModules(false), config.dependencies, config.langVersion, config.extensionsPath, config.extensionMainClass)

    fun addGeneratedModule(modulePath: ModulePath, scope: Scope) {
        val builder = StringBuilder()
        scopeToText(scope, "", builder)
        val file = PsiFileFactory.getInstance(config.project).createFileFromText(modulePath.lastName + FileUtils.EXTENSION, ArendLanguage.INSTANCE, builder.toString()) as? ArendFile ?: return
        file.virtualFile.isWritable = false
        file.generatedModuleLocation = ModuleLocation(name, ModuleLocation.LocationKind.GENERATED, modulePath)
        fillGroup(file, scope)
        config.project.service<TypeCheckingService>().fillAdditionalNames(file, isExternal)
        config.addAdditionalModule(modulePath, file)
    }

    override fun load(libraryManager: LibraryManager, typechecking: TypecheckingOrderingListener?): Boolean {
        if (!super.load(libraryManager, typechecking)) {
            return false
        }

        for (entry in additionalModules) {
            addGeneratedModule(entry.key, entry.value)
        }

        return true
    }

    override fun unload(): Boolean {
        super.unload()
        config.clearAdditionalModules()
        return isExternal
    }

    override fun getLoadedModules() = config.findModules(false)

    override fun getTestModules() = config.findModules(true)

    override fun getDependencies() = config.dependencies

    override fun getRawSource(modulePath: ModulePath) =
        config.findArendFile(modulePath, false)?.let { ArendRawSource(it) } ?: ArendFakeRawSource(modulePath)

    override fun getTestSource(modulePath: ModulePath) =
        config.findArendFile(modulePath, true)?.let { ArendRawSource(it) } ?: ArendFakeRawSource(modulePath)

    override fun getBinarySource(modulePath: ModulePath): BinarySource? =
        config.binariesPath?.let { GZIPStreamBinarySource(FileBinarySource(it, modulePath)) }

    override fun containsModule(modulePath: ModulePath) = config.containsModule(modulePath)

    override fun resetDefinition(referable: LocatedReferable) {
        if (referable !is ArendDefinition) {
            return
        }
        runReadAction {
            if (!config.project.isDisposed) {
                config.project.service<TypeCheckingService>().updateDefinition(referable, null, TypeCheckingService.LastModifiedMode.DO_NOT_TOUCH)
            }
        }
    }

    override fun getReferableConverter() = config.project.service<TypeCheckingService>().newReferableConverter(true)

    override fun getDependencyListener() = config.project.service<TypeCheckingService>().dependencyListener

    companion object {
        fun getLibraryFor(libraryManager: LibraryManager, module: Module) =
            libraryManager.getRegisteredLibrary { ((it as? ArendRawLibrary)?.config as? ArendModuleConfigService)?.module == module } as? ArendRawLibrary

        fun getExternalLibrary(libraryManager: LibraryManager, name: String) =
            libraryManager.getRegisteredLibrary { ((it as? ArendRawLibrary)?.config as? ExternalLibraryConfig)?.name == name } as? ArendRawLibrary

        private fun scopeToText(scope: Scope, prefix: String, builder: StringBuilder) {
            var first = true

            for (element in scope.elements) {
                if (!(element is MetaReferable || element is EmptyGlobalReferable)) {
                    continue
                }

                val name = element.refName
                val subscope = scope.resolveNamespace(name, false)
                if (subscope == null && element is EmptyGlobalReferable) {
                    continue
                }

                if (first) {
                    first = false
                } else {
                    builder.append("\n\n")
                }

                builder.append(prefix)
                if (element is MetaReferable) {
                    if (element.description.isNotEmpty()) {
                        val lines = element.description.split('\n')
                        if (lines.size == 1) {
                            builder.append("-- | ").append(lines[0])
                        } else {
                            builder.append("{- | ")
                            var firstLine = true
                            for (line in lines) {
                                if (firstLine) {
                                    firstLine = false
                                } else if (line.isNotEmpty()) {
                                    builder.append(" - ")
                                }
                                builder.append(line).append('\n')
                            }
                            builder.append(" -}")
                        }
                        builder.append('\n')
                    }

                    builder.append("\\meta ")
                    val prec = element.precedence
                    if (prec != Precedence.DEFAULT && prec.priority >= 0) {
                        builder.append('\\').append(prec).append(' ')
                    }
                } else {
                    builder.append("\\module ")
                }
                builder.append(name)

                if (subscope != null) {
                    builder.append(" \\where {\n")
                    scopeToText(subscope, "$prefix  ", builder)
                    builder.append('}')
                }
            }

            if (!first) {
                builder.append('\n')
            }
        }

        private fun fillGroup(group: Group, scope: Scope) {
            for (subgroup in group.subgroups) {
                val name = subgroup.referable.refName
                val meta = scope.resolveName(name)
                if (meta is MetaReferable) {
                    (subgroup.referable as? ModuleAdapter)?.let { module ->
                        module.metaReferable = meta
                        meta.underlyingReferable = module
                    }
                }
                scope.resolveNamespace(name, false)?.let {
                    fillGroup(subgroup, it)
                }
            }
        }
    }
}
