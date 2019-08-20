package org.arend.resolving

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.arend.ArendFileType
import org.arend.ArendIcons
import org.arend.naming.reference.ModuleReferable
import org.arend.naming.reference.Referable
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiModuleReferable
import org.arend.psi.ext.PsiReferable
import org.arend.refactoring.ArendNamesValidator
import org.arend.term.abs.Abstract
import org.arend.typechecking.TypeCheckingService

interface ArendReference : PsiReference {
    override fun getElement(): ArendReferenceElement

    override fun resolve(): PsiElement?
}

open class ArendDefReferenceImpl<T : ArendReferenceElement>(element: T): PsiReferenceBase<T>(element, TextRange(0, element.textLength)), ArendReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun resolve(): PsiElement = element.parent as? PsiReferable ?: element
}

open class ArendPatternDefReferenceImpl<T : ArendDefIdentifier>(element: T, private val onlyResolve: Boolean): ArendReferenceImpl<T>(element) {
    override fun resolve(): PsiElement? = super.resolve() ?: if (onlyResolve) null else element
}

open class ArendReferenceImpl<T : ArendReferenceElement>(element: T, private val beforeImportDot: Boolean = false): PsiReferenceBase<T>(element, TextRange(0, element.textLength)), ArendReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun bindToElement(element: PsiElement): PsiElement {
        return element
    }

    override fun getVariants(): Array<Any> {
        var notARecord = false
        var clazz: Class<*>? = null
        val element = element
        val parent = element.parent
        val pparent = (parent as? ArendLongName)?.parent
        if (pparent is ArendDefClass) {
            clazz = ArendDefClass::class.java
        } else {
            val atomFieldsAcc = ((pparent as? ArendLiteral)?.parent as? ArendAtom)?.parent as? ArendAtomFieldsAcc
            val argParent = when {
                atomFieldsAcc == null -> (pparent as? ArendLongNameExpr)?.parent
                atomFieldsAcc.fieldAccList.isNotEmpty() -> null
                else -> atomFieldsAcc.parent
            }
            if ((((argParent as? ArendArgumentAppExpr)?.parent as? ArendNewExpr)?.parent as? ArendReturnExpr)?.parent is ArendDefInstance) {
                clazz = ArendDefClass::class.java
                notARecord = true
            }
        }

        return element.scope.elements.mapNotNull { origElement ->
            val ref = origElement.underlyingReferable
            if (ref !is ModuleReferable && (clazz != null && !clazz.isInstance(ref) || notARecord && (ref as? ArendDefClass)?.recordKw != null)) {
                null
            } else when (ref) {
                is PsiNamedElement -> {
                    var builder = LookupElementBuilder.create(ref, origElement.textRepresentation()).withIcon(ref.getIcon(0))
                    val parameters = (ref as? Abstract.ParametersHolder)?.parameters ?: emptyList()
                    if (parameters.isNotEmpty()) {
                        val stringBuilder = StringBuilder()
                        for (parameter in parameters) {
                            if (parameter is PsiElement) {
                                stringBuilder.append(' ').append(parameter.oneLineText)
                            }
                        }
                        builder = builder.withTailText(stringBuilder.toString(), true)
                    }
                    (ref as? PsiReferable)?.psiElementType?.let { builder = builder.withTypeText(it.oneLineText) }
                    builder
                }
                is ModuleReferable -> {
                    val module = if (ref is PsiModuleReferable) {
                        ref.modules.firstOrNull()
                    } else {
                        element.libraryConfig?.forAvailableConfigs { it.findArendFileOrDirectory(ref.path) }
                    }
                    module?.let {
                        if (it is ArendFile)
                            LookupElementBuilder.create(it, it.textRepresentation()).withIcon(ArendIcons.AREND_FILE) else
                            LookupElementBuilder.createWithIcon(it)
                    } ?: LookupElementBuilder.create(ref, origElement.textRepresentation()).withIcon(ArendIcons.DIRECTORY)
                }
                else -> LookupElementBuilder.create(ref, origElement.textRepresentation())
            }
        }.toTypedArray()
    }

    override fun resolve(): PsiElement? {
        val cache = ServiceManager.getService(element.project, ArendResolveCache::class.java)
        val resolver =  { element : ArendReferenceElement ->
            if (beforeImportDot) {
                val refName = element.referenceName
                var result: Referable? = null
                for (ref in element.scope.elements) {
                    if (ref.textRepresentation() == refName) {
                        result = ref
                        if (ref !is PsiModuleReferable || ref.modules.firstOrNull() is PsiDirectory) {
                            break
                        }
                    }
                }
                result
            } else {
                element.scope.resolveName(element.referenceName)
            }
        }

        return when (val ref = (if (cache != null) cache.resolveCached(resolver, this.element) else resolver.invoke(this.element))?.underlyingReferable) {
            is PsiElement -> ref
            is PsiModuleReferable -> ref.modules.firstOrNull()
            is ModuleReferable -> {
                if (ref.path == Prelude.MODULE_PATH) {
                    TypeCheckingService.getInstance(element.project).prelude
                } else {
                    element.libraryConfig?.forAvailableConfigs { it.findArendFileOrDirectory(ref.path) }
                }
            }
            else -> null
        }
    }
}

private fun doRename(oldNameIdentifier: PsiElement, rawName: String) {
    val name = rawName.removeSuffix('.' + ArendFileType.defaultExtension)
    if (!ArendNamesValidator().isIdentifier(name, oldNameIdentifier.project)) return
    val factory = ArendPsiFactory(oldNameIdentifier.project)
    val newNameIdentifier = when (oldNameIdentifier) {
        is ArendDefIdentifier -> factory.createDefIdentifier(name)
        is ArendRefIdentifier -> factory.createRefIdentifier(name)
        is ArendInfixArgument -> factory.createInfixName(name)
        is ArendPostfixArgument -> factory.createPostfixName(name)
        else -> error("Unsupported identifier type for `$name`")
    }
    oldNameIdentifier.replace(newNameIdentifier)
}
