package org.arend.intention

import org.arend.quickfix.QuickFixTestBase

class SplitAtomPatternIntentionTest: QuickFixTestBase() {
    fun testBasicSplit() = typedQuickFixTest("Split", """
       \func isLessThan2 (a : Nat) : Nat
         | 0 => 1
         | suc _{-caret-} => 1        
    """, """
       \func isLessThan2 (a : Nat) : Bool
         | 0 => 1
         | suc zero => 1
         | suc (suc _) => 1 
    """)

    fun testNoSplitInConstructor() = typedCheckNoQuickFixes("Split", """
         \func isLessThan2 (a : Nat) : Nat
         | 0 => 1
         | suc{-caret-} _ => 1    
    """)

    fun testSplitEmptyPattern1() = typedQuickFixTest("Split", """
       \data Empty 
        
       \func foobar (e : Empty) : Nat
         | e{-caret-} => {?} 
    """, """
       \data Empty
        
       \func foobar (e : Empty) : Nat
         | () => {?} 
    """)

    fun testSplitEmptyPattern2() = typedQuickFixTest("Split", """
       \data Empty 
        
       \data Foo
         | goo Empty

       \func bar (f : Foo) : Nat
         | goo e{-caret-} => {?} 
    """, """
       \data Empty
        
       \data Foo
         | goo Empty

       \func bar (f : Foo) : Nat
         | goo () => {?}  
    """)
}