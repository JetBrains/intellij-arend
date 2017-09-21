package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.term.Concrete
import org.vclang.psi.VcClassViewField
import org.vclang.psi.stubs.VcClassViewFieldStub

abstract class ClassViewFieldAdapter : ReferableAdapter<VcClassViewFieldStub>, VcClassViewField {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassViewFieldStub, nodeType: IStubElementType<*, *>): super(stub, nodeType)

    override fun computeConcrete(): Concrete.ClassViewField {
        TODO("not implemented")
    }

/* TODO[abstract]
    private var underlyingFieldName: String? = null
    private var ownView: ClassViewAdapter? = null
    private var underlyingField: Abstract.ClassField? = null

    override fun getIcon(flags: Int): Icon = VcIcons.CLASS_VIEW_FIELD

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            precedence: Abstract.Precedence?,
            underlyingFieldName: String?,
            ownView: ClassViewAdapter?
    ): ClassViewFieldAdapter {
        super.reconstruct(position, name, precedence)
        this.underlyingFieldName = underlyingFieldName
        this.ownView = ownView
        return this
    }

    override fun getUnderlyingFieldName(): String =
            underlyingFieldName ?: throw IllegalStateException()

    override fun getUnderlyingField(): Abstract.ClassField =
            underlyingField ?: throw IllegalStateException()

    fun setUnderlyingField(underlyingField: Abstract.ClassField?) {
        this.underlyingField = underlyingField
    }

    override fun getOwnView(): ClassViewAdapter = ownView ?: throw IllegalStateException()

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitClassViewField(this, params)
    */
}
