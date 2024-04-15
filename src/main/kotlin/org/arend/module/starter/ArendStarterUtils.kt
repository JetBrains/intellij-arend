package org.arend.module.starter

import com.intellij.ide.starters.local.Dependency
import com.intellij.ide.starters.local.DependencyConfig
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Version
import com.intellij.openapi.vfs.VfsUtil
import org.arend.library.LibraryDependency
import org.arend.module.AREND_LIB
import org.arend.util.*
import org.arend.util.FileUtils.LIBRARY_CONFIG_FILE
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@ApiStatus.Internal
object ArendStarterUtils {
    private val PLACEHOLDER_VERSION_PATTERN: Regex = Regex("\\$\\{(.+)}")

    private class IncorrectBomFileException(message: String) : IOException(message)

    internal fun parseDependencyConfig(
        projectTag: Element,
        resourcePath: String,
        interpolateProperties: Boolean = true
    ): DependencyConfig {
        val properties: MutableMap<String, String> = mutableMapOf()
        val dependencies: MutableList<Dependency> = mutableListOf()
        val bomVersion: String

        if (projectTag.name != "project") throw IncorrectBomFileException("Incorrect root tag name ${projectTag.name}")

        val bomVersionText = projectTag.getChild("version")?.text
        if (bomVersionText.isNullOrEmpty()) throw IncorrectBomFileException("Empty BOM version for ${resourcePath}")

        bomVersion = bomVersionText

        val propertiesTag = projectTag.getChild("properties")
        if (propertiesTag != null) {
            for (propertyTag in propertiesTag.children) {
                val propertyName = propertyTag.name
                val propertyValue = propertyTag.text

                if (propertyName == null || propertyValue.isNullOrBlank()) {
                    throw IncorrectBomFileException("Incorrect property '${propertyName}'")
                }

                properties[propertyName] = propertyValue
            }
        }

        val dependenciesTag = projectTag.getChild("dependencyManagement")?.getChild("dependencies")
        if (dependenciesTag != null) {
            for (dependencyTag in dependenciesTag.getChildren("dependency")) {
                val groupId = dependencyTag.getChild("groupId")?.text
                val artifactId = dependencyTag.getChild("artifactId")?.text
                var version = dependencyTag.getChild("version")?.text

                if (groupId.isNullOrEmpty() || artifactId.isNullOrEmpty() || version.isNullOrEmpty()) {
                    throw IncorrectBomFileException("Incorrect dependency '${groupId}:${artifactId}'")
                }

                version = interpolateDependencyVersion(groupId, artifactId, version, properties, interpolateProperties)

                dependencies.add(Dependency(groupId, artifactId, version))
            }
        }

        return DependencyConfig(bomVersion, properties, dependencies)
    }

    internal fun parseDependencyConfigVersion(projectTag: Element, resourcePath: String): Version {
        if (projectTag.name != "project") throw IncorrectBomFileException("Incorrect root tag name ${projectTag.name}")

        val bomVersionText = projectTag.getChild("version")?.text
        if (bomVersionText.isNullOrEmpty()) throw IncorrectBomFileException("Empty BOM version for ${resourcePath}")

        return Version.parseVersion(bomVersionText) ?: error("Failed to parse starter dependency config version")
    }

    internal fun mergeDependencyConfigs(
        dependencyConfig: DependencyConfig,
        dependencyConfigUpdates: DependencyConfig?
    ): DependencyConfig {
        if (dependencyConfigUpdates == null || dependencyConfig.version.toFloat() > dependencyConfigUpdates.version.toFloat()) return dependencyConfig

        val newVersion = dependencyConfigUpdates.version

        val properties =
            (dependencyConfig.properties.keys union dependencyConfigUpdates.properties.keys).associateWith { propertyKey ->
                dependencyConfigUpdates.properties[propertyKey] ?: dependencyConfig.properties[propertyKey]
                ?: error("Failed to find property value for key: $propertyKey")
            }

        val dependencies = dependencyConfig.dependencies.map { dependency ->
            val newDependencyVersion = (dependencyConfigUpdates.dependencies.find { updatedDependency ->
                dependency.group == updatedDependency.group && dependency.artifact == updatedDependency.artifact
            }?.version ?: dependency.version).let {
                interpolateDependencyVersion(dependency.group, dependency.artifact, it, properties, true)
            }

            Dependency(dependency.group, dependency.artifact, newDependencyVersion)
        }

        return DependencyConfig(newVersion, properties, dependencies)
    }

    internal fun isDependencyUpdateFileExpired(file: File): Boolean {
        val lastModifiedMs =
            Files.readAttributes(file.toPath(), BasicFileAttributes::class.java).lastModifiedTime().toMillis()
        val lastModified = Instant.ofEpochMilli(lastModifiedMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
        return lastModified.isBefore(LocalDate.now().atStartOfDay())
    }

    private fun interpolateDependencyVersion(
        groupId: String,
        artifactId: String,
        version: String,
        properties: Map<String, String>,
        interpolateProperties: Boolean = true
    ): String {
        val versionMatch = PLACEHOLDER_VERSION_PATTERN.matchEntire(version)
        if (versionMatch != null) {
            val propertyName = versionMatch.groupValues[1]
            val propertyValue = properties[propertyName]
            if (propertyValue.isNullOrEmpty()) {
                throw IncorrectBomFileException("No such property '${propertyName}' for version of '${groupId}:${artifactId}'")
            }
            return if (interpolateProperties) propertyValue else version
        }
        return version
    }

    internal fun getLibraryDependencies(librariesRoot: String, module: Module?): List<LibraryDependency> {
        val list = mutableListOf<LibraryDependency>()
        val arendLib = LibraryDependency(AREND_LIB)

        VfsUtil.findFile(Paths.get(librariesRoot), true)?.let { libRoot ->
            libRoot.children.mapNotNull { file ->
                if (file.name != LIBRARY_CONFIG_FILE && file.configFile != null) {
                    file.libraryName?.let { LibraryDependency(it) }
                } else null
            }.forEach {
                list.add(it)
            }
        }

        val modules = module?.project?.allModules
            ?: return if (list.contains(arendLib)) list else listOf(arendLib) + list
        for (otherModule in modules) {
            if (otherModule != module) {
                list.add(LibraryDependency(otherModule.name))
            }
        }
        return if (list.contains(arendLib)) list else listOf(arendLib) + list
    }
}
