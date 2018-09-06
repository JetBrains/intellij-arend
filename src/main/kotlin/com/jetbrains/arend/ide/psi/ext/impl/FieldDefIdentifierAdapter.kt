package com.jetbrains.arend.ide.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.arend.ide.ArdIcons
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.psi.stubs.ArdClassFieldParamStub
import com.jetbrains.arend.ide.resolving.ArdDefReferenceImpl
import com.jetbrains.arend.ide.resolving.ArdReference
import com.jetbrains.arend.ide.typing.ExpectedTypeVisitor
import com.jetbrains.arend.ide.typing.ReferableExtractVisitor
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract

abstract class FieldDefIdentifierAdapter : ReferableAdapter<ArdClassFieldParamStub>, ArdFieldDefIdentifier {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArdClassFieldParamStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.FIELD

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = name

    override fun getName(): String = stub?.name ?: text

    override fun textRepresentation(): String = name

    override fun getReference(): ArdReference = ArdDefReferenceImpl<ArdFieldDefIdentifier>(this)

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getReferable() = this

    override fun isVisible() = false

    override fun isExplicitField() = (parent as? ArdFieldTele)?.isExplicit ?: true

    override fun getTypeClassReference(): ClassReferable? =
            resultType?.let { ReferableExtractVisitor().findClassReferable(it) }

    override fun getParameterType(params: List<Boolean>) =
            when {
                params[0] -> ExpectedTypeVisitor.getParameterType(resultType, params, name)
                params.size == 1 -> ancestors.filterIsInstance<ArdDefClass>().firstOrNull()?.let { ExpectedTypeVisitor.ReferenceImpl(it) }
                else -> ExpectedTypeVisitor.getParameterType(resultType, params.drop(1), name)
            }

    override fun getTypeOf() = resultType

    override fun getParameters(): List<Abstract.Parameter> = emptyList()

    override fun getResultType(): ArdExpr? = (parent as? ArdFieldTele)?.expr

    override fun getIcon(flags: Int) = ArdIcons.CLASS_FIELD

    override val psiElementType: PsiElement?
        get() = (parent as? ArdFieldTele)?.expr
}