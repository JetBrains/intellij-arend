package com.jetbrains.arend.ide.resolving

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.jetbrains.arend.ide.ArdFileType
import com.jetbrains.arend.ide.ArdIcons
import com.jetbrains.arend.ide.module.util.ardlFile
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.psi.ext.ArdCompositeElement
import com.jetbrains.arend.ide.psi.ext.ArdReferenceElement
import com.jetbrains.arend.ide.psi.ext.PsiModuleReferable
import com.jetbrains.arend.ide.psi.ext.PsiReferable
import com.jetbrains.arend.ide.refactoring.ArdNamesValidator
import com.jetbrains.jetpad.vclang.naming.reference.ModuleReferable
import com.jetbrains.jetpad.vclang.naming.reference.RedirectingReferable
import com.jetbrains.jetpad.vclang.term.abs.Abstract

interface ArdReference : PsiReference {
    override fun getElement(): ArdCompositeElement

    override fun resolve(): PsiElement?
}

open class ArdDefReferenceImpl<T : ArdReferenceElement>(element: T) : PsiReferenceBase<T>(element, TextRange(0, element.textLength)), ArdReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun resolve(): PsiElement = element.parent as? PsiReferable ?: element
}

open class ArdPatternDefReferenceImpl<T : ArdDefIdentifier>(element: T, private val onlyResolve: Boolean) : ArdReferenceImpl<T>(element) {
    override fun resolve(): PsiElement? = super.resolve() ?: if (onlyResolve) null else element
}

open class ArdReferenceImpl<T : ArdReferenceElement>(element: T) : PsiReferenceBase<T>(element, TextRange(0, element.textLength)), ArdReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun getVariants(): Array<Any> {
        var notARecord = false
        var notASynonym = false
        var clazz: Class<*>? = null
        val element = element
        val parent = element.parent
        val pparent = parent as? ArdDefClass ?: (parent as? ArdLongName)?.parent
        if (pparent is ArdDefClass) {
            clazz = ArdDefClass::class.java
            notARecord = parent is ArdDefClass // inside a class synonym
            notASynonym = parent is ArdDefClass
        } else {
            val atomFieldsAcc = ((pparent as? ArdLiteral)?.parent as? ArdAtom)?.parent as? ArdAtomFieldsAcc
            val argParent = ((if (atomFieldsAcc == null) (pparent as? ArdLongNameExpr)?.parent else
                if (!atomFieldsAcc.fieldAccList.isEmpty()) null else atomFieldsAcc.parent) as? ArdArgumentAppExpr)?.parent
            if (argParent is ArdDefInstance || argParent is ArdNewArg || (argParent as? ArdNewExpr)?.newKw != null) {
                clazz = ArdDefClass::class.java
                notARecord = argParent is ArdDefInstance
            }
        }

        return element.scope.elements.mapNotNull {
            val ref = (it as? RedirectingReferable)?.originalReferable ?: it
            val origRef: Any? = if (ref is DataLocatedReferable) ref.data.element else ref
            if (origRef !is ModuleReferable && (clazz != null && !clazz.isInstance(origRef) || notARecord && (origRef as? ArdDefClass)?.recordKw != null || notASynonym && (origRef as? ArdDefClass)?.fatArrow != null)) {
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
                    val module = if (origRef is PsiModuleReferable) (origRef.modules.firstOrNull()) else element.module?.ardlFile?.findArdFilesAndDirectories(origRef.path)?.firstOrNull()
                    module?.let {
                        if (it is ArdFile)
                            LookupElementBuilder.create(it, it.textRepresentation()).withIcon(ArdIcons.MODULE) else
                            LookupElementBuilder.createWithIcon(it)
                    } ?: LookupElementBuilder.create(origRef, origRef.textRepresentation()).withIcon(ArdIcons.DIRECTORY)
                }
                else -> LookupElementBuilder.create(ref, ref.textRepresentation())
            }
        }.toTypedArray()
    }

    override fun resolve(): PsiElement? {
        var ref: Any? = ArdResolveCache.resolveCached({ element ->
            element.scope.resolveName(element.referenceName)
        }, this.element)

        if (ref is RedirectingReferable) ref = ref.originalReferable
        if (ref is DataLocatedReferable) ref = ref.data.element
        return when (ref) {
            is PsiElement -> ref
            is PsiModuleReferable -> ref.modules.firstOrNull()
            is ModuleReferable -> {
                val list = element.module?.ardlFile?.findArdFilesAndDirectories(ref.path) ?: return null
                list.firstOrNull { it is ArdFile } ?: list.firstOrNull()
            }
            else -> null
        }
    }
}

private fun doRename(oldNameIdentifier: PsiElement, rawName: String) {
    val name = rawName.removeSuffix('.' + ArdFileType.defaultExtension)
    if (!ArdNamesValidator().isIdentifier(name, oldNameIdentifier.project)) return
    val factory = ArdPsiFactory(oldNameIdentifier.project)
    val newNameIdentifier = when (oldNameIdentifier) {
        is ArdDefIdentifier -> factory.createDefIdentifier(name)
        is ArdRefIdentifier -> factory.createRefIdentifier(name)
        is ArdInfixArgument -> factory.createInfixName(name)
        is ArdPostfixArgument -> factory.createPostfixName(name)
        else -> error("Unsupported identifier type for `$name`")
    }
    oldNameIdentifier.replace(newNameIdentifier)
}

open class ArdPolyReferenceImpl<T : ArdReferenceElement>(element: T) : ArdReferenceImpl<T>(element), PsiPolyVariantReference {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        var ref: Any? = element.scope.resolveName(element.referenceName)
        if (ref is RedirectingReferable) ref = ref.originalReferable
        if (ref is DataLocatedReferable) ref = ref.data.element
        return when (ref) {
            is PsiElement -> arrayOf(PsiElementResolveResult(ref))
            is PsiModuleReferable -> ref.modules.map { PsiElementResolveResult(it) }.toTypedArray()
            is ModuleReferable -> element.module?.ardlFile?.findArdFilesAndDirectories(ref.path)?.map { PsiElementResolveResult(it) }?.toTypedArray<ResolveResult>()
                    ?: emptyArray()
            else -> emptyArray()
        }
    }
}
