package org.arend.settings

import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import org.arend.core.expr.visitor.ToAbstractVisitor
import org.arend.term.prettyprint.PrettyPrinterConfig

// This class is needed since EnumSet<MessageType> does not serialize correctly for some reason
class ArendProjectSettingsState {
    // Hierarchy
    var showImplFields = true
    var showNonImplFields = true
    var hierarchyViewType = TypeHierarchyBrowserBase.SUBTYPES_HIERARCHY_TYPE!!

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

    //Printing options
    var hideHideableDefinitions = false
    var showConstructorParameters = true
    var showFieldInstance = true
    var showImplicitArgs = true
    var showTypesInLambda = true
    var showPrefixPath = true
    var showBinOpImplicitArgs = true
    var showCaseResultType = true
    var showInferenceLevelVars = true

}