package org.vclang.lang.core.stubs.index

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.vclang.lang.core.psi.ext.VcNamedElement
import org.vclang.lang.core.stubs.VcFileStub

class VcNamedElementIndex : StringStubIndexExtension<VcNamedElement>() {

    override fun getVersion(): Int = VcFileStub.Type.stubVersion

    override fun getKey(): StubIndexKey<String, VcNamedElement> = KEY

    companion object {
        val KEY: StubIndexKey<String, VcNamedElement> =
                StubIndexKey.createIndexKey("org.vclang.lang.core.stubs.index.VcNamedElementIndex")
    }
}
