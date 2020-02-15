package org.arend.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.util.xmlb.XmlSerializerUtil
import org.arend.core.expr.visitor.ToAbstractVisitor
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.toolWindow.errors.MessageType
import java.util.*

@State(name = "ArendSettings")
class ArendProjectSettings : PersistentStateComponent<ArendProjectSettingsState> {
    val data = ArendProjectSettingsState()

    var autoScrollFromSource = EnumSet.of(MessageType.ERROR, MessageType.WARNING, MessageType.GOAL, MessageType.TYPECHECKING)!!
    var messagesFilterSet = EnumSet.of(MessageType.ERROR, MessageType.WARNING, MessageType.GOAL, MessageType.TYPECHECKING, MessageType.SHORT, MessageType.RESOLVING)!!
    var errorPrintingOptionsFilterSet = PrettyPrinterConfig.DEFAULT.expressionFlags!!
    var goalPrintingOptionsFilterSet = EnumSet.of(ToAbstractVisitor.Flag.SHOW_FIELD_INSTANCE)!!
    // for show-type and show-normalized
    var popupPrintingOptionsFilterSet = PrettyPrinterConfig.DEFAULT.expressionFlags!!

    fun setAutoScrollFromSource(type: MessageType, enabled: Boolean) {
        if (enabled) {
            autoScrollFromSource.add(type)
        } else {
            autoScrollFromSource.remove(type)
        }
    }

    fun setShowMessages(type: MessageType, enabled: Boolean) {
        if (enabled) {
            messagesFilterSet.add(type)
        } else {
            messagesFilterSet.remove(type)
        }
    }

    fun setPrintOption(filterSet: EnumSet<ToAbstractVisitor.Flag>, type: ToAbstractVisitor.Flag, enabled: Boolean) {
        if (enabled)
            filterSet.add(type)
        else
            filterSet.remove(type)
    }

    override fun getState(): ArendProjectSettingsState {
        data.autoScrollFromErrors = autoScrollFromSource.contains(MessageType.ERROR)
        data.autoScrollFromWarnings = autoScrollFromSource.contains(MessageType.WARNING)
        data.autoScrollFromGoals = autoScrollFromSource.contains(MessageType.GOAL)
        data.autoScrollFromTypechecking = autoScrollFromSource.contains(MessageType.TYPECHECKING)
        data.autoScrollFromShort = autoScrollFromSource.contains(MessageType.SHORT)
        data.autoScrollFromResolving = autoScrollFromSource.contains(MessageType.RESOLVING)
        data.autoScrollFromParsing = autoScrollFromSource.contains(MessageType.PARSING)

        data.showErrors = messagesFilterSet.contains(MessageType.ERROR)
        data.showWarnings = messagesFilterSet.contains(MessageType.WARNING)
        data.showGoals = messagesFilterSet.contains(MessageType.GOAL)
        data.showTypechecking = messagesFilterSet.contains(MessageType.TYPECHECKING)
        data.showShort = messagesFilterSet.contains(MessageType.SHORT)
        data.showResolving = messagesFilterSet.contains(MessageType.RESOLVING)
        data.showParsing = messagesFilterSet.contains(MessageType.PARSING)

        getPrintingOptions(errorPrintingOptionsFilterSet, data.errorPrintingOptions)
        getPrintingOptions(goalPrintingOptionsFilterSet, data.goalPrintingOptions)
        getPrintingOptions(popupPrintingOptionsFilterSet, data.popupPrintingOptions)

        return data
    }

    private fun getPrintingOptions(filterSet: EnumSet<ToAbstractVisitor.Flag>, options: ArendPrintingOptions) {
        options.showCoerceDefinitions = filterSet.contains(ToAbstractVisitor.Flag.SHOW_COERCE_DEFINITIONS)
        options.showConstructorParameters = filterSet.contains(ToAbstractVisitor.Flag.SHOW_CON_PARAMS)
        options.showTupleType = filterSet.contains(ToAbstractVisitor.Flag.SHOW_TUPLE_TYPE)
        options.showFieldInstance = filterSet.contains(ToAbstractVisitor.Flag.SHOW_FIELD_INSTANCE)
        options.showImplicitArgs = filterSet.contains(ToAbstractVisitor.Flag.SHOW_IMPLICIT_ARGS)
        options.showTypesInLambda = filterSet.contains(ToAbstractVisitor.Flag.SHOW_TYPES_IN_LAM)
        options.showPrefixPath = filterSet.contains(ToAbstractVisitor.Flag.SHOW_PREFIX_PATH)
        options.showBinOpImplicitArgs = filterSet.contains(ToAbstractVisitor.Flag.SHOW_BIN_OP_IMPLICIT_ARGS)
        options.showCaseResultType = filterSet.contains(ToAbstractVisitor.Flag.SHOW_CASE_RESULT_TYPE)
        options.showInferenceLevelVars = filterSet.contains(ToAbstractVisitor.Flag.SHOW_INFERENCE_LEVEL_VARS)
    }

    override fun loadState(state: ArendProjectSettingsState) {
        XmlSerializerUtil.copyBean(state, data)

        setAutoScrollFromSource(MessageType.ERROR, state.autoScrollFromErrors)
        setAutoScrollFromSource(MessageType.WARNING, state.autoScrollFromWarnings)
        setAutoScrollFromSource(MessageType.GOAL, state.autoScrollFromGoals)
        setAutoScrollFromSource(MessageType.TYPECHECKING, state.autoScrollFromTypechecking)
        setAutoScrollFromSource(MessageType.SHORT, state.autoScrollFromShort)
        setAutoScrollFromSource(MessageType.RESOLVING, state.autoScrollFromResolving)
        setAutoScrollFromSource(MessageType.PARSING, state.autoScrollFromParsing)

        setShowMessages(MessageType.ERROR, state.showErrors)
        setShowMessages(MessageType.WARNING, state.showWarnings)
        setShowMessages(MessageType.GOAL, state.showGoals)
        setShowMessages(MessageType.TYPECHECKING, state.showTypechecking)
        setShowMessages(MessageType.SHORT, state.showShort)
        setShowMessages(MessageType.RESOLVING, state.showResolving)
        setShowMessages(MessageType.PARSING, state.showParsing)

        setPrintingOptions(errorPrintingOptionsFilterSet, state.errorPrintingOptions)
        setPrintingOptions(goalPrintingOptionsFilterSet, state.goalPrintingOptions)
        setPrintingOptions(popupPrintingOptionsFilterSet, state.popupPrintingOptions)
    }

    private fun setPrintingOptions(filterSet: EnumSet<ToAbstractVisitor.Flag>, printingOptions: ArendPrintingOptions) {
        setPrintOption(filterSet, ToAbstractVisitor.Flag.SHOW_COERCE_DEFINITIONS, printingOptions.showCoerceDefinitions)
        setPrintOption(filterSet, ToAbstractVisitor.Flag.SHOW_CON_PARAMS, printingOptions.showConstructorParameters)
        setPrintOption(filterSet, ToAbstractVisitor.Flag.SHOW_TUPLE_TYPE, printingOptions.showTupleType)
        setPrintOption(filterSet, ToAbstractVisitor.Flag.SHOW_FIELD_INSTANCE, printingOptions.showFieldInstance)
        setPrintOption(filterSet, ToAbstractVisitor.Flag.SHOW_IMPLICIT_ARGS, printingOptions.showImplicitArgs)
        setPrintOption(filterSet, ToAbstractVisitor.Flag.SHOW_TYPES_IN_LAM, printingOptions.showTypesInLambda)
        setPrintOption(filterSet, ToAbstractVisitor.Flag.SHOW_PREFIX_PATH, printingOptions.showPrefixPath)
        setPrintOption(filterSet, ToAbstractVisitor.Flag.SHOW_BIN_OP_IMPLICIT_ARGS, printingOptions.showBinOpImplicitArgs)
        setPrintOption(filterSet, ToAbstractVisitor.Flag.SHOW_CASE_RESULT_TYPE, printingOptions.showCaseResultType)
        setPrintOption(filterSet, ToAbstractVisitor.Flag.SHOW_INFERENCE_LEVEL_VARS, printingOptions.showInferenceLevelVars)
    }
}