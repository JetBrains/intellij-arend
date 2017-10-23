package org.vclang.psi

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jetpad.vclang.naming.scope.EmptyModuleScopeProvider
import com.jetbrains.jetpad.vclang.naming.scope.ModuleScopeProvider
import com.jetbrains.jetpad.vclang.term.Group
import org.vclang.module.PsiModuleScopeProvider

val PsiElement.ancestors: Sequence<PsiElement>
    get() = generateSequence(this) { it.parent }

val PsiElement.module: Module?
    get() = ModuleUtilCore.findModuleForPsiElement(this)

val PsiElement.moduleScopeProvider: ModuleScopeProvider
    get() = module?.let { PsiModuleScopeProvider(project, it) } ?: EmptyModuleScopeProvider.INSTANCE

val PsiElement.sourceRoot: VirtualFile?
    get() = containingFile?.virtualFile?.let { ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(it) }

inline fun <reified T : PsiElement> PsiElement.parentOfType(
        strict: Boolean = true,
        minStartOffset: Int = -1
): T? = PsiTreeUtil.getParentOfType(this, T::class.java, strict, minStartOffset)

inline fun <reified T : PsiElement> PsiElement.childOfType(
        strict: Boolean = true
): T? = PsiTreeUtil.findChildOfType(this, T::class.java, strict)

fun Group.findGroupByFullName(fullName: List<String>): Group? =
    if (fullName.isEmpty()) this else (subgroups.find { it.referable.textRepresentation() == fullName[0] } ?: dynamicSubgroups.find { it.referable.textRepresentation() == fullName[0] })?.findGroupByFullName(fullName.drop(1))
