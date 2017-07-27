package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcDefClassView
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class ClassViewAdapter(node: ASTNode) : DefinitionAdapter(node),
                                                 VcDefClassView {
    private var underlyingClass: Surrogate.ReferenceExpression? = null
    private var classifyingFieldName: String? = null
    private var fields: List<ClassViewFieldAdapter>? = null
    private var classifyingField: Abstract.ClassField? = null

    override val namespace: Namespace
        get() = NamespaceProvider.forDefinition(this)

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            underlyingClass: Surrogate.ReferenceExpression?,
            classifyingFieldName: String?,
            fields: List<ClassViewFieldAdapter>?
    ): ClassViewAdapter {
        super.reconstruct(position, name, Abstract.Precedence.DEFAULT)
        this.underlyingClass = underlyingClass
        this.fields = fields
        this.classifyingFieldName = classifyingFieldName
        return this
    }

    override fun getUnderlyingClassReference(): Surrogate.ReferenceExpression =
            underlyingClass ?: throw IllegalStateException()

    override fun getClassifyingFieldName(): String =
            classifyingFieldName ?: throw IllegalStateException()

    override fun getClassifyingField(): Abstract.ClassField =
            classifyingField ?: throw IllegalStateException()

    fun setClassifyingField(classifyingField: Abstract.ClassField?) {
        this.classifyingField = classifyingField
    }

    override fun getFields(): List<ClassViewFieldAdapter> =
            fields ?: throw IllegalStateException()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitClassView(this, params)
}
