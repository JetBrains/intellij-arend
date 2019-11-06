package org.arend.intention

import org.arend.quickfix.QuickFixTestBase

class SplitAtomPatternIntentionTest: QuickFixTestBase() {
    private val pairDefinition = "\\record Pair (A B : \\Type) | fst : A | snd : B"
    private val pairDefinition2 = """
       \data Empty
       \record Pair {A : \Type} (B : \Type) | fst : A | snd : B
       \func Not (a : \Type) =>  a -> Empty 
    """
    private val pairDefinition3 = """
       \record Pair (A B : \Type) | fst : A | snd : \Sigma (b : B) (c : Nat)
       \record Pair2 (A : \Type) (B : \Type) | fst2 : Pair A B | snd2 : Pair A B 
    """

    fun testBasicSplit() = typedQuickFixTest("Split", """
       \func isLessThan2 (a : Nat) : Nat
         | 0 => 1
         | suc _{-caret-} => 1        
    """, """
       \func isLessThan2 (a : Nat) : Nat
         | 0 => 1
         | 1 => 1
         | suc (suc n) => 1 
    """)

    fun testBasicSplitWithImplicitArgs() = typedQuickFixTest("Split", """
       \func isLessThan2 {a : Nat} : Nat
         | {0} => 1
         | {suc _{-caret-}} => 1
    """, """
       \func isLessThan2 {a : Nat} : Nat
         | {0} => 1
         | {1} => 1
         | {suc (suc n)} => 1 
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
         | {A}, 0, xs : Vec A zero => (suc zero, zero)
         | {A}, suc n, xs : Vec A (suc n) => (suc (suc n), suc n) 
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
         | foo1 0 b => {?} 
         | foo1 (suc n) b => {?}
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
         | cons x (cons a l) => 1
    """)

    fun testRenaming2() = typedQuickFixTest("Split", """
       \data List (A : \Type)
         | nil
         | cons A (List A) 

       \func foo {A : \Type} (l l2 : List A) : Nat
         | nil, _ => 0
         | _, nil => 0
         | cons x xs{-caret-}, cons a l => foo xs l 
    """, """
       \data List (A : \Type)
         | nil
         | cons A (List A) 

       \func foo {A : \Type} (l l2 : List A) : Nat
         | nil, _ => 0
         | _, nil => 0
         | cons x nil, cons a l => foo nil l
         | cons x (cons a1 l1), cons a l => foo (cons a1 l1) l
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
         | :: x (:: a l) => (foo (a :: l)) Nat.+ x
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
         | :: a l => 0 
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
         | {A}, Foo.mySuc m, xs : Vec A (Foo.mySuc m) => (Foo.mySuc (Foo.mySuc m), Foo.mySuc m)
    """)

    fun testNaturalNumbers() = typedQuickFixTest("Split",
    """
       \func plus {a : Nat} (b : Nat) : Nat \with
         | {a{-caret-}}, b => 0 
    """, """
       \func plus {a : Nat} (b : Nat) : Nat \with
         | {0}, b => 0 
         | {suc n}, b => 0
    """)

    fun testElim() = typedQuickFixTest("Split",
    """
       \data Foo
         | foo Nat
          
       \func plus {a : Nat} (b : Foo) : Nat \elim b 
         | foo ((suc ((Nat.suc b{-caret-})))) => b 
    """, """
       \data Foo
         | foo Nat
         
       \func plus {a : Nat} (b : Foo) : Nat \elim b
         | foo 2 => zero
         | foo ((suc ((Nat.suc (suc n))))) => suc n 
    """)

    fun testTuple1() = typedQuickFixTest("Split",
    """
       \func test3 {A : \Type} (B : A -> \Type) (p : \Sigma (x : A) (B x)) : A \elim p
         | p{-caret-} => p.1 
    """, """
       \func test3 {A : \Type} (B : A -> \Type) (p : \Sigma (x : A) (B x)) : A \elim p
         | (a,b) => (a,b).1 
    """)

    fun testRecord1() = typedQuickFixTest("Split",
    """
       $pairDefinition
       
       \func test4 {A B : \Type} (p : Pair A B) : A \elim p
         | p{-caret-} => fst {p}         
    """, """
       $pairDefinition
       
       \func test4 {A B : \Type} (p : Pair A B) : A \elim p
         | (a,b) => fst {\new Pair A B a b} 
    """)

    fun testRecord2() = typedQuickFixTest("Split",
    """
       $pairDefinition
       
       \func test5 {A B : \Type} (p : Pair A B) : A \elim p
         | p{-caret-} : Pair => p.fst         
    """, """
       $pairDefinition
       
       \func test5 {A B : \Type} (p : Pair A B) : A \elim p
         | (a,b) => fst {\new Pair A B a b} 
    """)

    fun testRecord3() = typedQuickFixTest("Split",
    """
       $pairDefinition2
       
       \func test6 {A B : \Type} (p : Pair {Not A} (Not B)) : A -> Empty \elim p
         | p{-caret-} : Pair => p.fst         
    """, """
       $pairDefinition2
       
       \func test6 {A B : \Type} (p : Pair {Not A} (Not B)) : A -> Empty \elim p
         | (a,b) => fst {\new Pair {Not A} (Not B) a b} 
    """)

    fun testRecord4() = typedQuickFixTest("Split",
    """
       \module Foo \where \record Pair (A : \Type) (B : \Type) | fst : A | snd : B

       \func test7 {A B : \Type} (p : Foo.Pair A B) : A \elim p
         | p{-caret-} : Foo.Pair => p.fst 
    """, """
       \module Foo \where \record Pair (A : \Type) (B : \Type) | fst : A | snd : B

       \func test7 {A B : \Type} (p : Foo.Pair A B) : A \elim p
         | (a,b) => Foo.fst {\new Foo.Pair A B a b} 
    """)

    fun testRecord5() = typedQuickFixTest("Split",
    """
       $pairDefinition3
       \func test8 {A B : \Type} (p : Pair2 A B) : A \elim p
         | p{-caret-} : Pair2 => p.fst2.fst         
    """, """
       $pairDefinition3
       \func test8 {A B : \Type} (p : Pair2 A B) : A \elim p
         | (p,p1) => fst {fst2 {\new Pair2 A B p p1}}
    """)

    fun testRecord6() = typedQuickFixTest("Split", """
       $pairDefinition3 
       \func test9 (p : Pair2) : Nat \elim p
         | p{-caret-} : Pair2 => p.snd2.snd.2
    """, """
       $pairDefinition3 
       \func test9 (p : Pair2) : Nat \elim p
         | (p,p1) => (snd {snd2 {\new Pair2 p p1}}).2  
    """)
}