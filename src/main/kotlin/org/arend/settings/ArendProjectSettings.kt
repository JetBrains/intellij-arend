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
    var printOptionsFilterSet = PrettyPrinterConfig.DEFAULT.expressionFlags

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

    fun setPrintOption(type: ToAbstractVisitor.Flag, enabled: Boolean) {
        if (enabled)
            printOptionsFilterSet.add(type)
        else
            printOptionsFilterSet.remove(type)
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

        data.hideHideableDefinitions = printOptionsFilterSet.contains(ToAbstractVisitor.Flag.HIDE_HIDEABLE_DEFINITIONS)
        data.showConstructorParameters = printOptionsFilterSet.contains(ToAbstractVisitor.Flag.SHOW_CON_PARAMS)
        data.showFieldInstance = printOptionsFilterSet.contains(ToAbstractVisitor.Flag.SHOW_FIELD_INSTANCE)
        data.showImplicitArgs = printOptionsFilterSet.contains(ToAbstractVisitor.Flag.SHOW_IMPLICIT_ARGS)
        data.showTypesInLambda = printOptionsFilterSet.contains(ToAbstractVisitor.Flag.SHOW_TYPES_IN_LAM)
        data.showPrefixPath = printOptionsFilterSet.contains(ToAbstractVisitor.Flag.SHOW_PREFIX_PATH)
        data.showBinOpImplicitArgs = printOptionsFilterSet.contains(ToAbstractVisitor.Flag.SHOW_BIN_OP_IMPLICIT_ARGS)
        data.showCaseResultType = printOptionsFilterSet.contains(ToAbstractVisitor.Flag.SHOW_CASE_RESULT_TYPE)
        data.showInferenceLevelVars = printOptionsFilterSet.contains(ToAbstractVisitor.Flag.SHOW_INFERENCE_LEVEL_VARS)

        return data
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

        setPrintOption(ToAbstractVisitor.Flag.HIDE_HIDEABLE_DEFINITIONS, state.hideHideableDefinitions)
        setPrintOption(ToAbstractVisitor.Flag.SHOW_CON_PARAMS, state.showConstructorParameters)
        setPrintOption(ToAbstractVisitor.Flag.SHOW_FIELD_INSTANCE, state.showFieldInstance)
        setPrintOption(ToAbstractVisitor.Flag.SHOW_IMPLICIT_ARGS, state.showImplicitArgs)
        setPrintOption(ToAbstractVisitor.Flag.SHOW_TYPES_IN_LAM, state.showTypesInLambda)
        setPrintOption(ToAbstractVisitor.Flag.SHOW_PREFIX_PATH, state.showPrefixPath)
        setPrintOption(ToAbstractVisitor.Flag.SHOW_BIN_OP_IMPLICIT_ARGS, state.showBinOpImplicitArgs)
        setPrintOption(ToAbstractVisitor.Flag.SHOW_CASE_RESULT_TYPE, state.showCaseResultType)
        setPrintOption(ToAbstractVisitor.Flag.SHOW_INFERENCE_LEVEL_VARS, state.showInferenceLevelVars)
    }
}