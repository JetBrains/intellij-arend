package org.arend.module.starter

import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.shared.*
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.UserDataHolderBase

open class ArendCommonStarterContext : UserDataHolderBase() {
    var name: String = DEFAULT_MODULE_NAME
    var version: String = DEFAULT_MODULE_VERSION

    var isCreatingNewProject: Boolean = false
    var gitIntegration: Boolean = false

    lateinit var language: StarterLanguage
    var projectType: StarterProjectType? = null
    var applicationType: StarterAppType? = null
    var testFramework: StarterTestRunner? = null
    var includeExamples: Boolean = true
}

class ArendStarterContext : ArendCommonStarterContext() {
    lateinit var starterPack: StarterPack
    var starter: Starter? = null
    var starterDependencyConfig: DependencyConfig? = null
    val startersDependencyUpdates: MutableMap<String, DependencyConfig> = mutableMapOf()

    val libraryIds: MutableSet<String> = HashSet()
}

class ArendStarterContextProvider(
    val moduleBuilder: ArendStarterModuleBuilder,
    val parentDisposable: Disposable,
    val starterContext: ArendStarterContext,
    val wizardContext: WizardContext,
    val settings: StarterWizardSettings,
    val starterPackProvider: () -> StarterPack
)
