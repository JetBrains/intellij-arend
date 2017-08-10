package org.vclang.lang.core.psi

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

val PsiElement.module: Module?
    get() = ModuleUtilCore.findModuleForPsiElement(this)

val PsiElement.contentRoot: VirtualFile?
    get() {
        val module = module ?: return null
        val contentRoots = module.let { ModuleRootManager.getInstance(it).contentRoots }
        return containingDirectories.map { it.virtualFile }.firstOrNull { it in contentRoots }
    }

val PsiElement.sourceRoot: VirtualFile?
    get() {
        val module = module ?: return null
        val sourceRoots = module.let { ModuleRootManager.getInstance(it).sourceRoots }
        return containingDirectories.map { it.virtualFile }.firstOrNull { it in sourceRoots }
    }

val PsiElement.ancestors
    get() = generateSequence(this) { it.parent }

val PsiElement.containingDirectories
    get() = generateSequence(containingFile.containingDirectory) { it.parentDirectory }

inline fun <reified T : PsiElement> PsiElement.parentOfType(
        strict: Boolean = true,
        minStartOffset: Int = -1
): T? = PsiTreeUtil.getParentOfType(this, T::class.java, strict, minStartOffset)

inline fun <reified T : PsiElement> PsiElement.parentOfType(
        strict: Boolean = true,
        stopAt: Class<out PsiElement>
): T? = PsiTreeUtil.getParentOfType(this, T::class.java, strict, stopAt)

inline fun <reified T : PsiElement> PsiElement.contextOfType(
        strict: Boolean = true
): T? = PsiTreeUtil.getContextOfType(this, T::class.java, strict)

inline fun <reified T : PsiElement> PsiElement.childOfType(
        strict: Boolean = true
): T? = PsiTreeUtil.findChildOfType(this, T::class.java, strict)

inline fun <reified T : PsiElement> PsiElement.descendantsOfType(): Collection<T> =
        PsiTreeUtil.findChildrenOfType(this, T::class.java)
