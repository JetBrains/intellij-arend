package org.arend.intention

import org.arend.quickfix.QuickFixTestBase

class ReplaceWithShortNameIntentionTest: QuickFixTestBase() {

    fun testLink() = simpleQuickFixTest("Replace", """
       \module M \where {
         \module M2 \where {
           \func foo => 101
         }
       }

       \func lol => M2.foo{-caret-} \where {
         \open M
       } 
    """, """
       \open M.M2 (foo)

       \module M \where {
         \module M2 \where {
           \func foo => 101
         }
       }

       \func lol => foo{-caret-} \where {
         \open M
       } """)

    fun testHeadCaret() = simpleQuickFixTest("Replace", """
        \func lol => Pa{-caret-}th.inProp
    """, """
        \open Path (inProp)
        
        \func lol => {-caret-}inProp
    """)
}