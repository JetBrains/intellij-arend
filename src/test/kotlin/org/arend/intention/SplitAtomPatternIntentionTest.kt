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
         | () 
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
         | goo ()  
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
         | cons x xs{-caret-}, cons _x _x1 => foo xs _x1 
    """, """
       \data List (A : \Type)
         | nil
         | cons A (List A) 

       \func foo {A : \Type} (l l2 : List A) : Nat
         | nil, _ => 0
         | _, nil => 0
         | cons x nil, cons _x _x1 => foo nil _x1
         | cons x (cons _x2 _x3), cons _x _x1 => foo (cons _x2 _x3) _x1
    """)

    fun testInfixNotation() = typedQuickFixTest("Split", """
       \data List (A : \Type)
         | nil
         | \infixr 5 :: A (List A)

       \func foo (l : List Nat) : Nat
         | nil => 0
         | :: x xs{-caret-} => (foo xs) Nat.+ x
    """, """
       \data List (A : \Type)
         | nil
         | \infixr 5 :: A (List A)

       \func foo (l : List Nat) : Nat
         | nil => 0
         | :: x nil => (foo nil) Nat.+ x 
         | :: x (:: _x _x1) => (foo (_x :: _x1)) Nat.+ x
    """)

    fun testSimpleResolving() = typedQuickFixTest("Split", """
       --! A.ard
       \data List (A : \Type)
         | nil
         | \infixr 5 :: A (List A)
  
       --! Main.ard
       \import A (List)
       
       \func foo (xs : List Nat) : Nat
         | xs{-caret-} => 0 
    """, """
       \import A (::, List, nil) 
        
       \func foo (xs : List Nat) : Nat
         | nil => 0
         | :: _x _x1 => 0 
    """)

    fun testLongName() = typedQuickFixTest("Split", """
       --! MyNat.ard
       \module Foo \where { 
         \data MyNatural
           | myZero
           | mySuc (MyNatural)
       } 
        
       \open Foo 
       
       \data Vec (A : \Type) (n : MyNatural) \elim n
         | myZero => nil
         | mySuc n => cons A (Vec A n) 
         
       --! Main.ard
       \import MyNat
       
       \func foo {A : \Type} (n : Foo.MyNatural) (xs : Vec A n) : \Sigma Foo.MyNatural Foo.MyNatural
         | {A}, n{-caret-}, xs : Vec A n => (Foo.mySuc n, n) 
    """, """
       \import MyNat

       \func foo {A : \Type} (n : Foo.MyNatural) (xs : Vec A n) : \Sigma Foo.MyNatural Foo.MyNatural
         | {A}, Foo.myZero, xs : Vec A Foo.myZero => (Foo.mySuc Foo.myZero, Foo.myZero)
         | {A}, Foo.mySuc _x, xs : Vec A (Foo.mySuc _x) => (Foo.mySuc (Foo.mySuc _x), Foo.mySuc _x)
    """)
}