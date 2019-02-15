package org.arend.refactoring

import com.intellij.psi.PsiElement
import org.arend.ArendTestBase
import org.arend.fileTreeFromText
import org.arend.psi.ancestors
import org.arend.psi.ext.impl.ArendGroup
import org.arend.refactoring.move.ArendMoveHandlerDelegate
import org.arend.refactoring.move.ArendMoveMembersDialog
import org.arend.refactoring.move.ArendMoveMembersDialog.Companion.simpleLocate
import org.arend.refactoring.move.ArendStaticMemberRefactoringProcessor
import org.arend.term.group.ChildGroup
import org.intellij.lang.annotations.Language
import java.lang.IllegalArgumentException

abstract class ArendMoveTestBase : ArendTestBase() {
    fun testMoveRefactoring(@Language("Arend") contents: String,
                            @Language("Arend") resultingContent: String?, //Null indicates that an error is expected as a correct test result
                            targetFile: String,
                            targetName: String) {
        val fileTree = fileTreeFromText(contents)
        fileTree.createAndOpenFileWithCaretMarker()
        val sourceElement = myFixture.elementAtCaret.ancestors.filterIsInstance<ArendGroup>().firstOrNull() ?: throw AssertionError("Cannot find source anchor")

        doPerformMoveRefactoringTest(resultingContent, targetFile, targetName, listOf(sourceElement))
    }

    fun testMoveRefactoring(@Language("Arend") contents: String,
                            @Language("Arend") resultingContent: String?,
                            targetFile: String,
                            targetName: String,
                            sourceFile: String,
                            vararg sourceNames: String) {
        val fileTree = fileTreeFromText(contents)
        fileTree.createAndOpenFileWithCaretMarker()
        val sourceElements = ArrayList<ArendGroup>()

        for (sourceName in sourceNames.asList()) {
            val sourceElement = simpleLocate(sourceFile, sourceName, myFixture.module)
            if (sourceElement is ArendGroup) sourceElements.add(sourceElement) else
                throw IllegalArgumentException("Cannot locate source element named $sourceName")
        }

        doPerformMoveRefactoringTest(resultingContent, targetFile, targetName, sourceElements)
    }

    private fun doPerformMoveRefactoringTest(@Language("Arend") resultingContent: String?,
                                             targetFile: String,
                                             targetName: String,
                                             sourceElements: List<ArendGroup>) {
        val expectsError: Boolean = resultingContent == null
        val container = ArendMoveHandlerDelegate.getCommonContainer(sourceElements) ?: throw AssertionError("Elements are not contained in the same ChildGroup")

        val myTargetGroup = ArendMoveMembersDialog.locateTargetGroupWithChecks(targetFile, targetName, myFixture.module, container, sourceElements)
        val group = myTargetGroup.first
        if (group is PsiElement) {
            val processor = ArendStaticMemberRefactoringProcessor(myFixture.project, {}, sourceElements, container, group)
            try {
                processor.run()
            } catch (e: Exception) {
                if (!expectsError) throw e else return
            }
            if (resultingContent != null) myFixture.checkResult(resultingContent.trimIndent(), true)
            else throw AssertionError("Error was expected as a correct test result")
        } else {
            if (!expectsError) throw AssertionError(myTargetGroup.second)
        }
    }
}