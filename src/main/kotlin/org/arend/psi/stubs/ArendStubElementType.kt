package org.arend.psi.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.tree.IStubFileElementType
import org.arend.ArendLanguage
import org.arend.ext.reference.Precedence
import org.arend.ext.reference.Precedence.Associativity.*
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.ext.PsiLocatedReferable

abstract class ArendStubElementType<StubT : ArendStub<*>, PsiT : PsiLocatedReferable>(debugName: String)
    : IStubElementType<StubT, PsiT>(debugName, ArendLanguage.INSTANCE) {

    final override fun getExternalId(): String = "arend.${super.toString()}"

    abstract fun createStub(parentStub: StubElement<*>?, name: String?, prec: Precedence?, aliasName: String?): StubT

    override fun createStub(psi: PsiT, parentStub: StubElement<*>?) = createStub(parentStub, psi.name, psi.precedence, (psi as? GlobalReferable)?.aliasName)

    override fun serialize(stub: StubT, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
        dataStream.writeName(stub.aliasName)
        serializePrecedence(stub.precedence, dataStream)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): StubT {
        val name = dataStream.readNameString()
        val aliasName = dataStream.readNameString()
        val prec = deserializePrecedence(dataStream)
        return createStub(parentStub, name, prec, aliasName)
    }

    private fun createStubIfParentIsStub(node: ASTNode): Boolean {
        val parent = node.treeParent
        val parentType = parent.elementType
        return (parentType is IStubElementType<*, *> && parentType.shouldCreateStub(parent)) ||
                parentType is IStubFileElementType<*>
    }

    private fun serializePrecedence(prec: Precedence?, dataStream: StubOutputStream) {
        if (prec == null) {
            dataStream.writeVarInt(0)
        } else {
            val priority = when {
                prec.priority < 0 -> 0
                prec.priority > Precedence.MAX_PRIORITY -> Precedence.MAX_PRIORITY
                else -> prec.priority
            }
            val assoc = when (prec.associativity) {
                LEFT_ASSOC -> 0
                RIGHT_ASSOC -> 1
                NON_ASSOC -> 2
            }
            dataStream.writeVarInt(1 + priority + 256 * (assoc + 4 * if (prec.isInfix) 1 else 0))
        }
    }

    private fun deserializePrecedence(dataStream: StubInputStream): Precedence? {
        val code = dataStream.readVarInt() - 1
        if (code < 0) {
            return null
        }

        val priority = (code % 256).toByte()
        if (priority < 0 || priority > Precedence.MAX_PRIORITY) {
            return null
        }

        val code2 = code.shr(8)
        val assoc = when (code2 % 4) {
            0 -> LEFT_ASSOC
            1 -> RIGHT_ASSOC
            2 -> NON_ASSOC
            else -> return null
        }

        val isInfix = when (code2.shr(2)) {
            0 -> false
            1 -> true
            else -> return null
        }

        return Precedence(assoc, priority, isInfix)
    }
}
