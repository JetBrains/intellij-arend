package org.vclang

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import com.jetbrains.jetpad.vclang.util.FileUtils
import org.intellij.lang.annotations.Language
import org.vclang.module.VcRawLibrary
import org.vclang.psi.parentOfType
import org.vclang.typechecking.TypeCheckingService

abstract class VcTestBase : LightPlatformCodeInsightFixtureTestCase(), VcTestCase {

    override fun getProjectDescriptor(): LightProjectDescriptor = DefaultDescriptor

    override fun isWriteActionRequired(): Boolean = false

    open val dataPath: String = ""

    override fun getTestDataPath(): String = "${VcTestCase.testResourcesPath}/$dataPath"

    override fun setUp() {
        super.setUp()

        val root = ModuleRootManager.getInstance(myModule).contentEntries.firstOrNull()?.file
        if (root != null) {
            val name = myModule.name + FileUtils.LIBRARY_EXTENSION
            if (root.findChild(name) == null) {
                runWriteAction { root.createChildData(root, name) }
            }
        }

        val service = TypeCheckingService.getInstance(myModule.project)
        val library = VcRawLibrary(myModule, service.typecheckerState)
        service.libraryManager.unloadLibrary(library)
        service.libraryManager.loadLibrary(library)
    }

    override fun runTest() {
        val projectDescriptor = projectDescriptor
        val reason = (projectDescriptor as? VclangProjectDescriptorBase)?.skipTestReason
        if (reason != null) {
            System.err.println("SKIP $name: $reason")
            return
        }

        super.runTest()
    }

    protected val fileName: String
        get() = testName + FileUtils.EXTENSION

    private val testName: String
        get() = camelOrWordsToSnake(getTestName(true))

    protected fun checkByDirectory(action: () -> Unit) {
        val (before, after) = ("$testName/before" to "$testName/after")

        val targetPath = ""
        val beforeDir = myFixture.copyDirectoryToProject(before, targetPath)

        action()

        val afterDir = getVirtualFileByName("$testDataPath/$after")
        PlatformTestUtil.assertDirectoriesEqual(afterDir, beforeDir)
    }

    protected fun checkByDirectory(
            @Language("Vclang") before: String,
            @Language("Vclang") after: String,
            action: () -> Unit
    ) {
        fileTreeFromText(before).create()
        action()
        FileDocumentManager.getInstance().saveAllDocuments()
        fileTreeFromText(after).assertEquals(myFixture.findFileInTempDir("."))
    }

    protected fun checkByText(
            @Language("Vclang") before: String,
            @Language("Vclang") after: String,
            action: () -> Unit
    ) {
        InlineFile(before)
        action()
        myFixture.checkResult(replaceCaretMarker(after))
    }

    private fun getVirtualFileByName(path: String): VirtualFile? =
            LocalFileSystem.getInstance().findFileByPath(path)

    protected open class VclangProjectDescriptorBase : LightProjectDescriptor() {
        open val skipTestReason: String? = null

        final override fun configureModule(
                module: Module,
                model: ModifiableRootModel,
                contentEntry: ContentEntry
        ) {
            super.configureModule(module, model, contentEntry)

            skipTestReason ?: return
            LibraryTablesRegistrar.getInstance().getLibraryTable(module.project).libraries.forEach { model.addLibraryEntry(it) }
        }
    }

    protected object DefaultDescriptor : VclangProjectDescriptorBase()

    inner class InlineFile(@Language("Vclang") private val code: String, name: String = "Main.vc") {
        private val hasCaretMarker = "{-caret-}" in code

        init {
            myFixture.configureByText(name, replaceCaretMarker(code))
        }

        fun withCaret() = check(hasCaretMarker) { "Please, add `{-caret-}` marker to\n$code" }
    }

    protected inline fun <reified T : PsiElement> findElementInEditor(marker: String = "^"): T {
        val (element, data) = findElementWithDataAndOffsetInEditor<T>(marker)
        check(data.isEmpty()) { "Did not expect marker data" }
        return element
    }

    protected inline fun <reified T : PsiElement> findElementWithDataAndOffsetInEditor(
            marker: String = "^"
    ): Triple<T, String, Int> {
        val caretMarker = "--$marker"
        val (elementAtMarker, data, offset) = run {
            val text = myFixture.file.text
            val markerOffset = text.indexOf(caretMarker)
            check(markerOffset != -1) { "No `$marker` marker:\n$text" }
            check(text.indexOf(caretMarker, startIndex = markerOffset + 1) == -1) {
                "More than one `$marker` marker:\n$text"
            }

            val data = text
                    .drop(markerOffset)
                    .removePrefix(caretMarker)
                    .takeWhile { it != '\n' }
                    .trim()
            val markerPosition = myFixture.editor.offsetToLogicalPosition(
                    markerOffset + caretMarker.length - 1
            )
            val previousLine = LogicalPosition(markerPosition.line - 1, markerPosition.column)
            val elementOffset = myFixture.editor.logicalPositionToOffset(previousLine)
            Triple(myFixture.file.findElementAt(elementOffset)!!, data, elementOffset)
        }
        val element = elementAtMarker.parentOfType<T>(strict = false)
                ?: error("No ${T::class.java.simpleName} at ${elementAtMarker.text}")
        return Triple(element, data, offset)
    }

    companion object {
        @JvmStatic
        fun camelOrWordsToSnake(name: String): String {
            if (' ' in name) return name.replace(" ", "_")
            return name
                    .split("(?=[A-Z])".toRegex())
                    .joinToString("_", transform = String::toLowerCase)
        }
    }

    private fun FileTree.create(): TestProject =
            create(myFixture.project, myFixture.findFileInTempDir("."))

    protected fun FileTree.createAndOpenFileWithCaretMarker(): TestProject {
        val testProject = create()
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        return testProject
    }
}
