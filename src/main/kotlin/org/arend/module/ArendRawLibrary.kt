package org.arend.module

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiFileFactory
import org.arend.ArendLanguage
import org.arend.ext.error.ErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.Precedence
import org.arend.library.LibraryHeader
import org.arend.library.LibraryManager
import org.arend.library.SourceLibrary
import org.arend.module.config.ExternalLibraryConfig
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.*
import org.arend.naming.scope.Scope
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiDefReferable
import org.arend.psi.ext.ArendGroup
import org.arend.psi.ext.ArendDefMeta
import org.arend.resolving.ArendReferableConverter
import org.arend.source.BinarySource
import org.arend.source.FileBinarySource
import org.arend.source.GZIPStreamBinarySource
import org.arend.source.PersistableBinarySource
import org.arend.term.group.Group
import org.arend.typechecking.TypeCheckingService
import org.arend.ui.impl.ArendGeneralUI
import org.arend.util.FileUtils
import java.lang.StringBuilder
import java.util.function.Supplier

class ArendRawLibrary(val config: LibraryConfig) : SourceLibrary() {

    override fun isExternal() = config.isExternal

    override fun getName() = config.name

    override fun getVersion() = config.version

    override fun getModuleGroup(modulePath: ModulePath, inTests: Boolean) =
        config.findArendFile(modulePath, inTests)

    override fun getUI() = ArendGeneralUI(config.project)

    override fun loadHeader(errorReporter: ErrorReporter) =
        LibraryHeader(config.findModules(false), config.dependencies, config.version, config.langVersion, config.classLoaderDelegate, config.extensionMainClass)

    fun addGeneratedModule(modulePath: ModulePath, scope: Scope) {
        val builder = StringBuilder()
        scopeToText(scope, "", builder)
        val fileName = modulePath.toString() + FileUtils.EXTENSION
        val file = PsiFileFactory.getInstance(config.project)
                .createFileFromText(fileName, ArendLanguage.INSTANCE, builder.toString())
                as? ArendFile ?: return
        file.virtualFile.isWritable = false
        file.generatedModuleLocation = ModuleLocation(this, ModuleLocation.LocationKind.GENERATED, modulePath)
        fillGroup(file, scope)
        config.addAdditionalModule(modulePath, file)
    }

    override fun loadGeneratedModules() {
        for (entry in additionalModules) {
            addGeneratedModule(entry.key, entry.value)
        }
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

    override fun getBinarySource(modulePath: ModulePath): BinarySource? {
        val root = config.root ?: return null
        val binDir = config.binariesDirList ?: return null
        return GZIPStreamBinarySource(IntellijBinarySource(root, binDir, modulePath))
    }

    override fun getPersistableBinarySource(modulePath: ModulePath?): PersistableBinarySource? {
        // We do not persist binary files with VirtualFiles because it takes forever for some reason
        val root = config.root ?: return null
        val binDir = config.binariesDir ?: return null
        val path = root.fileSystem.getNioPath(root) ?: return null
        return GZIPStreamBinarySource(FileBinarySource(path.resolve(binDir), modulePath))
    }

    override fun containsModule(modulePath: ModulePath) = config.containsModule(modulePath)

    override fun resetGroup(group: Group) {
        super.resetGroup(group)
        (group as? ArendFile)?.apply {
            moduleLocation?.let {
                config.project.service<TypeCheckingService>().getTCRefMaps(Referable.RefKind.EXPR).remove(it)
            }
        }
    }

    override fun resetDefinition(referable: LocatedReferable) {
        if (referable !is PsiDefReferable) {
            return
        }
        runReadAction {
            if (!config.project.isDisposed) {
                referable.dropTypechecked()
                if (referable !is ArendGroup) return@runReadAction
                for (ref in referable.internalReferables) {
                    ref.dropTypechecked()
                }
            }
        }
    }

    override fun getReferableConverter() = ArendReferableConverter

    override fun getDependencyListener() = config.project.service<TypeCheckingService>().dependencyListener

    companion object {
        fun getExternalLibrary(libraryManager: LibraryManager, name: String) =
            libraryManager.getRegisteredLibrary { ((it as? ArendRawLibrary)?.config as? ExternalLibraryConfig)?.name == name } as? ArendRawLibrary

        private fun scopeToText(scope: Scope, prefix: String, builder: StringBuilder) {
            var first = true

            for (element in scope.elements) {
                if (!(element is MetaReferable || element is EmptyLocatedReferable || element is ConcreteLocatedReferable)) {
                    continue
                }

                val name = element.refName
                val subscope = scope.resolveNamespace(name)
                if (subscope == null && element is EmptyLocatedReferable) {
                    continue
                }

                if (first) {
                    first = false
                } else {
                    builder.append("\n\n")
                }

                builder.append(prefix)
                if (element is TCReferable && element.description.isNotEmpty()) {
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

                when (element) {
                    is MetaReferable -> {
                        builder.append("\\meta ")
                        val prec = element.precedence
                        if (prec != Precedence.DEFAULT && prec.priority >= 0) {
                            builder.append('\\').append(prec).append(' ')
                        }
                        builder.append(name)

                        val alias = element.aliasName
                        if (alias != null) {
                            builder.append(" \\alias")
                            val aliasPrec = element.aliasPrecedence
                            if (aliasPrec != Precedence.DEFAULT && aliasPrec.priority >= 0) {
                                builder.append(" \\").append(aliasPrec)
                            }
                            builder.append(' ').append(alias)
                        }
                    }
                    is ConcreteLocatedReferable -> builder.append(element.definition)
                    else -> builder.append("\\module ").append(name)
                }

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
            for (statement in group.statements) {
                val subgroup = statement.group ?: continue
                val name = subgroup.referable.refName
                val meta = scope.resolveName(name)
                if (meta is MetaReferable) {
                    (subgroup as? ArendDefMeta)?.let { module ->
                        module.metaRef = meta
                        meta.underlyingReferable = Supplier { module }
                    }
                }
                scope.resolveNamespace(name)?.let {
                    fillGroup(subgroup, it)
                }
            }
        }
    }
}
