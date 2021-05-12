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

    fun testSingleItemInWhere() = simpleQuickFixTest("Replace", """
       \module Long \where {
         \func name => 1
       }

       \func foo => 0 \where
           \func bar => Long.name{-caret-}
    """, """
       \module Long \where {
         \func name => 1
       }

       \func foo => 0 \where {
         \open Long (name)
       
         \func bar => name{-caret-}
       }
    """)

    fun testVisibleElement() = simpleQuickFixTest("Replace", """
       \module Long \where {
         \func name => 1
       }

       \open Long

       \func foo => 0 \where {
         \func bar => Long.name{-caret-}
       }
    """, """
       \module Long \where {
         \func name => 1
       }

       \open Long

       \func foo => 0 \where {
         \func bar => name
       } 
    """)

    fun testMultipleElements() = simpleQuickFixTest("Replace", """
       \module Long \where {
         \func name => 1
       }

       \func foo => 0 \where {
         \func bar => (Long.name{-caret-}, Long.name)
       }
    """, """
       \module Long \where {
         \func name => 1
       }

       \func foo => 0 \where {
         \open Long (name)
       
         \func bar => (name{-caret-}, name)
       }
    """)
}