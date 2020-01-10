package org.arend.typechecking.execution

import org.arend.ext.module.ModulePath


data class FullModulePath(val libraryName: String, val modulePath: ModulePath)