package org.arend.quickfix

class ImportQuickFixTest : QuickFixTestBase() {
    fun testMisplacedImport() = simpleQuickFixTest("Fix", """ 
       \module M \where {
         \import{-caret-} X
       } 
    """, """
       \import X

       \module M \where {
       }  
    """)

    fun testDuplicateOpenedName1() = simpleQuickFixTest("Rename", """
       \module M1 \where {
         \func foo => 0
       }

       \module M2 \where {
         \func foo => 0
         \func baz => 0
       }

       \open M1
       \open M2(foo{-caret-}) 
    """, """
       \module M1 \where {
         \func foo => 0
       }

       \module M2 \where {
         \func foo => 0
         \func baz => 0
       }

       \open M1
       \open M2(foo \as foo1) 
    """)

    fun testDuplicateOpenedName2() = simpleQuickFixTest("Rename", """
       \module M1 \where {
         \func foo => 0
       }

       \module M2 \where {
         \func foo => 0
         \func baz => 0
       }

       \open M1
       \open M2{-caret-} 
    """, """
       \module M1 \where {
         \func foo => 0
       }

       \module M2 \where {
         \func foo => 0
         \func baz => 0
       }

       \open M1
       \open M2 \using (foo \as foo1) 
    """)

    fun testDuplicateOpenedName3() = simpleQuickFixTest("Rename", """
       \module M1 \where {
         \func foo => 0
       }

       \module M2 \where {
         \func foo => 0
         \func baz => 0
       }

       \open M1
       \open M2(baz \as foo{-caret-}) 
    """, """
       \module M1 \where {
         \func foo => 0
       }

       \module M2 \where {
         \func foo => 0
         \func baz => 0
       }

       \open M1
       \open M2(baz \as foo1) 
    """)

    fun testExistingOpenedName1() = simpleQuickFixTest("Rename", """
       \module M \where {
         \func foo => 0
         \func baz => 0
       }

       \func foo => 0

       \open M(foo{-caret-})
    """, """
       \module M \where {
         \func foo => 0
         \func baz => 0
       }

       \func foo => 0

       \open M(foo \as foo1) 
    """)

    fun testExistingOpenedName2() = simpleQuickFixTest("Rename", """
       \module M \where {
         \func foo => 0
         \func baz => 0
       }

       \func foo => 0

       \open M(baz \as foo{-caret-})
    """, """
       \module M \where {
         \func foo => 0
         \func baz => 0
       }

       \func foo => 0

       \open M(baz \as foo1)
    """)

    fun testProperRemoving() = simpleQuickFixTest("Rename", """
       \module M1 \where {
         \func foo => 0
         \func baz => 1
       }

       \module M2 \where {
         \func foo => 0
         \func baz => 0
       }

       \open M1
       \open M2 (foo, baz{-caret-})
    """, """
       \module M1 \where {
         \func foo => 0
         \func baz => 1
       }

       \module M2 \where {
         \func foo => 0
         \func baz => 0
       }

       \open M1
       \open M2 (baz \as baz1, foo) 
    """)
}