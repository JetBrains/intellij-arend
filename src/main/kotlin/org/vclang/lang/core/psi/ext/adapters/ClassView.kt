package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.ide.icons.VcIcons
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcDefClassView
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider
import org.vclang.lang.core.stubs.VcDefClassViewStub
import javax.swing.Icon

abstract class ClassViewAdapter : DefinitionAdapter<VcDefClassViewStub>,
                                  VcDefClassView {
    private var underlyingClass: Surrogate.ReferenceExpression? = null
    private var classifyingFieldName: String? = null
    private var fields: List<ClassViewFieldAdapter>? = null
    private var classifyingField: Abstract.ClassField? = null

    override val namespace: Namespace
        get() = NamespaceProvider.forDefinition(this)

    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefClassViewStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_VIEW

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
