package org.arend.psi.stubs

import com.intellij.psi.stubs.IndexSink
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.psi.stubs.index.ArendFileIndex
import org.arend.psi.stubs.index.ArendGotoClassIndex
import org.arend.psi.stubs.index.ArendNamedElementIndex

fun IndexSink.indexFile(stub: ArendFileStub) {
    indexFileStub(stub)
}

fun IndexSink.indexClass(stub: ArendDefClassStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
    indexGotoClass(stub)
}

fun IndexSink.indexClassField(stub: ArendClassFieldStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexClassFieldParam(stub: ArendClassFieldParamStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexClassImplement(stub: ArendClassImplementStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexCoClauseDef(stub: ArendCoClauseDefStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexClassInstance(stub: ArendDefInstanceStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexConstructor(stub: ArendConstructorStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexData(stub: ArendDefDataStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexFunction(stub: ArendDefFunctionStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexMeta(stub: ArendDefMetaStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

fun IndexSink.indexModule(stub: ArendDefModuleStub) {
    indexNamedStub(stub)
    indexDefinitionStub(stub)
}

private fun IndexSink.indexNamedStub(stub: ArendNamedStub) {
    stub.name?.let { occurrence(ArendNamedElementIndex.KEY, it) }
}

private fun IndexSink.indexDefinitionStub(stub: ArendStub<out PsiLocatedReferable>) {
    stub.name?.let { occurrence(ArendDefinitionIndex.KEY, it) }
    stub.aliasName?.let { occurrence(ArendDefinitionIndex.KEY, it) }
}

private fun IndexSink.indexFileStub(stub: ArendFileStub) {
    stub.name?.let { occurrence(ArendFileIndex.KEY, it) }
}

private fun IndexSink.indexGotoClass(stub: ArendNamedStub) {
    stub.name?.let { occurrence(ArendGotoClassIndex.KEY, it) }
}
