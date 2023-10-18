package org.arend.module

import com.intellij.CommonBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.shared.*
import com.intellij.ide.util.projectWizard.SdkSettingsStep
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.layout.selected
import org.arend.ArendIcons
import org.arend.library.LibraryDependency
import org.arend.module.config.ArendModuleConfiguration
import org.arend.module.config.ArendModuleConfigurationUpdater
import org.arend.module.starter.*
import org.arend.module.starter.ArendStarterUtils.getLibraryDependencies
import org.arend.prelude.Prelude
import org.arend.ui.addBrowseAndChangeListener
import org.arend.util.*
import org.arend.util.FileUtils.defaultLibrariesRoot
import javax.swing.Icon
import javax.swing.JTextField

class ArendModuleBuilder : ArendStarterModuleBuilder(), ArendModuleConfiguration {

    private val moduleRoot = moduleFilePath?.let { FileUtil.toSystemDependentName(it) }

    private val textComponentAccessor = object : TextComponentAccessor<JTextField> {
        override fun getText(component: JTextField) = toAbsolute(moduleRoot, component.text)

        override fun setText(component: JTextField, text: String) {
            component.text = toRelative(moduleRoot, text)
        }
    }
    private val langVersionField = JTextField()
    private val versionField = JTextField()
    private val sourcesTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Sources Directory",
            "Select the directory in which the source files${if (name == null) "" else " of module $name"} are located",
            getProject(),
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            textComponentAccessor
        )
    }
    private val testsTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Tests Directory",
            "Select the directory with test files${if (name == null) "" else " for module $name"}",
            getProject(),
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            textComponentAccessor
        )
    }
    private val binariesSwitch = JBCheckBox("Save typechecker output to ", false)
    private val binariesTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Binaries Directory",
            "Select the directory in which the binary files${if (name == null) "" else " of module $name"} will be put",
            getProject(),
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            textComponentAccessor
        )
    }
    private val extensionsSwitch = JBCheckBox("Load language extensions", false)
    private val extensionsTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Extensions Directory",
            "Select the directory in which the language extensions${if (name == null) "" else " of module $name"} are located",
            getProject(),
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            textComponentAccessor
        )
    }
    private val extensionMainClassTextField = JTextField()
    private val libRootTextField = TextFieldWithBrowseButton()
    private val textFieldChangeListener = libRootTextField.addBrowseAndChangeListener(
        "Path to libraries",
        "Select the directory in which dependencies${if (name == null) "" else " of module $name"} are located",
        getProject(),
        FileChooserDescriptorFactory.createSingleFolderDescriptor()
    ) { }

    private fun getProject() = ProjectManager.getInstance().defaultProject

    override var librariesRoot: String
        get() = libRootTextField.text
        set(value) {
            libRootTextField.text = value
            textFieldChangeListener.fireEvent()
        }

    override var sourcesDir: String
        get() = sourcesTextField.text
        set(value) {
            sourcesTextField.text = value
        }

    override var withBinaries: Boolean
        get() = binariesSwitch.isSelected
        set(value) {
            binariesSwitch.isSelected = value
        }

    override var binariesDirectory: String
        get() = binariesTextField.text
        set(value) {
            binariesTextField.text = value
        }

    override var testsDir: String
        get() = testsTextField.text
        set(value) {
            testsTextField.text = value
        }

    override var withExtensions: Boolean
        get() = extensionsSwitch.isSelected
        set(value) {
            extensionsSwitch.isSelected = value
        }

    override var extensionsDirectory: String
        get() = extensionsTextField.text
        set(value) {
            extensionsTextField.text = value
        }

    override var extensionMainClassData: String
        get() = extensionMainClassTextField.text
        set(value) {
            extensionMainClassTextField.text = value
        }

    override var dependencies: List<LibraryDependency>
        get() = libraryStep.getSelectedLibraries().map { LibraryDependency(it.title) }
        set(_) {}

    override var versionString: String
        get() = versionField.text
        set(value) {
            versionField.text = value
        }

    override var langVersionString: String
        get() = langVersionField.text
        set(value) {
            langVersionField.text = value
        }

    private lateinit var libraryStep: ArendStarterLibrariesStep
    override fun createLibrariesStep(contextProvider: ArendStarterContextProvider): ArendStarterLibrariesStep {
        libraryStep = super.createLibrariesStep(contextProvider)
        return libraryStep
    }

    override fun getModuleType() = ArendModuleType.INSTANCE

    override fun getNodeIcon(): Icon = moduleType.getNodeIcon(false)

    override fun getPresentableName(): String = getModuleTypeName()

    override fun getProjectTypes(): List<StarterProjectType> = emptyList()

    override fun getStarterPack(): StarterPack = StarterPack(
        "intellij-arend", listOf(
            Starter(
                "intellij-arend",
                "intellij-arend",
                getDependencyConfig("/starters/intellij-arend.pom"),
                getLibraryList()
            )
        )
    )

    @Suppress("UnstableApiUsage")
    private fun getLibraryList(): List<Library> {
        return getLibraryDependencies(librariesRoot, null).map {
            if (it.name == AREND_LIB) {
                Library(
                    AREND_LIB,
                    ArendIcons.LIBRARY_ICON,
                    AREND_LIB,
                    ArendBundle.message("arend.libs.description.standard"),
                    null,
                    null,
                    listOf(LibraryLink(LibraryLinkType.WEBSITE, "https://github.com/JetBrains/$AREND_LIB"))
                )
            } else {
                Library(
                    it.name,
                    ArendIcons.LIBRARY_ICON,
                    it.name,
                    "No description",
                    null,
                    null,
                    listOf()
                )
            }
        }
    }

    override fun getDescription(): String = moduleType.description

    override fun getLanguages(): List<StarterLanguage> = listOf(AREND_STARTER_LANGUAGE)

    override fun getAssets(starter: Starter): List<GeneratorAsset> = emptyList()

    override fun getBuilderId(): String = "intellij-arend"

    override fun modifySettingsStep(settingsStep: SettingsStep) =
        object : SdkSettingsStep(settingsStep, this, { isSuitableSdkType(it) }) {
            init {
                myJdkComboBox.setSelectedItem(myJdkComboBox.showNoneSdkItem())
            }

            /**
             * This method is copy-pasted from the base class with checks for `null` SDK omitted.
             */
            override fun validate(): Boolean {
                try {
                    myModel.apply(null, true)
                } catch (e: ConfigurationException) {
                    //IDEA-98382 We should allow Next step if user has wrong SDK
                    if (Messages.showDialog(
                            JavaUiBundle.message("dialog.message.0.do.you.want.to.proceed", e.message),
                            e.title,
                            arrayOf(CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()),
                            1,
                            Messages.getWarningIcon()
                        ) != Messages.YES
                    ) {
                        return false
                    }
                }
                return true
            }
        }

    override fun validateModuleName(moduleName: String): Boolean {
        if (!FileUtils.isLibraryName(moduleName)) {
            throw ConfigurationException("Invalid module name")
        }
        return true
    }

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        modifiableRootModel.inheritSdk()
        doAddContentEntry(modifiableRootModel)
        addModuleConfigurationUpdater(ArendModuleConfigurationUpdater(true).apply { copyFrom(this@ArendModuleBuilder) })
    }

    override fun createOptionsStep(contextProvider: ArendStarterContextProvider): ArendStarterInitialStep {
        return ArendExtraStep(contextProvider)
    }

    private inner class ArendExtraStep(contextProvider: ArendStarterContextProvider) : ArendStarterInitialStep(contextProvider) {

        override fun addFieldsAfter(layout: Panel) {
            sdkComboBox.selectedJdk = null

            layout.apply {
                separator()
                aligned("Language version: ", langVersionField) {
                    langVersionField.text = Prelude.VERSION.toString()
                }
                aligned("Library version: ", versionField)
                aligned("Sources directory: ", sourcesTextField) {
                    sourcesTextField.text = FileUtils.DEFAULT_SOURCES_DIR
                }
                aligned("Tests directory: ", testsTextField)
                checked(binariesSwitch, binariesTextField) {
                    binariesSwitch.isSelected = true
                    binariesTextField.text = FileUtils.DEFAULT_BINARIES_DIR
                    align(AlignX.FILL)
                }.layout(RowLayout.LABEL_ALIGNED)

                group("Extensions") {
                    row { cell(extensionsSwitch) }
                    aligned("Extensions directory: ", extensionsTextField) { enabledIf(extensionsSwitch.selected) }
                    aligned(
                        "Extension main class: ", extensionMainClassTextField
                    ) { enabledIf(extensionsSwitch.selected) }
                }
                group("Libraries") {
                    aligned("Path to libraries: ", libRootTextField) {
                        libRootTextField.text = defaultLibrariesRoot().toString()
                    }
                }
            }
        }
    }
}
