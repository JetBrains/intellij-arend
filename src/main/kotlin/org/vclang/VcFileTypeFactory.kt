package org.vclang

import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory

class VcFileTypeFactory : FileTypeFactory() {
    override fun createFileTypes(fileTypeConsumer: FileTypeConsumer) =
            fileTypeConsumer.consume(VcFileType, VcFileType.defaultExtension)
}
