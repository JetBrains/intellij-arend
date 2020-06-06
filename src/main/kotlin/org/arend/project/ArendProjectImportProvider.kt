package org.arend.project

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectImportBuilder
import com.intellij.projectImport.ProjectImportProvider
import org.arend.util.FileUtils

class ArendProjectImportProvider : ProjectImportProvider() {
    override fun doGetBuilder(): ProjectImportBuilder<*> =
        ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(ArendProjectImportBuilder::class.java)

    override fun canImportFromFile(file: VirtualFile) =
        file.name == FileUtils.LIBRARY_CONFIG_FILE

    override fun getFileSample() = "<b>Arend</b> project file (arend.yaml)"
}