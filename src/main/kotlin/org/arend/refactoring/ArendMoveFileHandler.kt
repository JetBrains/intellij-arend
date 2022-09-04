package org.arend.refactoring

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.arend.module.ArendModuleType
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.ArendLongName
import org.arend.psi.ext.ArendRefIdentifier
import org.arend.psi.module
import org.arend.util.FileUtils
import org.arend.util.getRelativePath

class ArendMoveFileHandler: MoveFileHandler() {
    private val replaceMap = mutableMapOf<PsiElement, PsiElement>()
    private val usages = mutableMapOf<ArendFile, List<UsageInfo>>()

    override fun updateMovedFile(file: PsiFile?) {

    }

    override fun prepareMovedFile(file: PsiFile, moveDestination: PsiDirectory, oldToNewMap: MutableMap<PsiElement, PsiElement>) {
        if (file !is ArendFile) return
        val fileUsages = usages[file] ?: return
        val destinationPath = file.arendLibrary?.config?.sourcesDirFile?.getRelativePath(moveDestination.virtualFile) ?: return
        destinationPath.add(file.virtualFile?.name?.removeSuffix(FileUtils.EXTENSION) ?: return)
        val newModulePath = destinationPath.joinToString(".")
        for (usage in fileUsages) {
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
    }

    override fun findUsages(psiFile: PsiFile, newParent: PsiDirectory, searchInComments: Boolean, searchInNonJavaFiles: Boolean): ArrayList<UsageInfo> {
        if (psiFile !is ArendFile) return ArrayList()
        val finder = DefaultFindUsagesHandlerFactory().createFindUsagesHandler(psiFile, false)
        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        val options = FindUsagesOptions(psiFile.project)
        options.isUsages = true
        options.isSearchForTextOccurrences = false
        finder?.processElementUsages(psiFile, processor, options)

        val list = ArrayList(processor.results)
        usages[psiFile] = list
        return list
    }

    override fun retargetUsages(usageInfos: MutableList<UsageInfo>?, oldToNewMap: MutableMap<PsiElement, PsiElement>?) {
        for (replaceItem in replaceMap) {
            replaceItem.key.replace(replaceItem.value)
        }
    }

    override fun canProcessElement(element: PsiFile?): Boolean = element is ArendFile && ArendModuleType.has(element.module)
}