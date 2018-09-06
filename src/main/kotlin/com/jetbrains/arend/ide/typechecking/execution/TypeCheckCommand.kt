package com.jetbrains.arend.ide.typechecking.execution

data class TypeCheckCommand(
        val library: String = "",
        val modulePath: String = "",
        val definitionFullName: String = ""
)
