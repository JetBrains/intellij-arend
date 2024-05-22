package org.arend.quickfix

import org.arend.util.ArendBundle

class RemoveStrictKeywordQuickFixTest: QuickFixTestBase() {

    fun testRemoveStrictKeyword() = typedQuickFixTest(
        ArendBundle.message("arend.remove.strict"), """
            \data D (\strict{-caret-} A : \Type)
    """, """
            \data D (A : \Type)
    """)
}
