package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.*
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.elementType
import org.arend.ext.module.ModulePath
import org.arend.naming.reference.ModuleReferable
import org.arend.navigation.getPresentation
import org.arend.psi.*
import org.arend.psi.doc.ArendDocComment
import org.arend.psi.stubs.ArendNamedStub
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractReferable

interface PsiReferable : ArendCompositeElement, PsiNameIdentifierOwner, NavigatablePsiElement, Abstract.AbstractReferable, AbstractReferable {
    val psiElementType: PsiElement?
        get() = null

    val documentation: ArendDocComment?
        get() {
            val stat = parent
            val type = stat.elementType
            if (!(type == ArendElementTypes.CLASS_STAT || type == ArendElementTypes.STAT)) {
                return null
            }
            var sibling = stat.prevSibling
            while (sibling is PsiWhiteSpace) {
                sibling = sibling.prevSibling
            }
            return sibling as? ArendDocComment
        }
}

val Abstract.ParametersHolder.parametersText: String?
    get() {
        val parameters = parameters
        if (parameters.isEmpty()) {
            return null
        }

        val stringBuilder = StringBuilder()
        for (parameter in parameters) {
            if (parameter is PsiElement) {
                stringBuilder.append(' ').append(parameter.oneLineText)
            }
        }
        return stringBuilder.toString()
    }

open class PsiModuleReferable(val modules: List<PsiFileSystemItem>, modulePath: ModulePath) : ModuleReferable(modulePath)

abstract class PsiReferableImpl(node: ASTNode) : ArendCompositeElementImpl(node), PsiReferable {

    override fun getNameIdentifier(): PsiElement? = descendantOfType<ArendDefIdentifier>()

    override fun getName(): String? = nameIdentifier?.text

    override fun getRefName(): String = name ?: "_"

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(ArendPsiFactory(project).createDefIdentifier(name))
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}

abstract class PsiStubbedReferableImpl<StubT> : ArendStubbedElementImpl<StubT>, PsiReferable, Abstract.AbstractLocatedReferable
where StubT : ArendNamedStub, StubT : StubElement<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): ArendCompositeElement? = descendantOfType<ArendDefIdentifier>()

    override fun getName(): String? = stub?.name ?: if (isValid) descendantOfType<ArendDefIdentifier>()?.referenceName else null

    override fun getRefName(): String = name ?: "_"

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(ArendPsiFactory(project).createDefIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}
