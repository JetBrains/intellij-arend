package org.arend.refactoring

import com.intellij.psi.PsiElement
import org.arend.ArendTestBase
import org.arend.fileTreeFromText
import org.arend.psi.ArendDefinition
import org.arend.psi.ancestors
import org.arend.refactoring.move.ArendMoveMembersDialog
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
        val sourceElement = myFixture.elementAtCaret.ancestors.firstOrNull { it is ChildGroup && it is ArendDefinition } ?: throw AssertionError("Cannot find source element")
        val container = (sourceElement as ChildGroup).parentGroup as? PsiElement ?: throw AssertionError("Source element has parent of wrong type")
        if (sourceElement !is ArendDefinition) throw AssertionError("Source element is of wrong type")

        val myTargetGroup = ArendMoveMembersDialog.locateTargetGroupWithChecks(targetFile, targetName, myFixture.module, container, listOf(sourceElement))
        val group = myTargetGroup.first
        if (group is PsiElement) {
            val processor = ArendStaticMemberRefactoringProcessor(myFixture.project, {}, listOf(sourceElement) , group)
            processor.run()
            if (resultingContent != null) myFixture.checkResult(resultingContent.trimIndent(), true)
            else throw AssertionError("Error was expected as a correct test result")
        } else {
            if (resultingContent != null) throw AssertionError(myTargetGroup.second)
        }
    }
}