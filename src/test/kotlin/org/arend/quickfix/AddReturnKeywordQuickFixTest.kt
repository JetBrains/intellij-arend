package org.arend.quickfix

import org.arend.util.ArendBundle

class AddReturnKeywordQuickFixTest: QuickFixTestBase() {

    fun testAddReturnKeyword1() = typedQuickFixTest(
        ArendBundle.message("arend.return.add"), """
        \func test => \case{-caret-} 1 \with { zero => 0 | suc y => y }
    """, """
        \func test => \case 1 \return {?}{-caret-} \with { zero => 0 | suc y => y }
    """)

    fun testAddReturnKeyword2() = typedQuickFixTest(
        ArendBundle.message("arend.return.add"), """
        \func test => \case{-caret-} 1, 1 \with { z, w => 0 }
    """, """
        \func test => \case 1, 1 \return {?}{-caret-} \with { z, w => 0 }
    """)
}
