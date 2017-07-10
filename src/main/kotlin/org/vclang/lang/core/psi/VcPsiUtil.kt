package org.vclang.lang.core.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.FileBasedIndex

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.FileTypeIndex
import org.vclang.lang.VcFileType

fun Project.findDefinitions(name: String): List<VcDefinition> =
        findDefinitions().filter { it.referenceName == name }

fun Project.findDefinitions(): List<VcDefinition> {
    val virtualFiles = FileBasedIndex.getInstance().getContainingFiles(
            FileTypeIndex.NAME, VcFileType, GlobalSearchScope.allScope(this))
    return virtualFiles
            .map { PsiManager.getInstance(this).findFile(it) as VcFile? }
            .filterNotNull()
            .flatMap { it.children.asIterable() }
            .map { it.lastChild }
            .filterNotNull()
            .map { PsiTreeUtil.getChildrenOfType(it, VcDefinition::class.java) }
            .filterNotNull()
            .flatMap { it.asIterable() }
}
