package org.vclang.psi.stubs.index

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.vclang.psi.ext.VcNamedElement
import org.vclang.psi.stubs.VcFileStub

class VcNamedElementIndex : StringStubIndexExtension<VcNamedElement>() {

    override fun getVersion(): Int = VcFileStub.Type.stubVersion

    override fun getKey(): StubIndexKey<String, VcNamedElement> = KEY

    companion object {
        val KEY: StubIndexKey<String, VcNamedElement> =
                StubIndexKey.createIndexKey("org.vclang.lang.VcNamedElementIndex")
    }
}
