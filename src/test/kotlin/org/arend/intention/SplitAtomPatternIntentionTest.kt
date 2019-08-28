package org.arend.intention

import org.arend.quickfix.QuickFixTestBase

class SplitAtomPatternIntentionTest: QuickFixTestBase() {
    fun testBasicSplit() = typedQuickFixTest("Split", """
       \func isLessThan2 (a : Nat) : Nat
         | 0 => 1
         | suc _{-caret-} => 1        
    """, """
       \func isLessThan2 (a : Nat) : Nat
         | 0 => 1
         | suc zero => 1
         | suc (suc _x) => 1 
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

    fun testVariableReplacement() = typedQuickFixTest("Split", """
       \data Vec (A : \Type) (n : Nat) \elim n
         | 0 => nil
         | suc n => cons A (Vec A n)
  
       \func foo {A : \Type} (n : Nat) (xs : Vec A n) : \Sigma Nat Nat
         | {A}, n{-caret-}, xs : Vec A n => (suc n, n) 
    """, """
       \data Vec (A : \Type) (n : Nat) \elim n
         | 0 => nil
         | suc n => cons A (Vec A n)
  
       \func foo {A : \Type} (n : Nat) (xs : Vec A n) : \Sigma Nat Nat
         | {A}, zero, xs : Vec A zero => (suc zero, zero)
         | {A}, suc _x, xs : Vec A (suc _x) => (suc (suc _x), suc _x) 
    """)

    fun testMatchingPattern() = typedQuickFixTest("Split",
    """
       \data Foo (A : \Type)
         | foo1 (a : Nat) (b : A)

       \func foo3 {A : \Type} (f : Foo A) : Nat
         | foo1 a{-caret-} b => {?} 
    """, """
       \data Foo (A : \Type)
         | foo1 (a : Nat) (b : A)

       \func foo3 {A : \Type} (f : Foo A) : Nat
         | foo1 zero b => {?} 
         | foo1 (suc _x) b => {?}
    """)

    fun testRenaming() = typedQuickFixTest("Split", """
       \data List (A : \Type)
         | nil
         | cons A (List A) 

       \func foo {A : \Type} (l : List A) : Nat
         | nil => 0
         | cons x xs{-caret-} => 1 
    """, """
       \data List (A : \Type)
         | nil
         | cons A (List A) 

       \func foo {A : \Type} (l : List A) : Nat
         | nil => 0
         | cons x nil => 1
         | cons x (cons _x _x1) => 1
    """)

    fun testRenaming2() = typedQuickFixTest("Split", """
       \data List (A : \Type)
         | nil
         | cons A (List A) 

       \func foo {A : \Type} (l l2 : List A) : Nat
         | nil, _ => 0
         | _, nil => 0
         | cons x xs{-caret-}, cons _x _x1 => 1 
    """, """
       \data List (A : \Type)
         | nil
         | cons A (List A) 

       \func foo {A : \Type} (l l2 : List A) : Nat
         | nil, _ => 0
         | _, nil => 0
         | cons x nil, cons _x _x1 => 1
         | cons x (cons _x2 _x3), cons _x _x1 => 1
    """)
}