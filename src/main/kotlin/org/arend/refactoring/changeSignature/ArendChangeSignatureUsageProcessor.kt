package org.arend.refactoring.changeSignature

import ai.grazie.utils.WeakHashMap
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor
import com.intellij.refactoring.rename.ResolveSnapshotProvider
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.arend.codeInsight.ArendCodeInsightUtils
import org.arend.codeInsight.DefaultParameterDescriptorFactory
import org.arend.codeInsight.ParameterDescriptor
import org.arend.psi.ext.*
import org.arend.refactoring.rename.ArendRenameProcessor
import org.arend.refactoring.rename.ArendRenameRefactoringContext
import org.arend.term.abs.Abstract.ParametersHolder
import kotlin.collections.ArrayList

class ArendChangeSignatureUsageProcessor : ChangeSignatureUsageProcessor {
    private val refactoringDescriptors = WeakHashMap<ChangeInfo, List<ChangeSignatureRefactoringDescriptor>>()

    override fun findUsages(info: ChangeInfo?): Array<ArendUsageInfo> {
        if (info !is ArendChangeInfo) return emptyArray()

        val newParamsInDialog = info.newParameters.toList().map { it as ArendTextualParameter }
        val definition = info.method as ParametersHolder
        val externalParameters = ArendCodeInsightUtils.getExternalParameters(definition as PsiLocatedReferable)
            ?: throw IllegalStateException()
        val ownParameters = DefaultParameterDescriptorFactory.createFromTeles(definition.parameters)

        val newParameters = ArrayList<ParameterDescriptor>()
        newParameters.addAll(DefaultParameterDescriptorFactory.identityTransform(externalParameters))

        for (newParam in newParamsInDialog) {
            val oldIndex = newParam.oldIndex
            newParameters.add(
                DefaultParameterDescriptorFactory.createNewParameter(newParam.isExplicit(), ownParameters.getOrNull(oldIndex), ownParameters.getOrNull(oldIndex)?.getExternalScope(), newParam.name, { newParam.typeText })
            )
        }

        val descriptors = ChangeSignatureRefactoringDescriptor.getRefactoringDescriptors(definition, info.newName, externalParameters + ownParameters, newParameters)
        refactoringDescriptors[info] = descriptors


        //assert valid refactoring descriptors
        for (rd in descriptors)
            for (nP in rd.newParameters)
                if (nP.oldParameter != null)
                    assert (rd.oldParameters.contains(nP.oldParameter))

        val lastFoundUsages = descriptors.map { descriptor -> descriptor.getAffectedDefinition()?.let{
            ReferencesSearch.search(it).map { ArendUsageInfo(it.element, descriptor) }} ?: emptyList()
        }.flatten().sorted()
        return lastFoundUsages.toTypedArray<ArendUsageInfo>()
    }

    override fun findConflicts(info: ChangeInfo?, refUsages: Ref<Array<UsageInfo>>?): MultiMap<PsiElement, String> = MultiMap.empty()

    override fun processUsage(changeInfo: ChangeInfo?, usageInfo: UsageInfo?, beforeMethodChange: Boolean, usages: Array<out UsageInfo>?): Boolean = true

    override fun processPrimaryMethod(changeInfo: ChangeInfo?): Boolean {
        if (changeInfo !is ArendChangeInfo) return false
        val descriptors = refactoringDescriptors[changeInfo] ?: return false

        val project = changeInfo.method.project

        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(changeInfo.method.containingFile)
        document?.let { documentManager.doPostponedOperationsAndUnblockDocument(document) }

        val definition = changeInfo.method as PsiLocatedReferable

        if (changeInfo.isParameterNamesChanged && definition is ParametersHolder)
            renameParameters(project, changeInfo, definition)

        val secondaryRefactoringDescriptors = descriptors.drop(1)
        val secondaryParametersInfos = secondaryRefactoringDescriptors.map { it.toParametersInfo() }
        val changeInfos = ArrayList<ArendChangeInfo>()
        changeInfos.add(changeInfo)
        changeInfos.addAll(secondaryParametersInfos.filterNotNull().map { secondaryParameterInfo ->
            ArendChangeInfo(secondaryParameterInfo, null, secondaryParameterInfo.locatedReferable.name ?: "", secondaryParameterInfo.locatedReferable)
        })

        for (d in descriptors) d.fixEliminator()

        if (changeInfo.isNameChanged) {
            val renameProcessor = ArendRenameProcessor(project, definition, changeInfo.newName, ArendRenameRefactoringContext(definition.refName), null)
            val usages = renameProcessor.findUsages()
            renameProcessor.executeEx(usages)
        }

        for (cI in changeInfos) cI.modifySignature()

        return true
    }

    override fun shouldPreviewUsages(changeInfo: ChangeInfo?, usages: Array<out UsageInfo>?): Boolean = false

    override fun setupDefaultValues(changeInfo: ChangeInfo?, refUsages: Ref<Array<UsageInfo>>?, project: Project?): Boolean = false

    override fun registerConflictResolvers(snapshots: MutableList<in ResolveSnapshotProvider.ResolveSnapshot>?, resolveSnapshotProvider: ResolveSnapshotProvider, usages: Array<out UsageInfo>?, changeInfo: ChangeInfo?) {}
}