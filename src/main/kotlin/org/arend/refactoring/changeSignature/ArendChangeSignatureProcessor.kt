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
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.psi.ext.*
import org.arend.refactoring.NsCmdRefactoringAction
import org.arend.refactoring.changeSignature.entries.CoClauseEntry
import org.arend.refactoring.changeSignature.entries.NoArgumentsEntry
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.ErrorService
import org.arend.util.ArendBundle
import org.arend.util.appExprToConcrete
import org.arend.util.getBounds
import org.arend.util.patternToConcrete
import java.util.Collections.singletonList

class ArendChangeSignatureProcessor(project: Project,
                                    changeInfo: ArendChangeInfo,
                                    private val needsPreview: Boolean,
                                    private val changeExplicitnessMode: Boolean = false) :
    ChangeSignatureProcessorBase(project, changeInfo) {
    private val fileChangeMap = LinkedHashMap<PsiFile, SortedList<Pair<TextRange, String>>>()
    private val deferredNsCmds = ArrayList<NsCmdRefactoringAction>()

    override fun isPreviewUsages(usages: Array<out UsageInfo>): Boolean = needsPreview

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
        BaseUsageViewDescriptor(changeInfo?.method)

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val rootPsiWithErrors = HashSet<PsiElement>()
        val rootPsiWithArendErrors = LinkedHashSet<PsiElement>()

        fileChangeMap.clear()

        if (changeInfo.isParameterSetOrOrderChanged) {
            val usages = refUsages.get().filterIsInstance<ArendUsageInfo>()
            val changeSignatureName = RefactoringBundle.message("changeSignature.refactoring.name")
            val changeInfoNsCmds = (changeInfo as? ArendChangeInfo)?.deferredNsCmds ?: ArrayList()

            val usagesPreprocessor = getUsagesPreprocessor(usages, myProject, fileChangeMap, rootPsiWithArendErrors, rootPsiWithErrors, changeInfoNsCmds, deferredNsCmds)
            if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(usagesPreprocessor, changeSignatureName, true, myProject)) return false

            if (rootPsiWithErrors.size > 0 || rootPsiWithArendErrors.size > 0) {
                val conflictDescriptions = MultiMap<PsiElement, String>()

                for (psi in rootPsiWithErrors)
                    conflictDescriptions.put(psi, singletonList(ArendBundle.message("arend.changeSignature.parseError")))

                for (psi in rootPsiWithArendErrors)
                    conflictDescriptions.put(psi, singletonList(ArendBundle.message("arend.changeSignature.ambientError")))

                if (changeExplicitnessMode) setPrepareSuccessfulSwingThreadCallback { }

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

    companion object {
        fun getUsagesPreprocessor(usages: List<ArendUsageInfo>,
                                  project: Project,
                                  fileChangeMap: MutableMap<PsiFile, SortedList<Pair<TextRange, String>>>,
                                  rootPsiWithArendErrors: MutableSet<PsiElement>,
                                  rootPsiWithErrors: MutableSet<PsiElement>,
                                  changeInfoNsCmds: List<NsCmdRefactoringAction>,
                                  deferredNsCmds: MutableList<NsCmdRefactoringAction>): Runnable =
            Runnable {
            val concreteSet = LinkedHashSet<ConcreteDataItem>()
            val textReplacements =
                LinkedHashMap<PsiElement, String>()
            val rangeData = java.util.HashMap<Concrete.SourceNode, TextRange>()
            val rootPsiEntries = usages.filter { it.psiParentRoot != null }.map { it.psiParentRoot }
                .toCollection(LinkedHashSet())
            val referableData = usages.map { it.task }.toCollection(HashSet()).toList()

            for (action in changeInfoNsCmds)
                if (!deferredNsCmds.contains(action))
                    deferredNsCmds.add(action)

            runReadAction {
                val progressIndicator = ProgressManager.getInstance().progressIndicator

                for (usage in usages) {
                    val errors = (usage.psi.containingFile as? ArendFile)?.let {
                        project.service<ErrorService>().getErrors(it)
                    }
                    if (errors?.any {
                            val textRange = BasePass.getImprovedTextRange(it.error)
                            textRange?.contains(usage.psi.textRange) == true
                        } == true) usage.psiParentRoot?.let {
                        rootPsiWithArendErrors.add(it)
                    }
                }

                for ((index, rootPsi) in rootPsiEntries.withIndex())
                    if (rootPsi != null && !rootPsiWithArendErrors.contains(rootPsi)) {
                        progressIndicator.fraction = index.toDouble() / rootPsiEntries.size
                        progressIndicator.checkCanceled()
                        val errorReporter = CountingErrorReporter(GeneralError.Level.ERROR, DummyErrorReporter.INSTANCE)
                        when (rootPsi) {
                            is ArendArgumentAppExpr ->
                                appExprToConcrete(rootPsi, false, errorReporter)?.let {
                                if (errorReporter.errorsNumber == 0) concreteSet.add(ConcreteDataItem(rootPsi, it))
                            }

                            is ArendAtomFieldsAcc -> appExprToConcrete(rootPsi, false, errorReporter)?.let {
                                if (errorReporter.errorsNumber == 0) concreteSet.add(ConcreteDataItem(rootPsi, it))
                            }

                            is ArendTypeTele -> rootPsi.type?.let { appExprToConcrete(it) }?.let { concreteSet.add(ConcreteDataItem(rootPsi, it)) }

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
                    val refactoringContext = ChangeSignatureRefactoringContext(referableData, textReplacements, rangeData, deferredNsCmds)
                    rangeData.clear()

                    try {
                        when (callEntry.psi) {
                            is ArendArgumentAppExpr -> {
                                val expr = callEntry.concreteExpr as Concrete.Expression
                                getBounds(expr, callEntry.psi.node.getChildren(null).toList(), rangeData)
                                val printResult = printAppExpr(expr, callEntry.psi, refactoringContext)
                                printResult.text
                            }

                            is ArendTypeTele -> {
                                val expr = callEntry.concreteExpr as Concrete.Expression
                                if (expr is Concrete.ReferenceExpression) {
                                    val d = refactoringContext.identifyDescriptor(expr.referent)
                                    if (d != null) {
                                        val printResult = NoArgumentsEntry(expr, refactoringContext, d).printUsageEntry(expr.referent as? GlobalReferable)
                                        if (!callEntry.psi.isExplicit) "{${printResult.text}}" else if (printResult.isAtomic) printResult.text else "(${printResult.text})"
                                    } else null
                                } else null
                            }

                            is ArendAtomFieldsAcc -> {
                                val expr = callEntry.concreteExpr
                                val argumentAppExpr = callEntry.psi.ancestor<ArendArgumentAppExpr>()
                                if (expr is Concrete.ProjExpression) {
                                    val atom = expr.expression
                                    if (argumentAppExpr != null) {
                                        getBounds(atom, argumentAppExpr.node.getChildren(null).toList(), rangeData)
                                        val printResult = printAppExpr(atom, argumentAppExpr, refactoringContext)
                                        (if (printResult.isAtomic) printResult.text else "(${printResult.text})") + ".${expr.field + 1}"
                                    } else null
                                } else null
                            }

                            is ArendPattern -> {
                                val concretePattern = callEntry.concreteExpr as Concrete.ConstructorPattern
                                getBounds(concretePattern, callEntry.psi.node.getChildren(null).toList(), rangeData)
                                val printResult = printPattern(concretePattern, callEntry.psi, refactoringContext)
                                printResult.text
                            }

                            is CoClauseBase -> {
                                val referable = callEntry.psi.longName?.resolve as? Referable
                                if (referable != null) {
                                    val descriptor = refactoringContext.identifyDescriptor(referable)
                                    CoClauseEntry(callEntry.psi, refactoringContext, descriptor!!).printUsageEntry().text //TODO: Null safety
                                } else throw IllegalStateException()
                            }

                            else -> null
                        }?.let { result ->
                            textReplacements[callEntry.psi] = result
                        }
                    } catch (e: IllegalArgumentException) {
                        rootPsiWithErrors.add(callEntry.psi)
                    }

                    for (action in refactoringContext.deferredNsCmds) if (!deferredNsCmds.contains(action)) deferredNsCmds.add(action)
                }

                for ((psi, text) in textReplacements) {
                    val file = psi.containingFile
                    val comparator = Comparator<Pair<TextRange, String>> { o1, o2 ->
                        val i = o1.first.startOffset - o2.first.startOffset
                        if (i > 0) 1 else if (i < 0) -1 else 0
                    }
                    val changesList = fileChangeMap.computeIfAbsent(file) {
                        SortedList<Pair<TextRange, String>>(comparator)
                    }

                    val changeText = if (psi is ArendPattern && !psi.isExplicit) "{$text}" else text
                    changesList.add(Pair(psi.textRange, changeText))
                }

                for (changeEntry in fileChangeMap) { // Leave only non-overlapping top-level text changes
                    var sampleEntry: Pair<TextRange, String>? = null
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
        }

    }
}