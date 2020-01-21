package org.arend.toolWindow.errors

import org.arend.ext.error.GeneralError
import java.util.*

enum class MessageType {
    ERROR { override fun toText() = "error" },
    WARNING { override fun toText() = "warning" },
    GOAL { override fun toText() = "goal" },
    TYPECHECKING { override fun toText() = "typechecker message" },
    SHORT { override fun toText() = "short message" },
    RESOLVING { override fun toText() = "resolver message" },
    PARSING { override fun toText() = "parser message" };

    abstract fun toText(): String
}

val GeneralError.Level.toMessageType
    get() = when (this) {
        GeneralError.Level.ERROR -> MessageType.ERROR
        GeneralError.Level.WARNING -> MessageType.WARNING
        GeneralError.Level.WARNING_UNUSED -> MessageType.WARNING
        GeneralError.Level.GOAL -> MessageType.GOAL
        else -> null
    }

val GeneralError.Stage.toMessageType
    get() = when (this) {
        GeneralError.Stage.TYPECHECKER -> MessageType.TYPECHECKING
        GeneralError.Stage.RESOLVER -> MessageType.RESOLVING
        GeneralError.Stage.PARSER -> MessageType.PARSING
        else -> null
    }

fun GeneralError.satisfies(types: EnumSet<MessageType>) =
    level.toMessageType?.let { types.contains(it) } == true &&
    stage.toMessageType?.let { types.contains(it) } == true &&
    (types.contains(MessageType.SHORT) || !isShort)
