package org.arend.psi.arc

import com.intellij.openapi.vfs.LocalFileSystem
import org.arend.ArendTestBase
import java.io.File

class ArcTest : ArendTestBase() {
    override var dataPath = "org/arend/arc"

    fun `test decompile arc file`() {
        val file = File("$testDataPath/Test.arc")
        val result = LocalFileSystem.getInstance().findFileByIoFile(file)?.let { ArcFileDecompiler.decompile(it) }
        assertEquals("\\func f \\plevels  \\hlevels  : {?} => {?}", result)
    }
}
