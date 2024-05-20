package org.arend.quickfix

import org.arend.util.ArendBundle

class RemoveTruncatedUniverseQuickFixTest: QuickFixTestBase() {

    fun testRemoveTruncatedUniverse() = typedQuickFixTest(
        ArendBundle.message("arend.truncated.remove"), """
            \truncated{-caret-} \data D
    """, """
            \data D
    """)
}
