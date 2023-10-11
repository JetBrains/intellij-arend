package org.arend.module.starter

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.ide.IdeBundle
import com.intellij.ide.projectWizard.ProjectSettingsStep
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.importModule
import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.openSampleFiles
import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.preprocessModuleCreated
import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.setupProject
import com.intellij.ide.starters.local.StarterModuleBuilder.Companion.suggestPackageName
import com.intellij.ide.starters.local.generator.AssetsProcessor
import com.intellij.ide.starters.local.generator.convertOutputLocationForTests
import com.intellij.ide.starters.shared.*
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.GitRepositoryInitializer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.roots.ui.configuration.setupNewModuleJdk
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Version
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.ModalityUiUtil
import org.arend.module.starter.ArendStarterUtils.mergeDependencyConfigs
import org.arend.module.starter.ArendStarterUtils.parseDependencyConfig
import org.arend.module.starter.ArendStarterUtils.parseDependencyConfigVersion
import org.jetbrains.annotations.Nullable
import java.io.IOException
import java.net.URL
import javax.swing.Icon

abstract class ArendStarterModuleBuilder : ModuleBuilder() {

    protected val starterContext: ArendStarterContext = ArendStarterContext()
    private val starterSettings: StarterWizardSettings by lazy { createSettings() }

    override fun getWeight(): Int = OTHER_WEIGHT
    open fun getHelpId(): String? = null

    abstract override fun getBuilderId(): String
    abstract override fun getNodeIcon(): Icon?
    abstract override fun getPresentableName(): String
    abstract override fun getDescription(): String

    protected abstract fun getProjectTypes(): List<StarterProjectType>
    protected abstract fun getLanguages(): List<StarterLanguage>
    protected abstract fun getStarterPack(): StarterPack
    protected open fun getTestFrameworks(): List<StarterTestRunner> = emptyList()
    protected abstract fun getAssets(starter: Starter): List<GeneratorAsset>
    protected open fun isExampleCodeProvided(): Boolean = false
    protected open fun getCustomizedMessages(): CustomizedMessages? = null

    protected open fun getCollapsedDependencyCategories(): List<String> = emptyList()
    protected open fun getFilePathsToOpen(): List<String> = emptyList()

    internal open fun getCollapsedDependencyCategoriesInternal(): List<String> = getCollapsedDependencyCategories()

    internal fun isDependencyAvailableInternal(starter: Starter, dependency: Library): Boolean {
        return isDependencyAvailable(starter, dependency)
    }

    protected open fun isDependencyAvailable(starter: Starter, dependency: Library): Boolean {
        return true
    }

    override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean {
        return sdkType is JavaSdkType && !sdkType.isDependent
    }

    override fun modifyProjectTypeStep(settingsStep: SettingsStep): ModuleWizardStep? {
        // do not add standard SDK selector at the top
        return null
    }

    override fun createProject(name: String?, path: String?): Project? {
        val project = super.createProject(name, path)
        project?.let { setupProject(it) }
        return project
    }

    @Throws(ConfigurationException::class)
    override fun setupModule(module: Module) {
        super.setupModule(module)

        val isMaven = starterContext.projectType?.id?.contains("Maven", ignoreCase = true) ?: false
        ExternalSystemUtil.configureNewModule(module, starterContext.isCreatingNewProject, isMaven)

        startGenerator(module)
    }

    private fun createSettings(): StarterWizardSettings {
        return StarterWizardSettings(
            getProjectTypes(),
            getLanguages(),
            isExampleCodeProvided(),
            false,
            emptyList(),
            null,
            emptyList(),
            emptyList(),
            getTestFrameworks(),
            getCustomizedMessages()
        )
    }

    override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep {
        starterContext.language = starterSettings.languages.first()
        starterContext.testFramework = starterSettings.testFrameworks.firstOrNull()
        starterContext.projectType = starterSettings.projectTypes.firstOrNull()
        starterContext.applicationType = starterSettings.applicationTypes.firstOrNull()
        starterContext.isCreatingNewProject = context.isCreatingNewProject

        return createOptionsStep(ArendStarterContextProvider(this, parentDisposable, starterContext, context, starterSettings, ::getStarterPack))
    }

    override fun createWizardSteps(context: WizardContext, modulesProvider: ModulesProvider): Array<ModuleWizardStep> {
        return arrayOf(createLibrariesStep(
            ArendStarterContextProvider(this, context.disposable, starterContext, context, starterSettings, ::getStarterPack)
        ))
    }

    protected open fun createOptionsStep(contextProvider: ArendStarterContextProvider): ArendStarterInitialStep {
        return ArendStarterInitialStep(contextProvider)
    }

    protected open fun createLibrariesStep(contextProvider: ArendStarterContextProvider): ArendStarterLibrariesStep {
        return ArendStarterLibrariesStep(contextProvider)
    }

    override fun getIgnoredSteps(): List<Class<out ModuleWizardStep>> {
        return listOf(ProjectSettingsStep::class.java)
    }

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        setupNewModuleJdk(modifiableRootModel, moduleJdk, starterContext.isCreatingNewProject)
        doAddContentEntry(modifiableRootModel)
    }

    internal fun loadDependencyConfigInternal(): Map<String, DependencyConfig> {
        return loadDependencyConfig()
    }

    protected fun loadDependencyConfig(): Map<String, DependencyConfig> {
        return starterContext.starterPack.starters.associate { starter ->
            starter.id to starter.versionConfigUrl.openStream().use {
                val dependencyConfigUpdates = starterContext.startersDependencyUpdates[starter.id]
                val dependencyConfigUpdatesVersion = dependencyConfigUpdates?.version?.let { version -> Version.parseVersion(version) }
                    ?: Version(-1, -1, -1)

                val starterDependencyConfig = JDOMUtil.load(it)
                val starterDependencyConfigVersion = parseDependencyConfigVersion(
                    starterDependencyConfig,
                    starter.versionConfigUrl.path
                )

                val mergeDependencyUpdate = starterDependencyConfigVersion < dependencyConfigUpdatesVersion
                if (mergeDependencyUpdate) {
                    mergeDependencyConfigs(
                        parseDependencyConfig(
                            starterDependencyConfig,
                            starter.versionConfigUrl.path,
                            false
                        ),
                        dependencyConfigUpdates
                    )
                }
                else {
                    parseDependencyConfig(starterDependencyConfig, starter.versionConfigUrl.path)
                }
            }
        }
    }

    @Throws(ConfigurationException::class)
    protected open fun validateConfiguration() {
    }

    @Throws(ConfigurationException::class)
    internal fun validateConfigurationInternal() {
        return validateConfiguration()
    }

    @Throws(ConfigurationException::class)
    private fun startGenerator(module: Module) {
        val moduleContentRoot =
            if (!ApplicationManager.getApplication().isUnitTestMode) {
                LocalFileSystem.getInstance().refreshAndFindFileByPath(contentEntryPath!!.replace("\\", "/"))
                    ?: throw IllegalStateException("Module root not found")
            }
            else {
                val contentEntries = ModuleRootManager.getInstance(module).contentEntries
                contentEntries.first { it.sourceFolders.isNotEmpty() }.file!!
            }

        val starter = starterContext.starter ?: throw IllegalStateException("Starter is not set")
        val dependencyConfig = starterContext.starterDependencyConfig ?: error("Starter dependency config is not set")
        val sdk = moduleJdk

        val rootPackage = suggestPackageName(DEFAULT_MODULE_GROUP, DEFAULT_MODULE_ARTIFACT)

        val generatorContext = ArendGeneratorContext(
            starter.id,
            module.name,
            starterContext.version,
            starterContext.testFramework?.id,
            rootPackage,
            sdk?.let { JavaSdk.getInstance().getVersion(it) },
            starterContext.language.id,
            starterContext.libraryIds,
            dependencyConfig,
            getGeneratorContextProperties(sdk, dependencyConfig),
            getAssets(starter),
            convertOutputLocationForTests(moduleContentRoot)
        )

        if (!ApplicationManager.getApplication().isUnitTestMode) {
            WriteAction.runAndWait<Throwable> {
                try {
                    AssetsProcessor.getInstance().generateSources(
                        generatorContext.outputDirectory,
                        generatorContext.assets,
                        getTemplateProperties() + ("context" to generatorContext)
                    )
                }
                catch (e: IOException) {
                    logger<ArendStarterModuleBuilder>().error("Unable to create module by template", e)

                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            JavaStartersBundle.message("starter.generation.error", e.message ?: ""),
                            presentableName)
                    }
                    return@runAndWait
                }

                applyAdditionalChanges(module)
            }

            preprocessModuleCreated(module, this, starterContext.starter?.id)

            StartupManager.getInstance(module.project).runAfterOpened {  // IDEA-244863
                ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal(), module.disposed, Runnable {
                    if (module.isDisposed) return@Runnable

                    ReformatCodeProcessor(module.project, module, false).run()
                    // import of module may dispose it and create another, open files first
                    openSampleFiles(module, getFilePathsToOpen())

                    if (starterContext.gitIntegration) {
                        runBackgroundableTask(IdeBundle.message("progress.title.creating.git.repository"), module.project) {
                            GitRepositoryInitializer.getInstance()?.initRepository(module.project, moduleContentRoot, true)
                        }
                    }

                    importModule(module)
                })
            }
        }
        else {
            // test mode, open files immediately, do not import module
            AssetsProcessor.getInstance().generateSources(
                generatorContext.outputDirectory,
                generatorContext.assets,
                getTemplateProperties() + ("context" to generatorContext)
            )

            ReformatCodeProcessor(module.project, module, false).run()
            openSampleFiles(module, getFilePathsToOpen())
        }
    }

    override fun doAddContentEntry(modifiableRootModel: ModifiableRootModel): ContentEntry? {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            // do not create new content entry
            return modifiableRootModel.contentEntries.first { it.sourceFolders.isNotEmpty() }
        }
        return super.doAddContentEntry(modifiableRootModel)
    }

    open fun getTemplateProperties(): Map<String, Any> = emptyMap()

    open fun applyAdditionalChanges(module: Module) {
        // optional hook method
    }

    protected fun getDependencyConfig(resourcePath: String): URL {
        return javaClass.getResource(resourcePath) ?: error("Failed to get resource: $resourcePath")
    }

    protected open fun getGeneratorContextProperties(sdk: @Nullable Sdk?, dependencyConfig: DependencyConfig): Map<String, String> = emptyMap()
}
