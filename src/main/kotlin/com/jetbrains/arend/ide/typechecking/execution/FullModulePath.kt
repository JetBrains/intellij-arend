package com.jetbrains.arend.ide.typechecking.execution

import com.jetbrains.jetpad.vclang.module.ModulePath


data class FullModulePath(val libraryName: String, val modulePath: ModulePath)