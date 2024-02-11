package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class ReplaceWithShortNameIntentionTest: QuickFixTestBase() {
    private fun doTest(contents: String, result: String) = simpleQuickFixTest(ArendBundle.message("arend.import.replaceWithShortName"), contents, result)

    fun testLink() = doTest("""
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

    fun testHeadCaret() = doTest("""
        \func lol => Na{-caret-}t.div
    """, """
        \open Nat (div)
        
        \func lol => {-caret-}div
    """)

    fun testSingleItemInWhere() = doTest("""
       \module Long \where {
         \func name => 1
       }

       \func foo => 0 \where
           \func bar => Long.name{-caret-}
    """, """
       \open Long (name)
       
       \module Long \where {
         \func name => 1
       }
       
       \func foo => 0 \where
           \func bar => name{-caret-}
    """)

    fun testVisibleElement() = doTest("""
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

    fun testMultipleElements() = doTest("""
       \module Long \where {
         \func name => 1
       }

       \func foo => 0 \where {
         \func bar => (Long.name{-caret-}, Long.name)
       }
    """, """
       \open Long (name)
       
       \module Long \where {
         \func name => 1
       }

       \func foo => 0 \where {
         \func bar => (name{-caret-}, name)
       }
    """)

    fun testObstructedScopes() = doTest("""
       \module M \where {
         \func foo => 1
       }
       
       \module N \where {
         \func bar => M.{-caret-}foo
         
         \module Z \where {
           \func foo => 2
           
           \func bar => M.foo Nat.+ foo
         }
       }
    """, """
       \open M (foo)
       
       \module M \where {
         \func foo => 1
       }

       \module N \where {
         \func bar => foo

         \module Z \where {
           \func foo => 2

           \func bar => M.foo Nat.+ foo
         }
       } 
    """)

    fun testInPatterns() = doTest("""
       \module M \where {
         \data \infixl 2 union (A B : \Type)
           | inj1 (a : A)
           | inj2 (b : B)
       }

       \func bar (A B : \Type) (u : M.union A B) : Nat \elim u
         | M.inj1{-caret-} a => 1
         | M.inj2 b => 2 
    """, """
       \open M (inj1) 
        
       \module M \where {
         \data \infixl 2 union (A B : \Type)
           | inj1 (a : A)
           | inj2 (b : B)
       }

       \func bar (A B : \Type) (u : M.union A B) : Nat \elim u
         | inj1 a => 1
         | M.inj2 b => 2 
    """)
}