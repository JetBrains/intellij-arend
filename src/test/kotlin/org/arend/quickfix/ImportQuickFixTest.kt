package org.arend.quickfix

import org.arend.util.ArendBundle

class ImportQuickFixTest : QuickFixTestBase() {

    fun testMisplacedImport() = simpleQuickFixTest(ArendBundle.message("arend.import.fixMisplaced"), """ 
       \module M \where {
         \import{-caret-} X
       } 
    """, """
       \import X

       \module M \where {
       }  
    """)

    private val rename = ArendBundle.message("arend.import.rename")
    private val hide = ArendBundle.message("arend.import.hide")

    fun testDuplicateOpenedName1() = simpleQuickFixTest(rename, """
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

    fun testDuplicateOpenedName2() = simpleQuickFixTest(rename, """
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

    fun testDuplicateOpenedName3() = simpleQuickFixTest(rename, """
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

    fun testExistingOpenedName1() = simpleQuickFixTest(rename, """
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

    fun testExistingOpenedName2() = simpleQuickFixTest(rename, """
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

    fun testProperRemoving() = simpleQuickFixTest(rename, """
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

    fun testHideImport01() = simpleQuickFixTest(hide, """
       \module M \where {
         \func foo => 0
       }

       \module N \where {
         \func foo => 1
       }

       \open M
       \open N{-caret-} 
    """, """
       \module M \where {
         \func foo => 0
       }

       \module N \where {
         \func foo => 1
       }

       \open M
       \open N \hiding (foo) 
    """)

    fun testHideImport02() = simpleQuickFixTest(hide , """
       \module M \where {
         \func foo => 0
       }

       \module N \where {
         \func foo => 1
       }

       \open M
       \open N (foo{-caret-})
    """, """
       \module M \where {
         \func foo => 0
       }

       \module N \where {
         \func foo => 1
       }

       \open M
       \open N ()
    """)
}