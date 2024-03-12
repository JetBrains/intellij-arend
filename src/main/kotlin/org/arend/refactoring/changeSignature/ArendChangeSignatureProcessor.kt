package org.arend.refactoring.changeSignature

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase
import com.intellij.usageView.BaseUsageViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.SortedList
import org.arend.error.CountingErrorReporter
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.highlight.BasePass
import org.arend.naming.reference.Referable
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendArgumentAppExpr
import org.arend.psi.ext.ArendPattern
import org.arend.psi.ext.CoClauseBase
import org.arend.refactoring.NsCmdRefactoringAction
import org.arend.refactoring.changeSignature.entries.CoClauseEntry
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.ErrorService
import org.arend.util.ArendBundle
import org.arend.util.appExprToConcrete
import org.arend.util.getBounds
import org.arend.util.patternToConcrete
import java.util.Collections.singletonList

class ArendChangeSignatureProcessor(project: Project, changeInfo: ArendChangeInfo) :
    ChangeSignatureProcessorBase(project, changeInfo) {
    private val fileChangeMap = LinkedHashMap<PsiFile, SortedList<Pair<TextRange, Pair<String, String?>>>>()
    private val deferredNsCmds = ArrayList<NsCmdRefactoringAction>()

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
        BaseUsageViewDescriptor(changeInfo?.method)

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val rootPsiWithErrors = HashSet<PsiElement>()
        val rootPsiWithArendErrors = LinkedHashSet<PsiElement>()

        fileChangeMap.clear()

        if (changeInfo.isParameterSetOrOrderChanged) {
            val usages = refUsages.get().filterIsInstance<ArendUsageInfo>()
            val changeSignatureName = RefactoringBundle.message("changeSignature.refactoring.name")

            if (!ProgressManager.getInstance().runProcessWithProgressSynchronously({
                    val concreteSet = LinkedHashSet<ConcreteDataItem>()
                    val textReplacements =
                        LinkedHashMap<PsiElement, Pair<String /* Possibly parenthesized */, String? /* Not Parenthesized */>>()
                    val rangeData = java.util.HashMap<Concrete.SourceNode, TextRange>()
                    val rootPsiEntries = usages.filter { it.psiParentRoot != null }.map { it.psiParentRoot }.toCollection(LinkedHashSet())
                    val referableData = usages.map { it.task }.toCollection(HashSet()).toList()

                    runReadAction {
                        val progressIndicator = ProgressManager.getInstance().progressIndicator

                        for (usage in usages) {
                            val errors = (usage.psi.containingFile as? ArendFile)?.let{ myProject.service<ErrorService>().getErrors(it) }
                            if (errors?.any {
                                    val textRange = BasePass.getImprovedTextRange(it.error)
                                    textRange?.contains(usage.psi.textRange) == true } == true) usage.psiParentRoot?.let{
                                rootPsiWithArendErrors.add(it)
                            }
                        }

                        for ((index, rootPsi) in rootPsiEntries.withIndex()) if (rootPsi != null && !rootPsiWithArendErrors.contains(rootPsi)) {
                            progressIndicator.fraction = index.toDouble() / rootPsiEntries.size
                            progressIndicator.checkCanceled()
                            val errorReporter = CountingErrorReporter(GeneralError.Level.ERROR, DummyErrorReporter.INSTANCE)
                            when (rootPsi) {
                                is ArendArgumentAppExpr -> appExprToConcrete(rootPsi, false, errorReporter)?.let {
                                    if (errorReporter.errorsNumber == 0) concreteSet.add(ConcreteDataItem(rootPsi, it))
                                }

                                is ArendPattern -> patternToConcrete(rootPsi, errorReporter)?.let {
                                    if (errorReporter.errorsNumber == 0) concreteSet.add(ConcreteDataItem(rootPsi, it))
                                }

                                is CoClauseBase -> concreteSet.add(ConcreteDataItem(rootPsi, null))
                            }
                            if (errorReporter.errorsNumber > 0)
                                rootPsiWithErrors.add(rootPsi)
                        }

                        for ((index, callEntry) in concreteSet.withIndex()) {
                            progressIndicator.fraction = index.toDouble() / concreteSet.size
                            progressIndicator.checkCanceled()
                            val refactoringContext =
                                ChangeSignatureRefactoringContext(referableData, textReplacements, rangeData, (changeInfo as? ArendChangeInfo)?.deferredNsCmds ?: ArrayList())
                            rangeData.clear()

                            try {
                                when (callEntry.psi) {
                                    is ArendArgumentAppExpr -> {
                                        val expr = callEntry.concreteExpr as Concrete.Expression
                                        getBounds(expr, callEntry.psi.node.getChildren(null).toList(), rangeData)
                                        val printResult = printAppExpr(expr, callEntry.psi, refactoringContext)
                                        Pair(printResult.strippedText ?: printResult.text, null)
                                    }

                                    is ArendPattern -> {
                                        val concretePattern = callEntry.concreteExpr as Concrete.ConstructorPattern
                                        getBounds(concretePattern, callEntry.psi.node.getChildren(null).toList(), rangeData)
                                        val printResult = printPattern(concretePattern, callEntry.psi, refactoringContext)
                                        Pair(printResult.text, printResult.strippedText)
                                    }

                                    is CoClauseBase -> {
                                        val referable = callEntry.psi.longName?.resolve as? Referable
                                        if (referable != null) {
                                            val descriptor = refactoringContext.identifyDescriptor(referable)
                                            Pair(CoClauseEntry(callEntry.psi, refactoringContext, descriptor!!).printUsageEntry().first, null) //TODO: Null safety
                                        } else throw IllegalStateException()
                                    }

                                    else -> null
                                }?.let { result ->
                                    textReplacements[callEntry.psi] = result
                                }
                            } catch (e: IllegalArgumentException) {
                                rootPsiWithErrors.add(callEntry.psi)
                            }

                            deferredNsCmds.clear()
                            deferredNsCmds.addAll(refactoringContext.deferredNsCmds)
                        }

                        for (replacementEntry in textReplacements) {
                            val file = replacementEntry.key.containingFile
                            val comparator = Comparator<Pair<TextRange, Pair<String, String?>>> { o1, o2 ->
                                val i = o1.first.startOffset - o2.first.startOffset
                                if (i > 0) 1 else if (i < 0) -1 else 0
                            }
                            val changesList = fileChangeMap.computeIfAbsent(file) {
                                SortedList<Pair<TextRange, Pair<String, String?>>>(comparator)
                            }
                            changesList.add(Pair(replacementEntry.key.textRange, replacementEntry.value))
                        }

                        for (changeEntry in fileChangeMap) { // Leave only non-overlapping top-level text changes
                            var sampleEntry: Pair<TextRange, Pair<String, String?>>? = null
                            var index = 0
                            val list = changeEntry.value
                            while (index < list.size) {
                                val nextEntry = list[index]
                                if (sampleEntry == null || sampleEntry.first.endOffset <= nextEntry.first.startOffset) {
                                    sampleEntry = nextEntry
                                } else if (sampleEntry.first.contains(nextEntry.first)) {
                                    list.remove(nextEntry)
                                    continue
                                } else if (nextEntry.first.contains(sampleEntry.first)) {
                                    list.remove(sampleEntry)
                                    sampleEntry = nextEntry
                                    continue
                                } else throw IllegalStateException() // Changes should not overlap!
                                index++
                            }
                        }

                        // Assert text changes do not overlap
                        for (entry in fileChangeMap) for (i in 1 until entry.value.size) {
                            val precedingRange = entry.value[i - 1].first
                            val currentRange = entry.value[i].first
                            assert(precedingRange.endOffset <= currentRange.startOffset)
                        }
                    }

                }, changeSignatureName, true, myProject)) return false

            if (rootPsiWithErrors.size > 0) {
                val conflictDescriptions = MultiMap<PsiElement, String>()

                for (psi in rootPsiWithErrors)
                    conflictDescriptions.put(psi, singletonList(ArendBundle.message("arend.changeSignature.parseError")))

                for (psi in rootPsiWithArendErrors)
                    conflictDescriptions.put(psi, singletonList(ArendBundle.message("arend.changeSignature.ambientError")))

                setPrepareSuccessfulSwingThreadCallback { }

                return showConflicts(conflictDescriptions, usages.toTypedArray())
            }
        }

        this.prepareSuccessful()
        return true
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        if (changeInfo.isParameterSetOrOrderChanged) {
            writeFileChangeMap(changeInfo.method.project, fileChangeMap)
            for (nsCmd in deferredNsCmds) nsCmd.execute()
        }

        super.performRefactoring(usages)
    }
}