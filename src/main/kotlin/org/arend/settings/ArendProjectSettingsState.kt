package org.arend.settings

import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase

// This class is needed since EnumSet<MessageType> does not serialize correctly for some reason
class ArendProjectSettingsState {
    // Hierarchy
    var showImplFields = true
    var showNonImplFields = true
    var hierarchyViewType = TypeHierarchyBrowserBase.SUBTYPES_HIERARCHY_TYPE

    // Messages
    var autoScrollToSource = true

    var autoScrollFromErrors = true
    var autoScrollFromWarnings = true
    var autoScrollFromGoals = true
    var autoScrollFromTypechecking = true
    var autoScrollFromShort = false
    var autoScrollFromResolving = false
    var autoScrollFromParsing = false

    var showErrors = true
    var showWarnings = true
    var showGoals = true
    var showTypechecking = true
    var showShort = true
    var showResolving = true
    var showParsing = false

    // Printing options
    var errorPrintingOptions = ArendPrintingOptions()
    var goalPrintingOptions = ArendPrintingOptions()
    var popupPrintingOptions = ArendPrintingOptions()

    var normalizePopup = false

    // Repl options
    var replNormalizationMode = "null"
}

class ArendPrintingOptions {
    var showCoerceDefinitions = false
    var showConstructorParameters = false
    var showTupleType = false
    var showFieldInstance = true
    var showImplicitArgs = false
    var showTypesInLambda = false
    var showPrefixPath = false
    var showBinOpImplicitArgs = false
    var showCaseResultType = false
    var showLevels = false
}