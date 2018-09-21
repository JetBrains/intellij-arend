package org.arend.psi.stubs.index

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.ArendFileStub

class ArendNamedElementIndex : StringStubIndexExtension<PsiReferable>() {

    override fun getVersion(): Int = ArendFileStub.Type.stubVersion

    override fun getKey(): StubIndexKey<String, PsiReferable> = KEY

    companion object {
        val KEY: StubIndexKey<String, PsiReferable> =
                StubIndexKey.createIndexKey(ArendNamedElementIndex::class.java.canonicalName)
    }
}
