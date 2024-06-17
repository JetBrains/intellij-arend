package org.arend.module.orderRoot

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.ui.AttachRootButtonDescriptor
import com.intellij.openapi.roots.libraries.ui.DescendentBasedRootFilter
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor
import com.intellij.openapi.roots.libraries.ui.OrderRootTypePresentation
import org.arend.ArendFileTypeInstance
import org.arend.util.ArendLibraryChooserDescriptor
import org.arend.util.FileUtils


object ArendLibraryRootsComponentDescriptor : LibraryRootsComponentDescriptor() {
    override fun getRootDetectors() = listOf(
        ArendConfigRootDetector,
        DescendentBasedRootFilter.createFileTypeBasedFilter(OrderRootType.SOURCES, false, ArendFileTypeInstance, "sources"),
        DescendentBasedRootFilter(OrderRootType.CLASSES, false, "binaries") {
            it.name.endsWith(FileUtils.SERIALIZED_EXTENSION)
        },
        DescendentBasedRootFilter.createFileTypeBasedFilter(OrderRootType.CLASSES, false, JavaClassFileType.INSTANCE, "classes")
    )

    override fun createAttachButtons(): List<AttachRootButtonDescriptor> = emptyList()

    override fun getRootTypePresentation(type: OrderRootType): OrderRootTypePresentation? = null

    override fun getRootTypes() = arrayOf(ArendConfigOrderRootType, OrderRootType.SOURCES, OrderRootType.CLASSES)

    override fun createAttachFilesChooserDescriptor(libraryName: String?) =
        ArendLibraryChooserDescriptor(chooseYamlConfig = true, chooseZipLibrary = false, chooseLibraryDirectory = false, chooseDirectories = true).apply {
            title =
                if (libraryName == null || libraryName.isEmpty()) {
                    ProjectBundle.message("library.attach.files.action")
                } else {
                    ProjectBundle.message("library.attach.files.to.library.action", libraryName)
                }
            description = "Select config files or directories in which library sources are located"
        }
}