package org.vclang.lang.core.psi

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

val PsiElement.ancestors: Sequence<PsiElement>
    get() = generateSequence(this) { it.parent }

val PsiElement.module: Module?
    get() = ModuleUtilCore.findModuleForPsiElement(this)

val PsiElement.contentRoot: VirtualFile?
    get() {
        val module = module ?: return null
        val contentRoots = ModuleRootManager.getInstance(module).contentRoots
        return containingDirectories.map { it.virtualFile }.firstOrNull { it in contentRoots }
    }

val PsiElement.sourceRoot: VirtualFile?
    get() {
        val module = module ?: return null
        val sourceRoots = ModuleRootManager.getInstance(module).sourceRoots
        return containingDirectories.map { it.virtualFile }.firstOrNull { it in sourceRoots }
    }

val PsiElement.containingDirectories: Sequence<PsiDirectory>
    get() = generateSequence(containingFile.originalFile.containingDirectory) { it.parentDirectory }

inline fun <reified T : PsiElement> PsiElement.parentOfType(
        strict: Boolean = true,
        minStartOffset: Int = -1
): T? = PsiTreeUtil.getParentOfType(this, T::class.java, strict, minStartOffset)

inline fun <reified T : PsiElement> PsiElement.childOfType(
        strict: Boolean = true
): T? = PsiTreeUtil.findChildOfType(this, T::class.java, strict)

val VcStatCmd.isHiding: Boolean
    get() = hidingKw != null

val VcNsCmd.isExportCmd: Boolean
    get() = exportKw != null

val VcNsCmd.isOpenCmd: Boolean
    get() = openKw != null

val VcNewExpr.withNewContext: Boolean
    get() = newKw != null

val VcAtomPattern.isExplicit: Boolean
    get() = lparen != null && pattern != null && rparen != null

val VcAtomPattern.isImplicit: Boolean
    get() = lbrace != null && pattern != null && rbrace != null

val VcAtomPattern.isEmpty: Boolean
    get() = lparen != null && pattern == null && rparen != null

val VcAtomPattern.isAny: Boolean
    get() = underscore != null

val VcAssociativity.isLeftAssoc: Boolean
    get() = leftAssocKw != null

val VcAssociativity.isRightAssoc: Boolean
    get() = rightAssocKw != null

val VcAssociativity.isNonAssoc: Boolean
    get() = nonAssocKw != null

val VcTele.isExplicit: Boolean
    get() = lparen != null && typedExpr != null && rparen != null

val VcTele.isImplicit: Boolean
    get() = lbrace != null && typedExpr != null && rbrace != null

val VcTypedExpr.hasType: Boolean
    get() = colon != null
