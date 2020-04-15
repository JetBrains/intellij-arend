package org.arend.typechecking.execution

import org.arend.ext.module.ModulePath


enum class LocationKind { SOURCE, TEST, GENERATED }

class FullModulePath(val libraryName: String, val locationKind: LocationKind, path: List<String>) : ModulePath(path)
