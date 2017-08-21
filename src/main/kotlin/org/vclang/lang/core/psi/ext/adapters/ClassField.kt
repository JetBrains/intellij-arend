package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.ide.icons.VcIcons
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcClassField
import org.vclang.lang.core.stubs.VcClassFieldStub
import javax.swing.Icon

abstract class ClassFieldAdapter : DefinitionAdapter<VcClassFieldStub>,
                                   VcClassField {
    private var resultType: Surrogate.Expression? = null

    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassFieldStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_FIELD

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            precedence: Abstract.Precedence?,
            resultType: Surrogate.Expression?
    ): ClassFieldAdapter {
        super.reconstruct(position, name, precedence)
        setNotStatic()
        this.resultType = resultType
        return this
    }

    override fun getParentDefinition(): ClassDefinitionAdapter? =
            super.getParentDefinition() as? ClassDefinitionAdapter

    override fun getResultType(): Surrogate.Expression = resultType ?: throw IllegalStateException()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitClassField(this, params)
}
