package org.arend.intention

import org.arend.quickfix.QuickFixTestBase

class SplitAtomPatternIntentionTest: QuickFixTestBase() {
    fun testBasicSplit() = typedQuickFixTest("Split", """"
       \func isLessThan2 (a : Nat) : Nat
         | 0 => 1
         | suc _{-caret-} => 1        
    """, """
       \func isLessThan2 (a : Nat) : Bool
         | 0 => 1
         | suc zero => 1
         | suc (suc _) => {?} 
    """)

    fun testNoSplitInConstructor() = typedCheckNoQuickFixes("Split", """
         \func isLessThan2 (a : Nat) : Nat
         | 0 => 1
         | suc{-caret-} _ => 1    
    """)
}