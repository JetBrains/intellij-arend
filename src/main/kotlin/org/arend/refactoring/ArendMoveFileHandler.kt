package org.arend.refactoring

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.arend.ArendFileType
import org.arend.module.config.ArendModuleConfigService
import org.arend.psi.*

class ArendMoveFileHandler: MoveFileHandler() {
    private val replaceMap = mutableMapOf<PsiElement, PsiElement>()
    private val usages = mutableMapOf<PsiFile, MutableList<UsageInfo>>()

    override fun updateMovedFile(file: PsiFile?) {

    }

    override fun prepareMovedFile(file: PsiFile?, moveDestination: PsiDirectory?, oldToNewMap: MutableMap<PsiElement, PsiElement>?) {
        if (file == null || moveDestination == null) return

        val fileName = file.virtualFile?.name?.removeSuffix('.' + ArendFileType.defaultExtension) ?: return
        val newDirPath = moveDestination.virtualFile.path
        val srcDir = file.module?.let { module -> ArendModuleConfigService(module).sourcesPath?.let { FileUtil.toSystemIndependentName(it.toString()) } }
        val newRelativePath = if (srcDir == null || !newDirPath.startsWith(srcDir)) return else newDirPath.removePrefix(srcDir)
        val newModulePath = if (newRelativePath.isEmpty()) fileName else
            newRelativePath.removePrefix("/").replace('/', '.') + "." + fileName
        for (usage in (usages[file] ?: mutableListOf())) {
            if (usage.element is ArendRefIdentifier && usage.element != null) {
                val ref = usage.element as ArendRefIdentifier
                val longName = ref.parentOfType<ArendLongName>() ?: continue
                var suffix = ""
                val refInd = longName.children.indexOf(ref)
                for (i in refInd + 1 until longName.children.size) {
                    suffix += "." + (longName.children[i] as ArendRefIdentifier).referenceName
                }
                longName.replace(ArendPsiFactory(file.project).createLongName(newModulePath + suffix))
            }
        }
        return
    }

    override fun findUsages(psiFile: PsiFile?, newParent: PsiDirectory?, searchInComments: Boolean, searchInNonJavaFiles: Boolean): MutableList<UsageInfo> {
        if (psiFile == null || newParent == null) return mutableListOf()

        val finder = DefaultFindUsagesHandlerFactory().createFindUsagesHandler(psiFile, false)
        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        val options = FindUsagesOptions(psiFile.project)
        options.isUsages = true
        options.isSearchForTextOccurrences = false
        finder?.processElementUsages(psiFile, processor, options)

        usages[psiFile] = processor.results.mapTo(mutableListOf()) { x -> x }

        return processor.results.mapTo(mutableListOf()) { x -> x }
    }

    override fun retargetUsages(usageInfos: MutableList<UsageInfo>?, oldToNewMap: MutableMap<PsiElement, PsiElement>?) {
        for (replaceItem in replaceMap) {
            replaceItem.key.replace(replaceItem.value)
        }
    }

    override fun canProcessElement(element: PsiFile?): Boolean {
        return element is ArendFile
    }
}