package org.arend.settings

import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase

// This class is needed since EnumSet<MessageType> does not serialize correctly for some reason
data class ArendProjectSettingsState(
    // Hierarchy
    var showImplFields: Boolean = true,
    var showNonImplFields: Boolean = true,
    var hierarchyViewType: String = TypeHierarchyBrowserBase.SUBTYPES_HIERARCHY_TYPE,

    // Messages
    var autoScrollToSource: Boolean = true,

    var autoScrollFromErrors: Boolean = true,
    var autoScrollFromWarnings: Boolean = true,
    var autoScrollFromGoals: Boolean = true,
    var autoScrollFromTypechecking: Boolean = true,
    var autoScrollFromShort: Boolean = false,
    var autoScrollFromResolving: Boolean = false,
    var autoScrollFromParsing: Boolean = false,

    var showErrors: Boolean = true,
    var showWarnings: Boolean = true,
    var showGoals: Boolean = true,
    var showTypechecking: Boolean = true,
    var showShort: Boolean = true,
    var showResolving: Boolean = true,
    var showParsing: Boolean = false,

    //Printing options
    val errorPrintingOptions: ArendPrintingOptions = ArendPrintingOptions(),
    val goalPrintingOptions: ArendPrintingOptions = ArendPrintingOptions(),
    val popupPrintingOptions: ArendPrintingOptions = ArendPrintingOptions()
)

data class ArendPrintingOptions (
    var showCoerceDefinitions: Boolean = false,
    var showConstructorParameters: Boolean = true,
    var showTupleType: Boolean = true,
    var showFieldInstance: Boolean = true,
    var showImplicitArgs: Boolean = true,
    var showTypesInLambda: Boolean = true,
    var showPrefixPath: Boolean = true,
    var showBinOpImplicitArgs: Boolean = true,
    var showCaseResultType: Boolean = true,
    var showInferenceLevelVars: Boolean = true
)