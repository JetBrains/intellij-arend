package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcFieldDefIdentifier
import org.vclang.psi.VcFieldTele
import org.vclang.psi.ancestors
import org.vclang.psi.stubs.VcClassFieldStub
import org.vclang.resolving.VcDefReferenceImpl
import org.vclang.resolving.VcReference

abstract class FieldDefIdentifierAdapter : ReferableAdapter<VcClassFieldStub>, VcFieldDefIdentifier {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassFieldStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = text

    override fun getName(): String = referenceName

    override fun textRepresentation(): String = referenceName

    override fun getReference(): VcReference = VcDefReferenceImpl<VcFieldDefIdentifier>(this)

    override fun computeConcrete(errorReporter: ErrorReporter): Concrete.ClassField? {
        val classDef = ancestors.filterIsInstance<VcDefClass>().firstOrNull()?.computeConcrete(errorReporter) as? Concrete.ClassDefinition ?: return null
        return classDef.fields.firstOrNull { it.data === this }
    }

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getReferable() = this

    override fun isVisible() = false

    override fun getUseScope(): SearchScope {
        if (parent is VcFieldTele) {
            return LocalSearchScope(parent.parent)
        }
        return super.getUseScope()
    }
}