package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.ArendExpr
import org.arend.psi.ArendFieldDefIdentifier
import org.arend.psi.ArendFieldTele
import org.arend.psi.ArendPrec
import org.arend.psi.stubs.ArendClassFieldParamStub
import org.arend.resolving.ArendDefReferenceImpl
import org.arend.resolving.ArendReference
import org.arend.term.ClassFieldKind
import org.arend.term.abs.Abstract
import org.arend.typing.ReferableExtractVisitor

abstract class FieldDefIdentifierAdapter : ReferableAdapter<ArendClassFieldParamStub>, ArendFieldDefIdentifier {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendClassFieldParamStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.FIELD

    override val referenceNameElement
        get() = this

    override val referenceName: String
        get() = name

    override fun getName(): String = stub?.name ?: text

    override fun getClassFieldKind() = ClassFieldKind.ANY

    override fun textRepresentation(): String = name

    override fun getReference(): ArendReference = ArendDefReferenceImpl<ArendFieldDefIdentifier>(this)

    override fun getPrec(): ArendPrec? = null

    override fun getReferable() = this

    override fun isVisible() = false

    override fun isExplicitField() = (parent as? ArendFieldTele)?.isExplicit ?: true

    override fun isParameterField() = true

    override fun getTypeClassReference(): ClassReferable? =
        resultType?.let { ReferableExtractVisitor().findClassReferable(it) }

    override fun getTypeOf() = resultType

    override fun getParameters(): List<Abstract.Parameter> = emptyList()

    override fun getResultType(): ArendExpr? = (parent as? ArendFieldTele)?.expr

    override fun getResultTypeLevel(): ArendExpr? = null

    override fun getIcon(flags: Int) = ArendIcons.CLASS_FIELD

    override fun getUseScope() = GlobalSearchScope.projectScope(project)

    override val psiElementType: PsiElement?
        get() = resultType

    override val rangeInElement: TextRange
        get() = TextRange(0, text.length)
}