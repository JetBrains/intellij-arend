package org.arend.module.starter

import com.intellij.ide.IdeBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.starters.shared.*
import com.intellij.ide.util.installNameGenerators
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.*
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withPathToTextConvertor
import com.intellij.openapi.ui.BrowseFolderDescriptor.Companion.withTextToPathConvertor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import org.jetbrains.annotations.Nls
import java.io.File
import javax.swing.JComponent
import javax.swing.JTextField

abstract class ArendCommonStarterInitialStep(
    protected val wizardContext: WizardContext,
    private val starterContext: ArendCommonStarterContext,
    private val moduleBuilder: ModuleBuilder,
    protected val parentDisposable: Disposable,
    protected val starterSettings: StarterWizardSettings
) : ModuleWizardStep() {
    protected val validatedTextComponents: MutableList<JTextField> = mutableListOf()

    protected val propertyGraph: PropertyGraph = PropertyGraph()
    protected val entityNameProperty: GraphProperty<String> = propertyGraph.lazyProperty(::suggestName)
    private val locationProperty: GraphProperty<String> = propertyGraph.lazyProperty(::suggestLocationByName)
    private val canonicalPathProperty = locationProperty.joinCanonicalPath(entityNameProperty)
    protected val sdkProperty: GraphProperty<Sdk?> = propertyGraph.lazyProperty { null }

    protected val languageProperty: GraphProperty<StarterLanguage> =
        propertyGraph.lazyProperty { starterContext.language }
    protected val projectTypeProperty: GraphProperty<StarterProjectType> = propertyGraph.lazyProperty {
        starterContext.projectType ?: StarterProjectType("unknown", "")
    }
    protected val testFrameworkProperty: GraphProperty<StarterTestRunner> = propertyGraph.lazyProperty {
        starterContext.testFramework ?: StarterTestRunner("unknown", "")
    }
    protected val applicationTypeProperty: GraphProperty<StarterAppType> = propertyGraph.lazyProperty {
        starterContext.applicationType ?: StarterAppType("unknown", "")
    }
    protected val exampleCodeProperty: GraphProperty<Boolean> =
        propertyGraph.lazyProperty { starterContext.includeExamples }
    protected val gitProperty: GraphProperty<Boolean> = propertyGraph.property(false)
        .bindBooleanStorage(NewProjectWizardStep.GIT_PROPERTY_NAME)

    protected var entityName: String by entityNameProperty.trim()
    protected var location: String by locationProperty

    protected lateinit var groupRow: Row
    protected lateinit var artifactRow: Row

    protected lateinit var sdkComboBox: JdkComboBox

    protected fun Panel.addProjectLocationUi() {
        row(UIBundle.message("label.project.wizard.new.project.name")) {
            textField()
                .bindText(entityNameProperty)
                .withSpecialValidation(
                    listOf(ValidationFunctions.CHECK_NOT_EMPTY, ValidationFunctions.CHECK_SIMPLE_NAME_FORMAT),
                    ValidationFunctions.createLocationWarningValidator(locationProperty)
                )
                .columns(COLUMNS_MEDIUM)
                .gap(RightGap.SMALL)
                .focused()

            installNameGenerators(moduleBuilder.builderId, entityNameProperty)
        }.bottomGap(BottomGap.SMALL)

        val locationRow = row(UIBundle.message("label.project.wizard.new.project.location")) {
            val commentLabel = projectLocationField(locationProperty, wizardContext)
                .align(AlignX.FILL)
                .withSpecialValidation(
                    ValidationFunctions.CHECK_NOT_EMPTY,
                    ValidationFunctions.CHECK_LOCATION_FOR_ERROR
                )
                .comment(getLocationComment(), 100)
                .comment!!

            entityNameProperty.afterChange { commentLabel.text = getLocationComment() }
            locationProperty.afterChange { commentLabel.text = getLocationComment() }
        }

        if (wizardContext.isCreatingNewProject) {
            // Git should not be enabled for single module
            row("") {
                checkBox(UIBundle.message("label.project.wizard.new.project.git.checkbox"))
                    .bindSelected(gitProperty)
            }.bottomGap(BottomGap.SMALL)
        } else {
            locationRow.bottomGap(BottomGap.SMALL)
        }
    }

    private fun getLocationComment(): @Nls String {
        val shortPath = StringUtil.shortenPathWithEllipsis(getPresentablePath(canonicalPathProperty.get()), 60)
        return UIBundle.message(
            "label.project.wizard.new.project.path.description",
            wizardContext.isCreatingNewProjectInt,
            shortPath
        )
    }

    protected fun Panel.addSampleCodeUi() {
        if (starterSettings.isExampleCodeProvided) {
            row {
                checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
                    .bindSelected(exampleCodeProperty)
            }
        }
    }

    protected fun Panel.addSdkUi() {
        row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
            sdkComboBox =
                sdkComboBox(wizardContext, sdkProperty, StdModuleTypes.JAVA.id, moduleBuilder::isSuitableSdkType)
                    .columns(COLUMNS_MEDIUM)
                    .component
        }.bottomGap(BottomGap.SMALL)
        row {
            comment("Project SDK is needed if you want to create a language extension or debug typechecking")
        }.bottomGap(BottomGap.SMALL)
    }

    protected open fun addFieldsBefore(layout: Panel) {}

    protected open fun addFieldsAfter(layout: Panel) {}

    protected open fun getCustomValidationRules(propertyId: String): Array<TextValidationFunction> = emptyArray()

    private fun suggestName(): String {
        return suggestName(DEFAULT_MODULE_ARTIFACT)
    }

    protected fun suggestName(prefix: String): String {
        val projectFileDirectory = File(wizardContext.projectFileDirectory)
        return FileUtil.createSequentFileName(projectFileDirectory, prefix, "")
    }

    private fun suggestLocationByName(): String {
        return wizardContext.projectFileDirectory
    }

    @Suppress("SameParameterValue")
    protected fun <T : JComponent> Cell<T>.withSpecialValidation(vararg errorValidationUnits: TextValidationFunction): Cell<T> =
        withValidation(this, errorValidationUnits.asList(), null, validatedTextComponents, parentDisposable)

    private fun <T : JComponent> Cell<T>.withSpecialValidation(
        errorValidationUnits: List<TextValidationFunction>,
        warningValidationUnit: TextValidationFunction?
    ): Cell<T> {
        return withValidation(
            this,
            errorValidationUnits,
            warningValidationUnit,
            validatedTextComponents,
            parentDisposable
        )
    }

    private fun Row.projectLocationField(
        locationProperty: GraphProperty<String>,
        wizardContext: WizardContext
    ): Cell<TextFieldWithBrowseButton> {
        val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()
            .withFileFilter { it.isDirectory }
            .withPathToTextConvertor(::getPresentablePath)
            .withTextToPathConvertor(::getCanonicalPath)
        val title = IdeBundle.message("title.select.project.file.directory", wizardContext.presentationName)
        val property = locationProperty.transform(::getPresentablePath, ::getCanonicalPath)
        return textFieldWithBrowseButton(fileChooserDescriptor.withTitle(title), wizardContext.project)
            .bindText(property)
    }
}
