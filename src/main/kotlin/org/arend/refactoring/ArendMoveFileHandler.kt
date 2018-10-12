package org.arend.refactoring

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.*
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.arend.ArendFileType
import org.arend.module.util.sourcesDir
import org.arend.psi.*

class ArendMoveFileHandler: MoveFileHandler() {
    private var file: SmartPsiElementPointer<PsiFile>? = null
    private var newDirectory: SmartPsiElementPointer<PsiDirectory>? = null

    override fun updateMovedFile(file: PsiFile?) {

    }

    override fun prepareMovedFile(file: PsiFile?, moveDestination: PsiDirectory?, oldToNewMap: MutableMap<PsiElement, PsiElement>?) {
        if (file == null || moveDestination == null) return

        this.file = SmartPointerManager.getInstance(file.project).createSmartPsiElementPointer(file)
        this.newDirectory = SmartPointerManager.getInstance(file.project).createSmartPsiElementPointer(moveDestination)
    }

    override fun findUsages(psiFile: PsiFile?, newParent: PsiDirectory?, searchInComments: Boolean, searchInNonJavaFiles: Boolean): MutableList<UsageInfo> {
        if (psiFile == null || newParent == null) return mutableListOf()

        val finder = DefaultFindUsagesHandlerFactory().createFindUsagesHandler(psiFile, false)
        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        val options = FindUsagesOptions(psiFile.project)
        options.isUsages = true
        options.isSearchForTextOccurrences = false
        finder?.processElementUsages(psiFile, processor, options)

        return processor.results.mapTo(mutableListOf()) { x ->
            (x.element?.parentOfType<ArendLongName>()?.let{ UsageInfo(it, it.textOffset, it.textOffset + it.textLength) } ?: x) as UsageInfo }
    }

    override fun retargetUsages(usageInfos: MutableList<UsageInfo>?, oldToNewMap: MutableMap<PsiElement, PsiElement>?) {
        if (usageInfos == null || oldToNewMap == null || file == null || newDirectory == null) return

        val fileName = file?.element?.virtualFile?.name?.removeSuffix('.' + ArendFileType.defaultExtension) ?: return
        val newDirPath = newDirectory?.element?.virtualFile?.path ?: return
        val srcDir = file?.element?.module?.sourcesDir?.let { FileUtil.toSystemIndependentName(it) }
        val newRelativePath = if (srcDir == null || !newDirPath.startsWith(srcDir)) return else newDirPath.removePrefix(srcDir)
        val newModulePath = if (newRelativePath.isEmpty()) fileName else
            newRelativePath.removePrefix("/").replace('/', '.') + "." + fileName
        for (usage in usageInfos) {
            if (usage.element != null) {
                usage.element!!.replace(ArendPsiFactory(file!!.project).createLongName(newModulePath))
            }
        }
    }

    override fun canProcessElement(element: PsiFile?): Boolean {
        return element is ArendFile
    }
}