package org.arend.serialized

import com.google.protobuf.CodedInputStream
import com.intellij.openapi.fileTypes.BinaryFileDecompiler
import com.intellij.openapi.vfs.VirtualFile
import org.arend.module.serialization.ModuleProtos
import java.io.IOException
import java.util.zip.GZIPInputStream

class ArendDecompiler : BinaryFileDecompiler {
    override fun decompile(file: VirtualFile): CharSequence {
        val codedInputStream = CodedInputStream.newInstance(GZIPInputStream(file.inputStream))
        codedInputStream.setRecursionLimit(Int.MAX_VALUE)
        val moduleProto = try {
            ModuleProtos.Module.parseFrom(codedInputStream)
        } catch (e: IOException) {
            return "Decompile failed: ${e.message}"
        }
        return moduleProto.toString()
    }
}
