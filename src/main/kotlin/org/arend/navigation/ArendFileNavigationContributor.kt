package org.arend.navigation

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.module.ArendPreludeLibrary
import org.arend.typechecking.TypeCheckingService

class ArendFileNavigationContributor : ChooseByNameContributor {
    override fun getNames(project: Project?, includeNonProjectItems: Boolean): Array<String> =
        arrayOf(ArendPreludeLibrary.PRELUDE_FILE_NAME)

    override fun getItemsByName(
        name: String?,
        pattern: String?,
        project: Project?,
        includeNonProjectItems: Boolean
    ): Array<NavigationItem> {
        project ?: return emptyArray()
        return if (name == ArendPreludeLibrary.PRELUDE_FILE_NAME)
            project.service<TypeCheckingService>().prelude?.let { arrayOf(it) } ?: emptyArray()
        else emptyArray()
    }
}