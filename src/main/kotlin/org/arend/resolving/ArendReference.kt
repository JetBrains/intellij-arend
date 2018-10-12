package org.arend.resolving

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.arend.ArendFileType
import org.arend.ArendIcons
import org.arend.module.util.findArendFilesAndDirectories
import org.arend.module.util.libraryConfig
import org.arend.naming.reference.ModuleReferable
import org.arend.naming.reference.RedirectingReferable
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiModuleReferable
import org.arend.psi.ext.PsiReferable
import org.arend.refactoring.ArendNamesValidator
import org.arend.term.abs.Abstract
import org.arend.typechecking.TypeCheckingService

interface ArendReference : PsiReference {
    override fun getElement(): ArendCompositeElement

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

open class ArendReferenceImpl<T : ArendReferenceElement>(element: T): PsiReferenceBase<T>(element, TextRange(0, element.textLength)), ArendReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun bindToElement(element: PsiElement): PsiElement {
        return element
    }

    override fun getVariants(): Array<Any> {
        var notARecord = false
        var notASynonym = false
        var clazz: Class<*>? = null
        val element = element
        val parent = element.parent
        val pparent = (parent as? ArendLongName)?.parent
        if (pparent is ArendDefClass || pparent is ArendClassSynRef) {
            clazz = ArendDefClass::class.java
            notARecord = pparent is ArendClassSynRef // inside a class synonym
            notASynonym = pparent is ArendClassSynRef
        } else {
            val atomFieldsAcc = ((pparent as? ArendLiteral)?.parent as? ArendAtom)?.parent as? ArendAtomFieldsAcc
            val argParent = ((if (atomFieldsAcc == null) (pparent as? ArendLongNameExpr)?.parent else
                if (!atomFieldsAcc.fieldAccList.isEmpty()) null else atomFieldsAcc.parent) as? ArendArgumentAppExpr)?.parent
            if (argParent is ArendDefInstance || argParent is ArendNewArg || (argParent as? ArendNewExpr)?.newKw != null) {
                clazz = ArendDefClass::class.java
                notARecord = argParent is ArendDefInstance
            }
        }

        return element.scope.elements.mapNotNull {
            val ref = (it as? RedirectingReferable)?.originalReferable ?: it
            val origRef: Any? = if (ref is DataLocatedReferable) ref.data?.element else ref
            if (origRef !is ModuleReferable && (clazz != null && !clazz.isInstance(origRef) || notARecord && (origRef as? ArendDefClass)?.recordKw != null || notASynonym && (origRef as? ArendDefClass)?.fatArrow != null)) {
                null
            } else when (origRef) {
                is PsiNamedElement -> {
                    var builder = LookupElementBuilder.createWithIcon(origRef)
                    val parameters = (origRef as? Abstract.ParametersHolder)?.parameters ?: emptyList()
                    if (!parameters.isEmpty()) {
                        val stringBuilder = StringBuilder()
                        for (parameter in parameters) {
                            if (parameter is PsiElement) {
                                stringBuilder.append(' ').append(parameter.text)
                            }
                        }
                        builder = builder.withTailText(stringBuilder.toString(), true)
                    }
                    (origRef as? PsiReferable)?.psiElementType?.let { builder = builder.withTypeText(it.text) }
                    builder
                }
                is ModuleReferable -> {
                    val module = if (origRef is PsiModuleReferable) (origRef.modules.firstOrNull()) else element.module?.libraryConfig?.findArendFilesAndDirectories(origRef.path)?.firstOrNull()
                    module?.let {
                        if (it is ArendFile)
                            LookupElementBuilder.create(it, it.textRepresentation()).withIcon(ArendIcons.MODULE) else
                            LookupElementBuilder.createWithIcon(it)
                    } ?: LookupElementBuilder.create(origRef, origRef.textRepresentation()).withIcon(ArendIcons.DIRECTORY)
                }
                else -> LookupElementBuilder.create(ref, ref.textRepresentation())
            }
        }.toTypedArray()
    }

    override fun resolve(): PsiElement? {
        var ref: Any? = ArendResolveCache.resolveCached( { element ->
            element.scope.resolveName(element.referenceName)
        }, this.element)

        if (ref is RedirectingReferable) ref = ref.originalReferable
        if (ref is DataLocatedReferable) ref = ref.data?.element
        return when (ref) {
            is PsiElement -> ref
            is PsiModuleReferable -> ref.modules.firstOrNull()
            is ModuleReferable -> {
                if (ref.path == Prelude.MODULE_PATH) {
                    TypeCheckingService.getInstance(element.project).prelude
                } else {
                    val list = element.module?.libraryConfig?.findArendFilesAndDirectories(ref.path) ?: return null
                    list.firstOrNull { it is ArendFile } ?: list.firstOrNull()
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

open class ArendPolyReferenceImpl<T : ArendReferenceElement>(element: T): ArendReferenceImpl<T>(element), PsiPolyVariantReference {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        var ref: Any? = element.scope.resolveName(element.referenceName)
        if (ref is RedirectingReferable) ref = ref.originalReferable
        if (ref is DataLocatedReferable) ref = ref.data?.element
        return when (ref) {
            is PsiElement -> arrayOf(PsiElementResolveResult(ref))
            is PsiModuleReferable -> ref.modules.map { PsiElementResolveResult(it) }.toTypedArray()
            is ModuleReferable ->
                if (ref.path == Prelude.MODULE_PATH) {
                    TypeCheckingService.getInstance(element.project).prelude?.let { listOf(it) }
                } else {
                    element.module?.libraryConfig?.findArendFilesAndDirectories(ref.path)
                }?.map { PsiElementResolveResult(it) }?.toTypedArray<ResolveResult>() ?: emptyArray()
            else -> emptyArray()
        }
    }
}
