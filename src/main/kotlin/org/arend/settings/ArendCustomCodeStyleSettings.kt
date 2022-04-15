@file:Suppress("PropertyName")

package org.arend.settings

import com.intellij.configurationStore.Property
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CustomCodeStyleSettings

/**
 * Each of the policies makes the imports sorted alphabetically
 */
class ArendCustomCodeStyleSettings(container : CodeStyleSettings) : CustomCodeStyleSettings("ArendCustomCodeStyleSettings", container) {

    enum class OptimizeImportsPolicy {
        /**
         * If used, 'optimize imports' will just erase all unused imports, opens and identifiers without changing
         * their location or layout ('\import Foo (a, b, c)' won't become '\import Foo')
         */
        SOFT,

        /**
         * If used, 'optimize imports' will replace all imports with their implicit version ('\import Foo (a, b, c)' to
         * '\import Foo'). \hiding will be used to resolve collisions
         */
        ONLY_IMPLICIT,

        /**
         * If used, 'optimize imports' will replace all imports with their explicit version ('\import Foo' to
         * '\import Foo (a, b, c)').
         */
        ONLY_EXPLICIT,
    }

    @Property(externalName = "optimize_imports_policy")
    @JvmField
    var OPTIMIZE_IMPORTS_POLICY : OptimizeImportsPolicy = OptimizeImportsPolicy.SOFT

    @Property(externalName = "explicit_imports_limit")
    @JvmField
    var EXPLICIT_IMPORTS_LIMIT : Int = 10
}