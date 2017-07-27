package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcDefAbstract

abstract class ClassFieldAdapter(node: ASTNode) : Surrogate.SignatureDefinition(node),
                                                  VcDefAbstract {

    override fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            precedence: Abstract.Precedence?,
            arguments: List<Surrogate.Argument>?,
            resultType: Surrogate.Expression?
    ): ClassFieldAdapter {
        super.reconstruct(position, name, precedence, arguments, resultType)
        setIsStatic(false)
        return this
    }

    override fun getParentDefinition(): ClassDefinitionAdapter =
            super.getParentDefinition() as ClassDefinitionAdapter

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitClassField(this, params)
}
