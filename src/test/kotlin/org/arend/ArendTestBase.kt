package org.arend

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ThrowableRunnable
import com.maddyhome.idea.vim.VimPlugin
import org.arend.error.DummyErrorReporter
import org.arend.ext.DefinitionContributor
import org.arend.ext.module.ModulePath
import org.arend.ext.prettyprinting.doc.DocFactory
import org.arend.ext.reference.Precedence
import org.arend.extImpl.OldDefinitionContributorImpl
import org.arend.module.AREND_LIB
import org.arend.module.ArendModuleType
import org.arend.module.ArendRawLibrary
import org.arend.module.ModuleLocation
import org.arend.module.ModuleSynchronizer
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.ExternalLibraryConfig
import org.arend.module.scopeprovider.SimpleModuleScopeProvider
import org.arend.naming.reference.FullModuleReferable
import org.arend.naming.reference.MetaReferable
import org.arend.psi.parentOfType
import org.arend.server.ArendServerService
import org.arend.settings.ArendSettings
import org.arend.term.group.AccessModifier
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.util.FileUtils
import org.arend.util.findExternalLibrary
import org.intellij.lang.annotations.Language

abstract class ArendTestBase : BasePlatformTestCase(), ArendTestCase {

    override fun getProjectDescriptor(): LightProjectDescriptor = DefaultDescriptor

    override fun isWriteActionRequired(): Boolean = false

    open val dataPath: String = ""

    override fun getTestDataPath(): String = "${ArendTestCase.testResourcesPath}/$dataPath"

    override fun setUp() {
        super.setUp()

        service<ArendSettings>().isBackgroundTypechecking = true

        library.setArendExtension(null)

        VimPlugin.setEnabled(false)
    }

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        val projectDescriptor = projectDescriptor
        val reason = (projectDescriptor as? ArendProjectDescriptorBase)?.skipTestReason
        if (reason != null) {
            System.err.println("SKIP $name: $reason")
            return
        }

        super.runTestRunnable(testRunnable)
    }

    protected fun makeMetaRef(name: String): MetaReferable =
        MetaReferable(AccessModifier.PUBLIC, Precedence.DEFAULT, name, null, null, FullModuleReferable(ModuleLocation(module.name, ModuleLocation.LocationKind.GENERATED, ModulePath("Meta"))))

    protected fun addGeneratedModules(filler: DefinitionContributor.() -> Unit) {
        addGeneratedModules(this.library, filler)
    }

    protected val library: ArendRawLibrary
        get() = ArendModuleConfigService.getInstance(module)?.library
                ?: throw IllegalStateException("Cannot find library")

    protected val fileName: String
        get() = testName + FileUtils.EXTENSION

    private val testName: String
        get() = camelOrWordsToSnake(getTestName(true))

    protected fun checkByDirectory(action: () -> Unit) {
        val (before, after) = ("$testName/before" to "$testName/after")

        val targetPath = ""
        val beforeDir = myFixture.copyDirectoryToProject(before, targetPath)

        action()

        val afterDir = findTestDataFile(after)
        PlatformTestUtil.assertDirectoriesEqual(afterDir, beforeDir)
    }

    protected fun checkByDirectory(
            @Language("Arend") before: String,
            @Language("Arend") after: String,
            action: () -> Unit
    ) {
        fileTreeFromText(before).create()
        action()
        FileDocumentManager.getInstance().saveAllDocuments()
        fileTreeFromText(after).assertEquals(myFixture.findFileInTempDir("."))
    }

    protected fun checkByText(
            @Language("Arend") before: String,
            @Language("Arend") after: String,
            action: () -> Unit
    ) {
        InlineFile(before)
        action()
        myFixture.checkResult(replaceCaretMarker(after))
    }

    private fun findTestDataFile(path: String): VirtualFile =
            LocalFileSystem.getInstance().findFileByPath("$testDataPath/$path")!!

    protected open class ArendProjectDescriptorBase : LightProjectDescriptor() {
        open val skipTestReason: String? = null

        final override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
            super.configureModule(module, model, contentEntry)

            skipTestReason ?: return
            LibraryTablesRegistrar.getInstance().getLibraryTable(module.project).libraries.forEach { model.addLibraryEntry(it) }
        }

        override fun getModuleTypeId() = ArendModuleType.INSTANCE.id
    }

    protected object DefaultDescriptor : ArendProjectDescriptorBase()

    inner class InlineFile(@Language("Arend") private val code: String, name: String = "Main.ard") {
        private val hasCaretMarker = CARET_MARKER in code
        val psiFile: PsiFile = myFixture.configureByText(name, replaceCaretMarker(code))

        fun withCaret(): PsiFile {
            check(hasCaretMarker) { "Please, add `$CARET_MARKER` marker to\n$code" }
            return psiFile
        }
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
        const val CARET_MARKER = "{-caret-}"

        @JvmStatic
        fun camelOrWordsToSnake(name: String): String {
            if (' ' in name) return name.replace(" ", "_")
            return name
                    .split("(?=[A-Z])".toRegex())
                    .joinToString("_", transform = String::lowercase)
        }

        fun addGeneratedModules(library: ArendRawLibrary, filler: DefinitionContributor.() -> Unit) {
            val moduleScopeProvider = SimpleModuleScopeProvider()
            filler(OldDefinitionContributorImpl(library.name, DummyErrorReporter.INSTANCE, moduleScopeProvider))
            /* TODO[server2]
            for (entry in moduleScopeProvider.registeredEntries) {
                library.addGeneratedModule(entry.key, entry.value)
            }
            */
        }
    }

    protected fun FileTree.create(): TestProject =
            create(myFixture.project, myFixture.findFileInTempDir("."))

    protected fun FileTree.createAndOpenFileWithCaretMarker(): TestProject {
        val testProject = create()
        myFixture.configureFromTempProjectFile(testProject.fileWithCaret)
        return testProject
    }

    fun testCaret(resultingContent: String) {
        val rC = resultingContent.trimIndent()
        val index = rC.indexOf(CARET_MARKER)
        if (index != -1) {
            val contentWithoutMarkers = rC.replace(CARET_MARKER, "")
            myFixture.checkResult(contentWithoutMarkers, false)
            val actualCaret = myFixture.caretOffset
            if (index != actualCaret) {
                if (actualCaret < rC.length) {
                    System.err.println("Expected caret kind: \n$rC")
                    System.err.println("Actual caret kind: \n${StringBuilder(contentWithoutMarkers).insert(actualCaret, CARET_MARKER)}")
                } else {
                    println("Expected caret kind: $index\n Actual caret kind: $actualCaret")
                }
                assert(false)
            }
        } else {
            myFixture.checkResult(rC, true)
        }
    }

    fun typecheck(fileNames: List<ModulePath> = listOf(ModulePath("Main"))) {
        project.service<ArendServerService>().server.getCheckerFor(fileNames.map { ModuleLocation(module.name, ModuleLocation.LocationKind.SOURCE, it) }).typecheck(DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE)
    }

    protected fun withStdLib(name: String = AREND_LIB, test: () -> Unit) {
        try {
            createStdLib(name)
            test()
        } finally {
            removeStdLib(name)
        }
    }

    private fun createStdLib(name: String) {
        val libRoot = LocalFileSystem.getInstance().findFileByPath("${ArendTestCase.testResourcesPath}/org/arend")!!
        val arendLibConfig = project.findExternalLibrary(libRoot, AREND_LIB)
                ?: throw IllegalStateException("Cannot find arend-lib")
        setupLibraryManager(arendLibConfig)
        setupProjectModel(arendLibConfig, name)
    }

    private fun setupLibraryManager(config: ExternalLibraryConfig) {
        val arendLib = ArendRawLibrary(config)
        addGeneratedModules(arendLib) {
            declare(DocFactory.nullDoc(), MetaReferable(AccessModifier.PUBLIC, Precedence.DEFAULT, "using", null, null, FullModuleReferable(ModuleLocation(config.libraryName, ModuleLocation.LocationKind.GENERATED, ModulePath("Meta")))), null)
            declare(DocFactory.nullDoc(), MetaReferable(AccessModifier.PUBLIC, Precedence.DEFAULT, "$", null, null, FullModuleReferable(ModuleLocation(config.libraryName, ModuleLocation.LocationKind.GENERATED, ModulePath("Function", "Meta")))), null)
            declare(DocFactory.nullDoc(), MetaReferable(AccessModifier.PUBLIC, Precedence.DEFAULT, "rewrite", null, null, FullModuleReferable(ModuleLocation(config.libraryName, ModuleLocation.LocationKind.GENERATED, ModulePath("Paths", "Meta")))), null)
        }
        TypeCheckingService.LibraryManagerTestingOptions.setStdLibrary(arendLib, testRootDisposable)
    }

    private fun setupProjectModel(config: ExternalLibraryConfig, libName: String) {
        runWriteAction {
            val projectModel = LibraryTablesRegistrar.getInstance().getLibraryTable(project).modifiableModel
            val ideaLib = projectModel.createLibrary(libName)
            projectModel.commit()
            ideaLib.modifiableModel.apply {
                ModuleSynchronizer.setupFromConfig(this, config)
                commit()
            }
            ModuleRootModificationUtil.updateModel(module) { it.addLibraryEntry(ideaLib) }
        }
    }

    private fun removeStdLib(libName: String) {
        runWriteAction {
            val projectTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
            val arendLib = projectTable.getLibraryByName(libName) ?: return@runWriteAction
            ModuleRootModificationUtil.updateModel(module) { model ->
                model.findLibraryOrderEntry(arendLib)?.let { model.removeOrderEntry(it) }
            }
            projectTable.removeLibrary(arendLib)
        }
    }
}
