package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentsOfType
import com.intellij.util.castSafelyTo
import org.arend.codeInsight.ImportedName
import org.arend.codeInsight.OptimalModuleStructure
import org.arend.codeInsight.OptimizationResult
import org.arend.codeInsight.getOptimalImportStructure
import org.arend.inspection.ArendUnusedImportInspection
import org.arend.intention.ArendOptimizeImportsQuickFix
import org.arend.psi.ArendFile
import org.arend.psi.ArendStatement
import org.arend.psi.ext.ArendReferenceContainer
import org.arend.psi.ext.impl.ArendGroup
import org.arend.util.ArendBundle
import org.jetbrains.annotations.Nls

class ArendUnusedImportHighlightingPass(private val file: ArendFile, private val editor: Editor) :
    TextEditorHighlightingPass(file.project, editor.document) {

    @Volatile
    private var optimizationResult: OptimizationResult? = null

    override fun doCollectInformation(progress: ProgressIndicator) {
        optimizationResult = getOptimalImportStructure(file, false, progress)
    }

    private fun registerUnusedThing(
        element: PsiElement,
        description: @Nls String,
        collector: MutableList<HighlightInfo>
    ) {
        val profile = InspectionProjectProfileManager.getInstance(myProject).currentProfile
        val key = HighlightDisplayKey.find(ArendUnusedImportInspection.ID)
        val highlightInfoType = if (key == null) HighlightInfoType.UNUSED_SYMBOL else HighlightInfoType.HighlightInfoTypeImpl(profile.getErrorLevel(key, element).severity, HighlightInfoType.UNUSED_SYMBOL.attributesKey)
        UnusedSymbolUtil.createUnusedSymbolInfo(element, description, highlightInfoType, ArendUnusedImportInspection.ID)?.let {
            val actualOptimizationResult = optimizationResult
            if (actualOptimizationResult != null) {
                val intentionAction = QuickFixWrapper.wrap(InspectionManager.getInstance(element.project).createProblemDescriptor(element, description, ArendOptimizeImportsQuickFix(actualOptimizationResult), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true), 0)
                QuickFixAction.registerQuickFixAction(it, intentionAction)
            }
            collector.add(it)
        }
    }

    override fun doApplyInformationToEditor() {
        val (fileImports, openStructure, _) = optimizationResult ?: return
        val infos = mutableListOf<HighlightInfo>()
        highlightNsCommands(
            file.statements.filter { it.statCmd?.importKw != null },
            fileImports,
            infos,
            ArendBundle.message("arend.inspection.unused.import.message.unused.import")
        )
        highlightOpens(file, openStructure, openStructure.usages, infos)
        UpdateHighlightersUtil.setHighlightersToEditor(
            file.project,
            editor.document,
            0,
            file.textLength,
            infos,
            colorsScheme,
            id
        )
    }

    private fun highlightNsCommands(
        statements: List<ArendStatement>,
        pattern: Map<List<String>, Set<ImportedName>>,
        collector: MutableList<HighlightInfo>,
        bigErrorDescription: @Nls String
    ) {
        for (importStatement in statements) {
            val statCmd = importStatement.statCmd ?: continue
            val path = statCmd.longName ?: continue
            val qualifiedReference = statCmd.openedReference?.castSafelyTo<ArendReferenceContainer>()?.resolve?.qualifiedName() ?: path.longName
            val imported = pattern[qualifiedReference]
            if (imported == null) {
                registerUnusedThing(importStatement.statCmd!!, bigErrorDescription, collector)
                continue
            }
            val using = statCmd.nsUsing ?: continue
            for (nsId in using.nsIdList) {
                val importedName = ImportedName(nsId.refIdentifier.text, nsId.defIdentifier?.text)
                if (importedName !in imported) {
                    registerUnusedThing(nsId, ArendBundle.message("arend.inspection.unused.import.message.unused.definition"), collector)
                }
            }
        }
    }

    private fun PsiElement?.qualifiedName() : List<String>? {
        if (this is PsiFile) {
            return null
        }
        if (this !is ArendGroup) {
            return null
        }
        return this.parentsOfType<ArendGroup>(true).mapNotNull { it.name }.toList().reversed().drop(1)
    }

    private fun highlightOpens(
        group: ArendGroup,
        structure: OptimalModuleStructure?,
        patterns: Map<List<String>, Set<ImportedName>>,
        collector: MutableList<HighlightInfo>
    ) {
        highlightNsCommands(group.statements.filter { it.statCmd?.openKw != null }, patterns, collector, ArendBundle.message("arend.inspection.unused.import.message.unused.open"))
        for (subgroup in group.subgroups + group.dynamicSubgroups) {
            val substructure = structure?.subgroups?.find { it.name == subgroup.refName }
            highlightOpens(subgroup, substructure, patterns + (substructure?.usages ?: emptyMap()), collector)
        }
    }
}