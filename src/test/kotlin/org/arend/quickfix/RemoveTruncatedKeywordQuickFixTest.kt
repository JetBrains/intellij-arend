package org.arend.quickfix

import org.arend.util.ArendBundle

class RemoveTruncatedKeywordQuickFixTest: QuickFixTestBase() {

    fun testRemoveTruncatedKeywordQuickFix1() = typedQuickFixTest(
        ArendBundle.message("arend.truncated.remove"), """
            \truncated{-caret-} \data D
    """, """
            \data D
    """)

    fun testRemoveTruncatedKeywordQuickFix2() = typedQuickFixTest(
        ArendBundle.message("arend.truncated.remove"), """
            \truncated \data D : \1-Type{-caret-}
              | con1
              | con2
              | con3
    """, """
            \data D : \1-Type
              | con1
              | con2
              | con3
    """)
}
