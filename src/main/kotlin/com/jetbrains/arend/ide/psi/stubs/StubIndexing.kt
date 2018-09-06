package com.jetbrains.arend.ide.psi.stubs

import com.intellij.psi.stubs.IndexSink
import com.jetbrains.arend.ide.psi.stubs.index.ArdDefinitionIndex
import com.jetbrains.arend.ide.psi.stubs.index.ArdGotoClassIndex
import com.jetbrains.arend.ide.psi.stubs.index.ArdNamedElementIndex

fun IndexSink.indexClass(stub: ArdDefClassStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
    indexGotoClass(stub)
}

fun IndexSink.indexClassField(stub: ArdClassFieldStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexClassFieldParam(stub: ArdClassFieldParamStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexClassFieldSyn(stub: ArdClassFieldSynStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexClassImplement(stub: ArdClassImplementStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexClassInstance(stub: ArdDefInstanceStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexConstructor(stub: ArdConstructorStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexData(stub: ArdDefDataStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexFunction(stub: ArdDefFunctionStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexModule(stub: ArdDefModuleStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

private fun IndexSink.indexNamedStub(stub: ArdNamedStub) {
    stub.name?.let { occurrence(ArdNamedElementIndex.KEY, it) }
}

private fun IndexSink.indexDefinitionStub(stub: ArdNamedStub) {
    stub.name?.let { occurrence(ArdDefinitionIndex.KEY, it) }
}

private fun IndexSink.indexGotoClass(stub: ArdNamedStub) {
    stub.name?.let { occurrence(ArdGotoClassIndex.KEY, it) }
}
