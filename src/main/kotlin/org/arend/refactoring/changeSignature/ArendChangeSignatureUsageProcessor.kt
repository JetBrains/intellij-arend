package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor
import com.intellij.refactoring.rename.ResolveSnapshotProvider
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.arend.psi.ext.*
import org.arend.psi.findPrevSibling
import org.arend.refactoring.rename.ArendRenameProcessor
import org.arend.refactoring.rename.ArendRenameRefactoringContext
import org.arend.term.abs.Abstract
import kotlin.collections.ArrayList

class ArendChangeSignatureUsageProcessor : ChangeSignatureUsageProcessor {
    private var lastFoundUsages: List<ArendUsageInfo> = emptyList()

    override fun findUsages(info: ChangeInfo?): Array<ArendUsageInfo> {
        if (info !is ArendChangeInfo) return emptyArray()

        //assert valid refactoring descriptors
        for (rd in info.refactoringDescriptors)
            for (nP in rd.newParameters)
                if (nP.oldParameter != null)
                    assert (rd.oldParameters.contains(nP.oldParameter))

        lastFoundUsages = info.refactoringDescriptors.map { descriptor -> ReferencesSearch.search(descriptor.affectedDefinition).map { ArendUsageInfo(it.element, descriptor) }}.flatten().sorted()
        return lastFoundUsages.toTypedArray<ArendUsageInfo>()
    }

    override fun findConflicts(info: ChangeInfo?, refUsages: Ref<Array<UsageInfo>>?): MultiMap<PsiElement, String> = MultiMap.empty()

    override fun processUsage(changeInfo: ChangeInfo?, usageInfo: UsageInfo?, beforeMethodChange: Boolean, usages: Array<out UsageInfo>?): Boolean = true

    override fun processPrimaryMethod(changeInfo: ChangeInfo?): Boolean {
        if (changeInfo !is ArendChangeInfo) return false
        val project = changeInfo.method.project

        val documentManager = PsiDocumentManager.getInstance(project)
        val document = documentManager.getDocument(changeInfo.method.containingFile)
        document?.let { documentManager.doPostponedOperationsAndUnblockDocument(document) }

        val definition = changeInfo.method as PsiDefReferable

        if (changeInfo.isParameterNamesChanged && definition is Abstract.ParametersHolder)
            renameParameters(project, changeInfo, definition)

        val secondaryRefactoringDescriptors = changeInfo.refactoringDescriptors.drop(1)
        val secondaryParametersInfos = secondaryRefactoringDescriptors.map { it.toParametersInfo() }
        val changeInfos = ArrayList<ArendChangeInfo>()
        changeInfos.add(changeInfo)
        changeInfos.addAll(secondaryParametersInfos.filterNotNull().map { secondaryParameterInfo ->
            ArendChangeInfo(secondaryParameterInfo, null, secondaryParameterInfo.locatedReferable.name ?: "", secondaryParameterInfo.locatedReferable)
        })

        for (d in changeInfo.refactoringDescriptors) d.fixEliminator()

        if (changeInfo.isNameChanged) {
            val renameProcessor = ArendRenameProcessor(project, definition, changeInfo.newName, ArendRenameRefactoringContext(definition.refName), null)
            val usages = renameProcessor.findUsages()
            renameProcessor.executeEx(usages)
        }

        for (cI in changeInfos) if (cI.isParameterNamesChanged || cI.isParameterSetOrOrderChanged || cI.isParameterTypesChanged || cI.isReturnTypeChanged) {
            val signatureEndPsi: PsiElement? = cI.getSignatureEndPositionPsi()
            val signature = cI.getSignature()
            performTextModification(cI.method, signature, cI.method.startOffset, signatureEndPsi?.findPrevSibling()?.endOffset ?: cI.method.endOffset)
        } else if (cI.isNameChanged)
            (cI.method as ArendDefinition<*>).setName(cI.newName)

        return true
    }

    override fun shouldPreviewUsages(changeInfo: ChangeInfo?, usages: Array<out UsageInfo>?): Boolean = false

    override fun setupDefaultValues(changeInfo: ChangeInfo?, refUsages: Ref<Array<UsageInfo>>?, project: Project?): Boolean = false

    override fun registerConflictResolvers(snapshots: MutableList<in ResolveSnapshotProvider.ResolveSnapshot>?, resolveSnapshotProvider: ResolveSnapshotProvider, usages: Array<out UsageInfo>?, changeInfo: ChangeInfo?) {}

    companion object {
        fun renameParameters(project: Project, changeInfo: ArendChangeInfo, parametersHolder: Abstract.ParametersHolder) {
            val defIdentifiers: List<PsiElement> = parametersHolder.parameters.map { tele ->
                when (tele) {
                    is ArendNameTele -> tele.identifierOrUnknownList.mapNotNull { iou -> iou.defIdentifier }
                    is ArendTypeTele -> tele.typedExpr?.identifierOrUnknownList?.mapNotNull { iou -> iou.defIdentifier } ?: emptyList()
                    is ArendFieldTele -> tele.referableList
                    else -> throw IllegalStateException()
                }
            }.flatten()
            val processors = ArrayList<Pair<List<SmartPsiElementPointer<PsiElement>>, ArendRenameProcessor>>()
            for (p in changeInfo.newParameters) {
                val d = if (p.oldIndex != -1) defIdentifiers[p.oldIndex] else null
                if (d != null && p.name != d.text) {
                    val renameProcessor = ArendRenameProcessor(project, d, p.name, ArendRenameRefactoringContext(d.text), null)
                    val usages = renameProcessor.findUsages()
                    processors.add(Pair(usages.mapNotNull{ it.element }.map { SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it) }.toList(), renameProcessor))
                }
            }
            for (p in processors)
                p.second.executeEx(p.first.mapNotNull {
                    it.element as? ArendRefIdentifier
                }.map {
                    MoveRenameUsageInfo(it.reference, p.second.element)
                }.toTypedArray())
        }
    }
}