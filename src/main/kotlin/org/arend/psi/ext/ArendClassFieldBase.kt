package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.stubs.ArendNamedStub
import org.arend.term.abs.Abstract
import javax.swing.Icon

abstract class ArendClassFieldBase<StubT> : ReferableBase<StubT>, ArendInternalReferable, Abstract.ClassField
    where StubT : ArendNamedStub, StubT : StubElement<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.FIELD

    override fun getReferable() = this

    override fun getIcon(flags: Int): Icon = ArendIcons.CLASS_FIELD
}