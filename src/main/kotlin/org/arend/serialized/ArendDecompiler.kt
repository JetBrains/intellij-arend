package org.arend.serialized

import com.google.protobuf.CodedInputStream
import com.intellij.openapi.fileTypes.BinaryFileDecompiler
import com.intellij.openapi.vfs.VirtualFile
import org.arend.module.scopeprovider.SimpleModuleScopeProvider
import org.arend.module.serialization.ModuleDeserialization
import org.arend.module.serialization.ModuleProtos
import org.arend.typechecking.order.dependency.DummyDependencyListener

class ArendDecompiler : BinaryFileDecompiler {
    val provider = SimpleModuleScopeProvider()

    override fun decompile(file: VirtualFile): CharSequence {
        val codedInputStream = CodedInputStream.newInstance(file.inputStream)
        codedInputStream.setRecursionLimit(Int.MAX_VALUE)
        val moduleProto = ModuleProtos.Module.parseFrom(codedInputStream)
                .takeIf { it.complete } ?: return "Decompile failed"
        val deserialization = ModuleDeserialization(moduleProto, null, null)
        deserialization.readModule(provider, DummyDependencyListener.INSTANCE)
        return buildString {
            provider.registeredEntries.forEach { (path, scope) ->
                scope.elements.forEach { ref -> append(ref) }
            }
            provider.clear()
        }
    }
}
