package org.vclang.psi.stubs.index

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.stubs.VcFileStub

class VcNamedElementIndex : StringStubIndexExtension<PsiReferable>() {

    override fun getVersion(): Int = VcFileStub.Type.stubVersion

    override fun getKey(): StubIndexKey<String, PsiReferable> = KEY

    companion object {
        val KEY: StubIndexKey<String, PsiReferable> =
                StubIndexKey.createIndexKey(VcNamedElementIndex::class.java.canonicalName)
    }
}
