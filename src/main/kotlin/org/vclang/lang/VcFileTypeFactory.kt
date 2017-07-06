package org.vclang.lang

import com.intellij.openapi.fileTypes.*

class VcFileTypeFactory : FileTypeFactory() {
    override fun createFileTypes(fileTypeConsumer: FileTypeConsumer) {
        fileTypeConsumer.consume(VcFileType, VcFileType.defaultExtension)
    }
}
