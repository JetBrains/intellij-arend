package org.arend.navigation

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.arend.ext.module.ModulePath
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ArendRawLibrary
import org.arend.typechecking.TypeCheckingService
import org.arend.util.FileUtils

class ArendFileNavigationContributor : ChooseByNameContributor {
    override fun getNames(project: Project?, includeNonProjectItems: Boolean): Array<String> {
        project ?: return emptyArray()
        val result = mutableListOf(ArendPreludeLibrary.PRELUDE_FILE_NAME)
        val service = project.service<TypeCheckingService>()
        forEachArendRawLib(service) { lib ->
            lib.config.additionalModulesSet.forEach { modulePath ->
                result.add(modulePath.toString() + FileUtils.EXTENSION)
            }
        }
        return result.toTypedArray()
    }

    override fun getItemsByName(
            name: String?,
            pattern: String?,
            project: Project?,
            includeNonProjectItems: Boolean
    ): Array<NavigationItem> {
        project ?: return emptyArray()
        name ?: return emptyArray()
        val result = mutableListOf<NavigationItem>()
        if (name == ArendPreludeLibrary.PRELUDE_FILE_NAME) {
            project.service<TypeCheckingService>().prelude?.let { result.add(it) }
        }
        val modulePath = ModulePath.fromString(FileUtil.getNameWithoutExtension(name))
        val service = project.service<TypeCheckingService>()
        forEachArendRawLib(service) { lib ->
            lib.config.findArendFile(modulePath, withAdditional = true, withTests = false)?.let { file ->
                result.add(file)
            }
        }
        return result.toTypedArray()
    }
}

private fun forEachArendRawLib(service: TypeCheckingService, action: (ArendRawLibrary) -> Unit) {
    service.libraryManager.registeredLibraries?.forEach { (it as? ArendRawLibrary)?.let(action) }
}