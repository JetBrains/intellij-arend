package org.arend.psi

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.arend.module.scopeprovider.ModuleScopeProvider
import org.arend.module.util.availableConfigs
import org.arend.module.util.containsModule
import org.arend.module.util.findArendFile
import org.arend.module.util.libraryConfig
import org.arend.naming.scope.LexicalScope
import org.arend.prelude.Prelude
import org.arend.term.group.Group
import org.arend.typechecking.TypeCheckingService

val PsiElement.ancestors: Sequence<PsiElement>
    get() = generateSequence(this) { it.parent }

val PsiElement.module: Module?
    get() = ModuleUtilCore.findModuleForPsiElement(this)

val PsiElement.moduleScopeProvider: ModuleScopeProvider
    get() {
        val module = module
        return ModuleScopeProvider { modulePath ->
            val file = if (modulePath == Prelude.MODULE_PATH) {
                TypeCheckingService.getInstance(module?.project ?: project).prelude
            } else {
                val libConf = module?.libraryConfig
                if (libConf == null) {
                    TypeCheckingService.getInstance(module?.project ?: project).libraryManager.registeredLibraries.firstOrNull { it.containsModule(modulePath) }?.getModuleGroup(modulePath)
                } else {
                    libConf.availableConfigs.firstOrNull { it.containsModule(modulePath) }?.findArendFile(modulePath)
                }
            }
            file?.let { LexicalScope.opened(it) }
        }
    }

val PsiElement.sourceRoot: VirtualFile?
    get() = containingFile?.virtualFile?.let { ProjectRootManager.getInstance(project).fileIndex.getSourceRootForFile(it) }

val PsiElement.contentRoot: VirtualFile?
    get() = containingFile?.virtualFile?.let { ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(it) ?: it.parent }

inline fun <reified T : PsiElement> PsiElement.parentOfType(
        strict: Boolean = true,
        minStartOffset: Int = -1
): T? = PsiTreeUtil.getParentOfType(this, T::class.java, strict, minStartOffset)

inline fun <reified T : PsiElement> PsiElement.childOfType(
        strict: Boolean = true
): T? = PsiTreeUtil.findChildOfType(this, T::class.java, strict)

fun Group.findGroupByFullName(fullName: List<String>): Group? =
    if (fullName.isEmpty()) this else (subgroups.find { it.referable.textRepresentation() == fullName[0] } ?: dynamicSubgroups.find { it.referable.textRepresentation() == fullName[0] })?.findGroupByFullName(fullName.drop(1))

val PsiElement.parentsWithSelf: Sequence<PsiElement>
    get() = generateSequence(this) { if (it is PsiFile) null else it.parent }

fun TextRange.containsInside(offset: Int): Boolean = offset in (startOffset + 1)..(endOffset - 1)

fun PsiElement.findNextSibling(): PsiElement? = findNextSibling(null)

fun PsiElement.findNextSibling(punctuationType: IElementType?): PsiElement? {
    var sibling: PsiElement? = this.nextSibling
    while (sibling is PsiComment || sibling is PsiWhiteSpace ||
            (punctuationType != null && sibling != null && sibling.node.elementType == punctuationType)) sibling = sibling.nextSibling
    return sibling
}

fun PsiElement.findPrevSibling(): PsiElement? = findPrevSibling(null)

fun PsiElement.findPrevSibling(punctuationType: IElementType?): PsiElement? {
    var sibling: PsiElement? = this.prevSibling
    while (sibling is PsiComment || sibling is PsiWhiteSpace ||
            (punctuationType != null && sibling != null && sibling.node.elementType == punctuationType)) sibling = sibling.prevSibling
    return sibling
}