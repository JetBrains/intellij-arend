package org.arend.quickfix

import org.arend.util.ArendBundle

class RemoveTruncatedKeywordQuickFixTest: QuickFixTestBase() {

    fun testRemoveTruncatedKeywordQuickFix() = typedQuickFixTest(
        ArendBundle.message("arend.truncated.remove"), """
            \truncated{-caret-} \data D
    """, """
            \data D
    """)
}
