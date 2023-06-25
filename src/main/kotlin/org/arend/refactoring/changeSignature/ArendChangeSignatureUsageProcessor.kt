package org.arend.refactoring.changeSignature

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
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
import org.arend.codeInsight.ArendParameterInfoHandler
import org.arend.psi.ext.*
import org.arend.psi.findPrevSibling
import org.arend.refactoring.rename.ArendRenameProcessor
import org.arend.refactoring.rename.ArendRenameRefactoringContext
import org.arend.term.abs.Abstract
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

class ArendChangeSignatureUsageProcessor : ChangeSignatureUsageProcessor {
    private var lastFoundUsages: List<ArendUsageInfo> = emptyList()

    override fun findUsages(info: ChangeInfo?): Array<ArendUsageInfo> {
        if (info !is ArendChangeInfo) return emptyArray()
        val refactoringDescriptors = ArrayList<ChangeSignatureRefactoringDescriptor>()
        val newParams = info.newParameters.toList().map { it as ArendParameterInfo }
        val d = info.method as Abstract.ParametersHolder
        val implicitPrefix = ArendParameterInfoHandler.getImplicitPrefixForReferable(d).map { p ->
            p.referableList.map { Parameter(false, it) }
        }.flatten()
        val mainParameters = d.parameters.map { p -> p.referableList.map { Parameter(p.isExplicit, it) } }.flatten()

        val newParametersPrefix = ArrayList<NewParameter>()
        val newParameters = ArrayList<NewParameter>()
        for (p in implicitPrefix) newParametersPrefix.add(NewParameter(false, p))
        var isSetOrOrderPreserved = newParams.size == mainParameters.size // this is different from "info.isParameterSetOrOrderChanged"
        for ((i, newParam) in newParams.withIndex()) {
            val oldIndex = newParam.oldIndex
            if (oldIndex != i) isSetOrOrderPreserved = false
            newParameters.add(NewParameter(newParam.isExplicit(), mainParameters.getOrNull(oldIndex)))
        }

        refactoringDescriptors.addAll(info.changeSignatureProcessor.getRefactoringDescriptors(implicitPrefix, mainParameters, newParametersPrefix, newParameters, isSetOrOrderPreserved))

        //assert valid refactoring descriptors
        for (rd in refactoringDescriptors)
            for (nP in rd.newParameters)
                if (nP.oldParameter != null)
                    assert (rd.oldParameters.contains(nP.oldParameter))

        lastFoundUsages = refactoringDescriptors.map { descriptor -> ReferencesSearch.search(descriptor.affectedDefinition).map { ArendUsageInfo(it.element, descriptor) }}.flatten().sorted()
        return lastFoundUsages.toTypedArray<ArendUsageInfo>()
    }

    override fun findConflicts(info: ChangeInfo?, refUsages: Ref<Array<UsageInfo>>?): MultiMap<PsiElement, String> = MultiMap.empty()

    override fun processUsage(changeInfo: ChangeInfo?, usageInfo: UsageInfo?, beforeMethodChange: Boolean, usages: Array<out UsageInfo>?): Boolean = true

    override fun processPrimaryMethod(changeInfo: ChangeInfo?): Boolean {
        if (changeInfo !is ArendChangeInfo) return false
        val project = changeInfo.method.project
        val roots = lastFoundUsages.filter { it.psiParentRoot != null }.map { it.psiParentRoot!! }.toCollection(LinkedHashSet())
        val descriptors = lastFoundUsages.map { it.task }.toCollection(HashSet()).toList()

        if (changeInfo.isParameterSetOrOrderChanged) {
            if (!processUsages(changeInfo.method.project, roots, descriptors)) return true
        }

        runWriteAction {
            changeInfo.addNamespaceCommands()
            val definition = changeInfo.method as PsiDefReferable

            if (changeInfo.isParameterSetOrOrderChanged)
                changeInfo.changeSignatureProcessor.fixEliminator()

            if (changeInfo.isParameterNamesChanged && definition is Abstract.ParametersHolder)
                renameParameters(project, changeInfo, definition)

            if (changeInfo.isNameChanged) {
                val renameProcessor = ArendRenameProcessor(project, definition, changeInfo.newName, ArendRenameRefactoringContext(definition.refName), null)
                val usages = renameProcessor.findUsages()
                renameProcessor.executeEx(usages)
            }

            if (changeInfo.isParameterNamesChanged || changeInfo.isParameterSetOrOrderChanged || changeInfo.isParameterTypesChanged || changeInfo.isReturnTypeChanged) {
                val signatureEndPsi: PsiElement? = changeInfo.changeSignatureProcessor.getSignatureEnd()
                val signature = changeInfo.changeSignatureProcessor.getSignature()
                performTextModification(definition, signature, definition.startOffset, signatureEndPsi?.findPrevSibling()?.endOffset ?: definition.endOffset)
            }

            else if (changeInfo.isNameChanged)
                (changeInfo.method as ArendDefinition<*>).setName(changeInfo.newName)
        }

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