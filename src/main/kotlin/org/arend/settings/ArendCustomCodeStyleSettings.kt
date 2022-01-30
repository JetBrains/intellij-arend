@file:Suppress("PropertyName")

package org.arend.settings

import com.intellij.configurationStore.Property
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

class ArendCustomCodeStyleSettings(container : CodeStyleSettings) : CustomCodeStyleSettings("ArendCustomCodeStyleSettings", container) {

    @Property(externalName = "use_implicit_imports")
    @JvmField
    var USE_IMPLICIT_IMPORTS : Boolean = true

    @Property(externalName = "explicit_imports_limit")
    @JvmField
    var EXPLICIT_IMPORTS_LIMIT : Int = 10
}