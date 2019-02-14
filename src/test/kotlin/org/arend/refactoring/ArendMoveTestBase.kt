package org.arend.refactoring

import com.intellij.psi.PsiElement
import org.arend.ArendTestBase
import org.arend.fileTreeFromText
import org.arend.psi.ancestors
import org.arend.psi.ext.impl.ArendGroup
import org.arend.refactoring.move.ArendMoveMembersDialog
import org.arend.refactoring.move.ArendMoveMembersDialog.Companion.simpleLocate
import org.arend.refactoring.move.ArendStaticMemberRefactoringProcessor
import org.arend.term.group.ChildGroup
import org.intellij.lang.annotations.Language

abstract class ArendMoveTestBase : ArendTestBase() {
    fun testMoveRefactoring(@Language("Arend") contents: String,
                            @Language("Arend") resultingContent: String?, //Null indicates that an error is expected as a correct test result
                            targetFile: String,
                            targetName: String) {
        val fileTree = fileTreeFromText(contents)
        fileTree.createAndOpenFileWithCaretMarker()
        val sourceElement = myFixture.elementAtCaret.ancestors.filterIsInstance<ArendGroup>().firstOrNull() ?: throw AssertionError("Cannot find source element")

        doPerformMoveRefactoringTest(resultingContent, targetFile, targetName, sourceElement)
    }

    fun testMoveRefactoring(@Language("Arend") contents: String,
                            @Language("Arend") resultingContent: String?,
                            sourceFile: String,
                            sourceName: String,
                            targetFile: String,
                            targetName: String) {
        val fileTree = fileTreeFromText(contents)
        fileTree.createAndOpenFileWithCaretMarker()
        val sourceElement = simpleLocate(sourceFile, sourceName, myFixture.module) as ArendGroup

        doPerformMoveRefactoringTest(resultingContent, targetFile, targetName, sourceElement)
    }

    private fun doPerformMoveRefactoringTest(@Language("Arend") resultingContent: String?,
                                             targetFile: String,
                                             targetName: String,
                                             sourceElement: ArendGroup?) {
        val expectsError: Boolean = resultingContent == null
        val container = (sourceElement as ChildGroup).parentGroup ?: throw AssertionError("Source element has no parent")

        val myTargetGroup = ArendMoveMembersDialog.locateTargetGroupWithChecks(targetFile, targetName, myFixture.module, container, listOf(sourceElement))
        val group = myTargetGroup.first
        if (group is PsiElement) {
            val processor = ArendStaticMemberRefactoringProcessor(myFixture.project, {}, listOf(sourceElement), container, group)
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