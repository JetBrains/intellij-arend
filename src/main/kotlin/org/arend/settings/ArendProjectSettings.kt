package org.arend.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.toolWindow.errors.MessageType
import java.util.*

@Service(Service.Level.PROJECT)
@State(name = "ArendSettings")
class ArendProjectSettings : PersistentStateComponent<ArendProjectSettingsState> {
    val data = ArendProjectSettingsState()

    var autoScrollFromSource = EnumSet.of(MessageType.ERROR, MessageType.WARNING, MessageType.GOAL, MessageType.TYPECHECKING)!!
    var messagesFilterSet = EnumSet.of(MessageType.ERROR, MessageType.WARNING, MessageType.GOAL, MessageType.TYPECHECKING, MessageType.SHORT, MessageType.RESOLVING)!!
    var consolePrintingOptionsFilterSet = PrettyPrinterConfig.DEFAULT.expressionFlags
    var errorPrintingOptionsFilterSet = PrettyPrinterConfig.DEFAULT.expressionFlags
    var goalPrintingOptionsFilterSet = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE)!!
    var tracerPrintingOptionsFilterSet = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE)!!

    // for show-type and show-normalized
    var popupPrintingOptionsFilterSet = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE)!!
    var replPrintingOptionsFilterSet = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE)!!

    var librariesRoot: String
        get() = data.librariesRoot ?: service<ArendSettings>().librariesRoot
        set(value) {
            data.librariesRoot = value
            service<ArendSettings>().librariesRoot = value
        }

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

    fun setPrintOption(filterSet: EnumSet<PrettyPrinterFlag>, type: PrettyPrinterFlag, enabled: Boolean) {
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

        data.popupPrintingOptions = ArendPrintingOptions()
        data.consolePrintingOptions = ArendPrintingOptions()
        data.errorPrintingOptions = ArendPrintingOptions()
        data.goalPrintingOptions = ArendPrintingOptions()
        data.tracerPrintingOptions = ArendPrintingOptions()
        data.replPrintingOptions = ArendPrintingOptions()

        getPrintingOptions(consolePrintingOptionsFilterSet, data.consolePrintingOptions)
        getPrintingOptions(errorPrintingOptionsFilterSet, data.errorPrintingOptions)
        getPrintingOptions(goalPrintingOptionsFilterSet, data.goalPrintingOptions)
        getPrintingOptions(tracerPrintingOptionsFilterSet, data.tracerPrintingOptions)
        getPrintingOptions(popupPrintingOptionsFilterSet, data.popupPrintingOptions)
        getPrintingOptions(replPrintingOptionsFilterSet, data.replPrintingOptions)

        return data
    }

    private fun getPrintingOptions(filterSet: EnumSet<PrettyPrinterFlag>, options: ArendPrintingOptions) {
        options.showCoerceDefinitions = filterSet.contains(PrettyPrinterFlag.SHOW_COERCE_DEFINITIONS)
        options.showConstructorParameters = filterSet.contains(PrettyPrinterFlag.SHOW_CON_PARAMS)
        options.showTupleType = filterSet.contains(PrettyPrinterFlag.SHOW_TUPLE_TYPE)
        options.showLocalFieldInstance = filterSet.contains(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE)
        options.showGlobalFieldInstance = filterSet.contains(PrettyPrinterFlag.SHOW_GLOBAL_FIELD_INSTANCE)
        options.showImplicitArgs = filterSet.contains(PrettyPrinterFlag.SHOW_IMPLICIT_ARGS)
        options.showTypesInLambda = filterSet.contains(PrettyPrinterFlag.SHOW_TYPES_IN_LAM)
        options.showPrefixPath = filterSet.contains(PrettyPrinterFlag.SHOW_PREFIX_PATH)
        options.showBinOpImplicitArgs = filterSet.contains(PrettyPrinterFlag.SHOW_BIN_OP_IMPLICIT_ARGS)
        options.showCaseResultType = filterSet.contains(PrettyPrinterFlag.SHOW_CASE_RESULT_TYPE)
        options.showLevels = filterSet.contains(PrettyPrinterFlag.SHOW_LEVELS)
        options.showProofs = filterSet.contains(PrettyPrinterFlag.SHOW_PROOFS)
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

        setPrintingOptions(consolePrintingOptionsFilterSet, state.consolePrintingOptions)
        setPrintingOptions(errorPrintingOptionsFilterSet, state.errorPrintingOptions)
        setPrintingOptions(goalPrintingOptionsFilterSet, state.goalPrintingOptions)
        setPrintingOptions(tracerPrintingOptionsFilterSet, state.tracerPrintingOptions)
        setPrintingOptions(popupPrintingOptionsFilterSet, state.popupPrintingOptions)
        setPrintingOptions(replPrintingOptionsFilterSet, state.replPrintingOptions)
    }

    private fun setPrintingOptions(filterSet: EnumSet<PrettyPrinterFlag>, printingOptions: ArendPrintingOptions) {
        setPrintOption(filterSet, PrettyPrinterFlag.SHOW_COERCE_DEFINITIONS, printingOptions.showCoerceDefinitions)
        setPrintOption(filterSet, PrettyPrinterFlag.SHOW_CON_PARAMS, printingOptions.showConstructorParameters)
        setPrintOption(filterSet, PrettyPrinterFlag.SHOW_TUPLE_TYPE, printingOptions.showTupleType)
        setPrintOption(filterSet, PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE, printingOptions.showLocalFieldInstance)
        setPrintOption(filterSet, PrettyPrinterFlag.SHOW_GLOBAL_FIELD_INSTANCE, printingOptions.showGlobalFieldInstance)
        setPrintOption(filterSet, PrettyPrinterFlag.SHOW_IMPLICIT_ARGS, printingOptions.showImplicitArgs)
        setPrintOption(filterSet, PrettyPrinterFlag.SHOW_TYPES_IN_LAM, printingOptions.showTypesInLambda)
        setPrintOption(filterSet, PrettyPrinterFlag.SHOW_PREFIX_PATH, printingOptions.showPrefixPath)
        setPrintOption(filterSet, PrettyPrinterFlag.SHOW_BIN_OP_IMPLICIT_ARGS, printingOptions.showBinOpImplicitArgs)
        setPrintOption(filterSet, PrettyPrinterFlag.SHOW_CASE_RESULT_TYPE, printingOptions.showCaseResultType)
        setPrintOption(filterSet, PrettyPrinterFlag.SHOW_LEVELS, printingOptions.showLevels)
        setPrintOption(filterSet, PrettyPrinterFlag.SHOW_PROOFS, printingOptions.showProofs)
    }
}