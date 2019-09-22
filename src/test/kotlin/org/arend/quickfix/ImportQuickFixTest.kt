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
}