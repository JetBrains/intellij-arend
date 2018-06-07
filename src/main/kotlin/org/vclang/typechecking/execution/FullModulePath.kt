package org.vclang.typechecking.execution

import com.jetbrains.jetpad.vclang.module.ModulePath


data class FullModulePath(val libraryName: String, val modulePath: ModulePath)