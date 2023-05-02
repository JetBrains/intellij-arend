package org.arend.quickfix

import org.arend.util.ArendBundle

class AddReturnKeywordQuickFixTest: QuickFixTestBase() {

    fun testAddReturnKeyword() = typedQuickFixTest(
        ArendBundle.message("arend.return.add"), """
        \func test => \case{-caret-} 1 \with { zero => 0 | suc y => y }
    """, """
        \func test => \case 1 \return {?} \with { zero => 0 | suc y => y }
    """)
}
