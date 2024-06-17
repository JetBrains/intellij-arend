package org.arend.inspection

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.arend.fileTreeFromText

internal fun doWarningsCheck(myFixture: CodeInsightTestFixture, contents: String, typecheck: Boolean = false) {
    val fileTree = fileTreeFromText(contents)
    fileTree.create(myFixture.project, myFixture.findFileInTempDir("."))
    myFixture.configureFromTempProjectFile("Main.ard")
    if (typecheck) {
        myFixture.doHighlighting()
    }
    myFixture.checkHighlighting(true, false, false)
}

internal fun doWeakWarningsCheck(myFixture: CodeInsightTestFixture, contents: String, typecheck: Boolean = false) {
    val fileTree = fileTreeFromText(contents)
    fileTree.create(myFixture.project, myFixture.findFileInTempDir("."))
    myFixture.configureFromTempProjectFile("Main.ard")
    if (typecheck) {
        myFixture.doHighlighting()
    }
    myFixture.checkHighlighting(false, false, true)
}
