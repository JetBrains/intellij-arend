package org.arend.quickfix

import org.arend.util.ArendBundle

class RemoveUseLevelQuickFixTest: QuickFixTestBase() {

    fun testRemoveUseLevel1() = typedQuickFixTest(
        ArendBundle.message("arend.remove.useLevel"), """
        \data D
          \where {
            \use \level{-caret-} proof (x y : D) : x = y
          }
    """, """
        \data D
          \where {
            }
    """
    )

    fun testRemoveUseLevel2() = typedQuickFixTest(
        ArendBundle.message("arend.remove.useLevel"), """
        \data D
          \where
            \use \level{-caret-} proof (x y : D) : x = y
    """, """
        \data D
        
    """
    )
}
