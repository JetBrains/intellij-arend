package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.MetaReferable
import org.arend.psi.ArendDefMeta
import org.arend.psi.stubs.ArendDefMetaStub


abstract class MetaAdapter : DefinitionAdapter<ArendDefMetaStub>, ArendDefMeta {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefMetaStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    var metaReferable: MetaReferable? = null

    override fun getKind() = GlobalReferable.Kind.OTHER

    override fun getIcon(flags: Int) = ArendIcons.META_DEFINITION
}