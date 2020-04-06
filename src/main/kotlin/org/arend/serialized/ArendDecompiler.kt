package org.arend.serialized

import com.google.protobuf.CodedInputStream
import com.intellij.openapi.fileTypes.BinaryFileDecompiler
import com.intellij.openapi.vfs.VirtualFile
import org.arend.ext.module.ModulePath
import org.arend.module.scopeprovider.SimpleModuleScopeProvider
import org.arend.module.serialization.ExpressionProtos
import org.arend.module.serialization.ModuleDeserialization
import org.arend.module.serialization.ModuleProtos
import org.arend.naming.reference.ModuleReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCReferable
import org.arend.naming.scope.ListScope
import org.arend.prelude.Prelude
import org.arend.resolving.WrapperReferableConverter
import org.arend.typechecking.SimpleTypecheckerState
import org.arend.typechecking.order.dependency.DummyDependencyListener
import java.io.IOException
import java.util.zip.GZIPInputStream

class ArendDecompiler : BinaryFileDecompiler {
    val provider = SimpleModuleScopeProvider()

    init {
        val prelude = ArrayList<Referable>(20)
        Prelude.forEach { prelude.add(it.referable) }
        provider.addModule(Prelude.MODULE_PATH, ListScope(prelude))
    }

    override fun decompile(file: VirtualFile): CharSequence {
        val codedInputStream = CodedInputStream.newInstance(GZIPInputStream(file.inputStream))
        codedInputStream.setRecursionLimit(Int.MAX_VALUE)
        val moduleProto = try {
            ModuleProtos.Module.parseFrom(codedInputStream)
        } catch (e: IOException) {
            return "Decompile failed: ${e.message}"
        }
        moduleProto.moduleCallTargetsList.forEach { target ->
            val items = target.callTargetTreeList
                    .map { ModuleReferable(ModulePath(it.name)) }
            provider.addModule(ModulePath(target.nameList), ListScope(items))
        }
        val deserialization = ModuleDeserialization(moduleProto, SimpleTypecheckerState(), WrapperReferableConverter)
        deserialization.readModule(provider, DummyDependencyListener.INSTANCE)
        return buildString {
            provider.registeredEntries.forEach { (path, scope) ->
                scope.elements.forEach { ref -> appendln(ref) }
            }
            provider.clear()
        }
    }
}
