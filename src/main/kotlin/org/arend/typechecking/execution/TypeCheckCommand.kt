package org.arend.typechecking.execution

data class TypeCheckCommand(
    val library: String = "",
    val isTest: Boolean = false,
    val modulePath: String = "",
    val definitionFullName: String = ""
)
