package org.vclang.psi

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jetpad.vclang.term.Group

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

fun Group.findGroupByFullName(fullName: List<String>): Group? =
    if (fullName.isEmpty()) this else (subgroups.find { it.referable.textRepresentation() == fullName[0] } ?: dynamicSubgroups.find { it.referable.textRepresentation() == fullName[0] })?.findGroupByFullName(fullName.drop(1))
