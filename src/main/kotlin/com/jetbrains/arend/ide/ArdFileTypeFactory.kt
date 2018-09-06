package com.jetbrains.arend.ide

import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory

class ArdFileTypeFactory : FileTypeFactory() {
    override fun createFileTypes(fileTypeConsumer: FileTypeConsumer) {
        fileTypeConsumer.consume(ArdFileType, ArdFileType.defaultExtension)
        fileTypeConsumer.consume(ArdlFileType, ArdlFileType.defaultExtension)
    }
}
