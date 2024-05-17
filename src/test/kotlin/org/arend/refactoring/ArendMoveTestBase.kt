package org.arend.refactoring

import com.intellij.openapi.components.service
import org.arend.ArendTestBase
import org.arend.fileTreeFromText
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendGroup
import org.arend.refactoring.move.ArendMoveHandlerDelegate
import org.arend.refactoring.move.ArendMoveMembersDialog
import org.arend.refactoring.move.ArendMoveMembersDialog.Companion.determineClassPart
import org.arend.refactoring.move.ArendMoveMembersDialog.Companion.getLocateErrorMessage
import org.arend.refactoring.move.ArendMoveMembersDialog.Companion.simpleLocate
import org.arend.refactoring.move.ArendMoveRefactoringProcessor
import org.arend.settings.ArendSettings
import org.intellij.lang.annotations.Language
import java.lang.IllegalArgumentException

abstract class ArendMoveTestBase : ArendTestBase() {
    fun doTestMoveRefactoring(@Language("Arend") contents: String,
                              @Language("Arend") resultingContent: String?, //Null indicates that an error is expected as a correct test result
                              targetFile: String,
                              targetName: String,
                              targetIsDynamic: Boolean = false,
                              useOpenCommands: Boolean = false,
                              typecheck: Boolean = false,
                              fileToCheck: String? = null) {
        val arendSettings = service<ArendSettings>()
        arendSettings.autoImportWriteOpenCommands = useOpenCommands
        try {
            val fileTree = fileTreeFromText(contents)
            fileTree.createAndOpenFileWithCaretMarker()

            if (typecheck) typecheck()

            val sourceElement = myFixture.elementAtCaret.ancestor<ArendGroup>() ?: throw AssertionError("Cannot find source anchor")

            doPerformMoveRefactoringTest(resultingContent, targetFile, targetName, targetIsDynamic, listOf(sourceElement), fileToCheck)
        } finally {
            arendSettings.autoImportWriteOpenCommands = false
        }
    }

    fun doTestMoveRefactoring(@Language("Arend") contents: String,
                              @Language("Arend") resultingContent: String?,
                              targetFile: String,
                              targetName: String,
                              sourceFile: String,
                              vararg sourceNames: String,
                              targetIsDynamic: Boolean = false,
                              fileToCheck: String? = null) {
        val fileTree = fileTreeFromText(contents)
        fileTree.createAndOpenFileWithCaretMarker()
        val sourceElements = ArrayList<ArendGroup>()

        for (sourceName in sourceNames.asList()) {
            val sourceElement = simpleLocate(sourceFile, sourceName, myFixture.module).first
            if (sourceElement is ArendGroup) sourceElements.add(sourceElement) else
                throw IllegalArgumentException("Cannot locate source element named $sourceName")
        }

        doPerformMoveRefactoringTest(resultingContent, targetFile, targetName, targetIsDynamic, sourceElements, fileToCheck)
    }

    private fun doPerformMoveRefactoringTest(@Language("Arend") resultingContent: String?,
                                             targetFile: String,
                                             targetName: String,
                                             targetIsDynamic: Boolean,
                                             sourceElements: List<ArendGroup>,
                                             fileToCheck: String? = null) {
        val expectsError: Boolean = resultingContent == null
        val container = ArendMoveHandlerDelegate.getCommonContainer(sourceElements) ?: throw AssertionError("Elements are not contained in the same ChildGroup")

        val myTargetGroup = ArendMoveMembersDialog.locateTargetGroupWithChecks(targetFile, targetName, myFixture.module, container, sourceElements, determineClassPart(sourceElements), targetIsDynamic)
        val group = myTargetGroup.first
        if (group != null) {
            val processor = ArendMoveRefactoringProcessor(myFixture.project, {}, sourceElements, container, group, targetIsDynamic,
                myOpenInEditor = false,
                myOptimizeImportsAfterMove = false
            )
            try {
                processor.run()
            } catch (e: Exception) {
                if (!expectsError) throw e else return
            }
            if (resultingContent != null) {
                if (fileToCheck == null)
                    myFixture.checkResult(resultingContent.trimIndent(), true)
                else
                    myFixture.checkResult(fileToCheck, resultingContent.trimIndent(), false)
            }
            else throw AssertionError("Error was expected as a correct test result")
        } else {
            if (!expectsError) throw AssertionError(getLocateErrorMessage(myTargetGroup.second))
        }
    }
}