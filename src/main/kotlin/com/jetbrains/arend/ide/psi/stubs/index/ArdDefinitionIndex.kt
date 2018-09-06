package com.jetbrains.arend.ide.psi.stubs.index

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import com.jetbrains.arend.ide.psi.ext.PsiReferable
import com.jetbrains.arend.ide.psi.stubs.ArdFileStub

class ArdDefinitionIndex : StringStubIndexExtension<PsiReferable>() {

    override fun getVersion(): Int = ArdFileStub.Type.stubVersion

    override fun getKey(): StubIndexKey<String, PsiReferable> = KEY

    companion object {
        val KEY: StubIndexKey<String, PsiReferable> =
                StubIndexKey.createIndexKey(ArdDefinitionIndex::class.java.canonicalName)
    }
}
