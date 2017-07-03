package org.vclang

import com.intellij.openapi.fileTypes.*

class VclangFileTypeFactory : FileTypeFactory() {
    override fun createFileTypes(fileTypeConsumer: FileTypeConsumer) {
        fileTypeConsumer.consume(VclangFileType, "vc")
    }
}
