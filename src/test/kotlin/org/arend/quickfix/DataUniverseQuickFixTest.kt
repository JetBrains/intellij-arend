package org.arend.quickfix

import org.arend.util.ArendBundle

class DataUniverseQuickFixTest : QuickFixTestBase() {

    fun testUniverse() = typedQuickFixTest(ArendBundle.message("arend.universe.replace"), """
        \data D : \Set0{-caret-}
            | con \Set0
    """, """
        \data D : \1-Type1
            | con \Set0
    """)
}
