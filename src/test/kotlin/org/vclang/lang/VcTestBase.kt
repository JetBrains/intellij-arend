package org.vclang.lang

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

abstract class VcTestBase : LightPlatformCodeInsightFixtureTestCase(), VcTestCase {

    override fun getProjectDescriptor(): LightProjectDescriptor = DefaultDescriptor

    override fun isWriteActionRequired(): Boolean = false

    open val dataPath: String = ""

    override fun getTestDataPath(): String = "${VcTestCase.testResourcesPath}/$dataPath"

    override fun runTest() {
        val projectDescriptor = projectDescriptor
        val reason = (projectDescriptor as? VclangProjectDescriptorBase)?.skipTestReason
        if (reason != null) {
            System.err.println("SKIP $name: $reason")
            return
        }

        super.runTest()
    }

    protected open class VclangProjectDescriptorBase : LightProjectDescriptor() {
        open val skipTestReason: String? = null

        final override fun configureModule(
                module: Module,
                model: ModifiableRootModel,
                contentEntry: ContentEntry
        ) {
            super.configureModule(module, model, contentEntry)
            skipTestReason ?: return
            val libraries = LibraryTablesRegistrar.getInstance()
                    .getLibraryTable(module.project).libraries
            libraries.forEach { model.addLibraryEntry(it) }
        }
    }

    protected object DefaultDescriptor : VclangProjectDescriptorBase()

    inner class InlineFile(
            private @Language("Vclang") val code: String,
            name: String = "main.vc"
    ) {
        private val hasCaretMarker = "{-caret-}" in code

        init {
            myFixture.configureByText(name, replaceCaretMarker(code))
        }

        fun withCaret() {
            check(hasCaretMarker) {
                "Please, add `{-caret-}` marker to\n$code"
            }
        }
    }

    protected fun replaceCaretMarker(text: String) = text.replace("{-caret-}", "<caret>")
}
