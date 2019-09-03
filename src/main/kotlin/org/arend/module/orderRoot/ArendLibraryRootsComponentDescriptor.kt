package org.arend.module.orderRoot

import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.ui.AttachRootButtonDescriptor
import com.intellij.openapi.roots.libraries.ui.DescendentBasedRootFilter
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor
import com.intellij.openapi.roots.libraries.ui.OrderRootTypePresentation
import org.arend.util.FileTypeChooserDescriptor
import org.arend.util.FileUtils


object ArendLibraryRootsComponentDescriptor : LibraryRootsComponentDescriptor() {
    override fun getRootDetectors() = listOf(
        ArendConfigRootDetector,
        DescendentBasedRootFilter(OrderRootType.SOURCES, false, "sources") {
            it.name.endsWith(FileUtils.EXTENSION)
        }
    )

    override fun createAttachButtons(): List<AttachRootButtonDescriptor> = emptyList()

    override fun getRootTypePresentation(type: OrderRootType): OrderRootTypePresentation? = null

    override fun getRootTypes() = arrayOf(ArendConfigOrderRootType, OrderRootType.SOURCES)

    override fun createAttachFilesChooserDescriptor(libraryName: String?) =
        FileTypeChooserDescriptor(listOf(".yaml"), true).apply {
            title =
                if (libraryName == null || libraryName.isEmpty()) {
                    ProjectBundle.message("library.attach.files.action")
                } else {
                    ProjectBundle.message("library.attach.files.to.library.action", libraryName)
                }
            description = "Select config files or directories in which library sources are located"
        }
}