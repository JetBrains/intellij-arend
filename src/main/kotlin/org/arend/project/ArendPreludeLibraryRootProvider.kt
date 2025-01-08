package org.arend.project

import com.intellij.icons.AllIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import org.arend.server.ArendServerService
import javax.swing.Icon

class ArendPreludeLibraryRootProvider: AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): MutableCollection<SyntheticLibrary> {
        return project.service<ArendServerService>().prelude?.virtualFile
            ?.let { PreludeLibrary(it) }
            ?.let { mutableListOf(it) }
            ?: mutableListOf()
    }

    companion object {
        class PreludeLibrary(private val prelude: VirtualFile) : SyntheticLibrary(), ItemPresentation {
            override fun getSourceRoots(): MutableCollection<VirtualFile> = mutableListOf(prelude)

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as PreludeLibrary
                return prelude == other.prelude
            }

            override fun hashCode(): Int = prelude.hashCode()

            override fun getPresentableText(): String = "arend-prelude"

            override fun getLocationString(): String? = null

            override fun getIcon(unused: Boolean): Icon = AllIcons.Nodes.PpLibFolder
        }
    }
}