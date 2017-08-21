package org.vclang.lang.core.stubs

import com.intellij.psi.stubs.IndexSink
import org.vclang.lang.core.stubs.index.VcGotoClassIndex
import org.vclang.lang.core.stubs.index.VcNamedElementIndex

fun IndexSink.indexClass(stub: VcDefClassStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
    indexGotoClass(stub)
}

fun IndexSink.indexClassField(stub: VcClassFieldStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexClassImplement(stub: VcClassImplementStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexClassView(stub: VcDefClassViewStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexClassViewField(stub: VcClassViewFieldStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexClassViewImplement(stub: VcDefInstanceStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexConstructor(stub: VcConstructorStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexData(stub: VcDefDataStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexFunction(stub: VcDefFunctionStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

private fun IndexSink.indexNamedStub(stub: VcNamedStub) {
    stub.name?.let { occurrence(VcNamedElementIndex.KEY, it) }
}

private fun IndexSink.indexDefinitionStub(stub: VcNamedStub) {
    stub.name?.let { occurrence(VcNamedElementIndex.KEY, it) }
}

private fun IndexSink.indexGotoClass(stub: VcNamedStub) {
    stub.name?.let { occurrence(VcGotoClassIndex.KEY, it) }
}
