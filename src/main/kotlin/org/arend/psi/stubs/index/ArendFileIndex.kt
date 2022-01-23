package org.arend.psi.stubs.index

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.arend.psi.ArendFile
import org.arend.psi.stubs.ArendFileStub

class ArendFileIndex : StringStubIndexExtension<ArendFile>() {

    override fun getVersion(): Int = ArendFileStub.Type.stubVersion

    override fun getKey(): StubIndexKey<String, ArendFile> = KEY

    companion object {
        val KEY: StubIndexKey<String, ArendFile> =
                StubIndexKey.createIndexKey(ArendFileIndex::class.java.canonicalName)
    }
}