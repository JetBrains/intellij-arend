package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

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

    private fun doTest(contents: String, result: String) = typedQuickFixTest(ArendBundle.message("arend.pattern.split"), contents, result)

    fun testBasicSplit() = doTest("""
       \func isLessThan2 (a : Nat) : Nat
         | 0 => 1
         | suc _{-caret-} => 1        
    """, """
       \func isLessThan2 (a : Nat) : Nat
         | 0 => 1
         | 1 => 1
         | suc (suc n) => 1 
    """)

    fun testBasicSplitElim() = doTest("""
       \func isLessThan2 (a b : Nat) : Nat \elim b
         | 0 => 1
         | suc _{-caret-} => 1        
    """, """
       \func isLessThan2 (a b : Nat) : Nat \elim b
         | 0 => 1
         | 1 => 1
         | suc (suc n) => 1 
    """)

    fun testBasicSplitWithImplicitArgs() = doTest("""
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

    fun testSplitEmptyPattern1() = doTest("""
       \data Empty 
        
       \func foobar (e : Empty) : Nat
         | e{-caret-} => {?} 
    """, """
       \data Empty
        
       \func foobar (e : Empty) : Nat
         | () 
    """)

    fun testSplitEmptyPattern2() = doTest("""
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

    fun testVariableReplacement() = doTest("""
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
         | foo1 (suc a) b => {?}
    """)

    fun testRenaming() = doTest("""
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
         | cons x (cons a xs) => 1
    """)

    fun testRenaming2() = doTest("""
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
         | cons x (cons a1 xs), cons a l => foo (cons a1 xs) l
    """)

    fun testInfixNotation() = doTest("""
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
         | :: x (a :: xs) => (foo (a :: xs)) Nat.+ x
    """)

    fun testSimpleResolving() = doTest("""
       -- ! A.ard
       \data List (A : \Type)
         | nil
         | \infixr 5 :: A (List A)
  
       -- ! Main.ard
       \import A (List)
       
       \func foo (xs : List Nat) : Nat
         | xs{-caret-} => 0 
    """, """
      \import A (List)
      
      \func foo (xs : List Nat) : Nat
        | List.nil => 0
        | a List.:: xs => 0  
    """)

    fun testLongName() = doTest("""
       -- ! MyNat.ard
       \module Foo \where { 
         \data MyNatural
           | myZero
           | mySuc (MyNatural)
       } 
        
       \open Foo 
       
       \data Vec (A : \Type) (n : MyNatural) \elim n
         | myZero => nil
         | mySuc n => cons A (Vec A n) 
         
       -- ! Main.ard
       \import MyNat
       
       \func foo {A : \Type} (n : Foo.MyNatural) (xs : Vec A n) : \Sigma Foo.MyNatural Foo.MyNatural
         | {A}, n{-caret-}, xs : Vec A n => (Foo.mySuc n, n) 
    """, """
       \import MyNat

       \func foo {A : \Type} (n : Foo.MyNatural) (xs : Vec A n) : \Sigma Foo.MyNatural Foo.MyNatural
         | {A}, Foo.myZero, xs : Vec A Foo.myZero => (Foo.mySuc Foo.myZero, Foo.myZero)
         | {A}, Foo.mySuc n, xs : Vec A (Foo.mySuc n) => (Foo.mySuc (Foo.mySuc n), Foo.mySuc n)
    """)

    fun testNaturalNumbers() = typedQuickFixTest("Split",
    """
       \func plus {a : Nat} (b : Nat) : Nat \with
         | {a{-caret-}}, b => 0 
    """, """
       \func plus {a : Nat} (b : Nat) : Nat \with
         | {0}, b => 0 
         | {suc a}, b => 0
    """)

    fun testNaturalNumbers2() = doTest("""
       \func foo (p : \Sigma Nat Nat) : Nat
         | (x{-caret-}, y) => {?} 
    """, """
       \func foo (p : \Sigma Nat Nat) : Nat
         | (0, y) => {?} 
         | (suc x, y) => {?}
    """)

    fun testFinZero() = doTest("""
       \func foo (n : Nat) (s : Fin n) : Nat
         | suc n, s{-caret-} => 0 
    """, """
       \func foo (n : Nat) (s : Fin n) : Nat
         | suc n, 0 => 0
         | suc n, suc s => 0
    """)

    fun testFinSuc() = doTest("""
       \func foo (s : Fin 2) : Nat
         | 0 => 0
         | suc s{-caret-} => s
    """, """
       \func foo (s : Fin 2) : Nat
         | 0 => 0
         | 1 => zero
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
         | foo ((2)) => zero
         | foo ((suc ((Nat.suc (suc b))))) => suc b 
    """)

    fun testTuple1() = typedQuickFixTest("Split",
    """
       \func test3 {A : \Type} (B : A -> \Type) (p : \Sigma (x : A) (B x)) : A \elim p
         | p{-caret-} => p.1 
    """, """
       \func test3 {A : \Type} (B : A -> \Type) (p : \Sigma (x : A) (B x)) : A \elim p
         | (x,b) => (x,b).1 
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
         | (a,b) : Pair => fst {\new Pair A B a b} 
    """)

    fun testRecord3() = typedQuickFixTest("Split",
    """
       $pairDefinition2
       
       \func test6 {A B : \Type} (p : Pair {Not A} (Not B)) : A -> Empty \elim p
         | p{-caret-} : Pair => p.fst         
    """, """
       $pairDefinition2
       
       \func test6 {A B : \Type} (p : Pair {Not A} (Not B)) : A -> Empty \elim p
         | (a,b) : Pair => fst {\new Pair {Not A} (Not B) a b} 
    """)

    fun testRecord4() = typedQuickFixTest("Split",
    """
       \module Foo \where \record Pair (A : \Type) (B : \Type) | fst : A | snd : B

       \func test7 {A B : \Type} (p : Foo.Pair A B) : A \elim p
         | p{-caret-} : Foo.Pair => p.fst 
    """, """
       \module Foo \where \record Pair (A : \Type) (B : \Type) | fst : A | snd : B

       \func test7 {A B : \Type} (p : Foo.Pair A B) : A \elim p
         | (a,b) : Foo.Pair => Foo.fst {\new Foo.Pair A B a b} 
    """)

    fun testRecord5() = typedQuickFixTest("Split",
    """
       $pairDefinition3
       \func test8 {A B : \Type} (p : Pair2 A B) : A \elim p
         | p{-caret-} : Pair2 => p.fst2.fst         
    """, """
       $pairDefinition3
       \func test8 {A B : \Type} (p : Pair2 A B) : A \elim p
         | (p,p1) : Pair2 => fst {fst2 {\new Pair2 A B p p1}}
    """)

    fun testRecord6() = doTest("""
       $pairDefinition3 
       \func test9 (p : Pair2) : Nat \elim p
         | p{-caret-} : Pair2 => p.snd2.snd.2
    """, """
       $pairDefinition3 
       \func test9 (p : Pair2) : Nat \elim p
         | (A,B,p,p1) : Pair2 => (snd {snd2 {\new Pair2 A B p p1}}).2
    """)

    fun testCase() = doTest("""
       \open Nat

       \func foo : Nat => \case 1 + 2 \with {  
         | 0 => {?}
         | suc n{-caret-} => {?}
       } 
    """, """
       \open Nat

       \func foo : Nat => \case 1 + 2 \with {  
         | 0 => {?}
         | 1 => {?}
         | suc (suc n) => {?}
       }        
    """)

    fun test_86_1() = typedCheckNoQuickFixes("Split", """
       \func foo (x y : Nat) (p : x = y) : Nat => \case x, p \with {
         | n, p{-caret-} => {?}
       }
    """)

     fun test_86_2() = doTest("""
        \func bar (x y : Nat) (p : x = y) : Nat => \case \elim x, p \with {
          | n, q{-caret-} => {?}
        }
     """, """
        \func bar (x y : Nat) (p : x = y) : Nat => \case \elim x, p \with {
          | n, idp => {?}
        }
     """)

    fun test_86_3() = doTest("""
       \data Foo
         | foo (x y : Nat) (p : x = y)

       \func bar (f : Foo) : Nat => \case f \with {
         | foo x y p{-caret-} => {?}
       } 
    """, """
       \data Foo
         | foo (x y : Nat) (p : x = y)

       \func bar (f : Foo) : Nat => \case f \with {
         | foo x y idp => {?}
       } 
    """)

    fun test_86_4() = doTest("""
       \func foo (x y : Nat) (p : x = y) : Nat \elim x, p
         | n, p{-caret-} => {?} 
    """, """
       \func foo (x y : Nat) (p : x = y) : Nat \elim x, p
         | n, idp => {?} 
    """)

    fun test_86_5() = typedCheckNoQuickFixes("Split", """
       \func foo (x : Nat) (p : x = x) : Nat \elim x, p
         | x, p{-caret-} => {?}
    """)

    fun test_88_1() = doTest("""
       \data D | con (t s : D)
       
       \func foo (t : D) : Nat
         | t{-caret-} => {?}
    """, """
       \data D | con (t s : D)
       
       \func foo (t : D) : Nat
         | con t t1 => {?} 
    """)

    fun test_225() = doTest("""
       \lemma foo (n : Nat) : n = n
         | 0 => {?}
         | suc n{-caret-} => {?} 
    """, """
       \lemma foo (n : Nat) : n = n
         | 0 => {?}
         | 1 => {?}
         | suc (suc n) => {?} 
    """)

    fun test_type() = doTest("""
       \data D | con1 | con2
       \type T => D
       \func foo (t : T) : Nat
         | t{-caret-} => 0 
    """, """
       \data D | con1 | con2
       \type T => D
       \func foo (t : T) : Nat
         | con1 => 0
         | con2 => 0 
    """)

    fun test_arrays() = doTest("""
       \func foo (a : Array Nat) : Nat
         | a{-caret-} => {?} 
    """, """
       \func foo (a : Array Nat) : Nat
         | nil => {?}
         | a :: a1 => {?} 
    """)

    fun test_arrays2() = doTest("""
       \func foo {n : Nat} (x : Array Nat (suc n)) : Nat
         | a{-caret-} => {?} 
    """, """
       \func foo {n : Nat} (x : Array Nat (suc n)) : Nat
         | a :: a1 => {?} 
    """)

    fun test_arrays3() = doTest("""
       \func foo (x : Array Nat 0) : Nat
         | a{-caret-} => {?} 
    """, """
       \func foo (x : Array Nat 0) : Nat
         | nil => {?}
    """)

   fun test272() = typedQuickFixTest("Split", """
      \data \infixl 2 union (A B : \Type)
        | inj1 (a : A)
        | inj2 (b : B)

      \record Map {A B : \Type}
        | f : A -> B

      \func u-assoc' {A B C : \Type} : Map {union (union A B) C} {union A (union B C)} \cowith
        | f (w : union (union A B) C) : union A (union B C) \with {
          | inj1 a{-caret-} => {?}
          | inj2 b => {?}
        }
   """, """
      \data \infixl 2 union (A B : \Type)
        | inj1 (a : A)
        | inj2 (b : B)

      \record Map {A B : \Type}
        | f : A -> B

      \func u-assoc' {A B C : \Type} : Map {union (union A B) C} {union A (union B C)} \cowith
        | f (w : union (union A B) C) : union A (union B C) \with {
          | inj1 (inj1 a) => {?}
          | inj1 (inj2 b) => {?}
          | inj2 b => {?}
        }
   """)

    fun testInfix() = typedQuickFixTest("Split", """
      \data List | nil | \infixr 3 :: Nat List

      \func f (a : List) : Nat
        | a :: b{-caret-}b :: xs => 1
        | _ => 0
   """, """
      \data List | nil | \infixr 3 :: Nat List

      \func f (a : List) : Nat
        | a :: 0 :: xs => 1
        | a :: suc bb :: xs => 1
        | _ => 0
   """)

    fun testInfix2() = typedQuickFixTest("Split", """
      \data List | nil | \infixr 3 :: Nat List

      \func f (a : List) : Nat
        | {-caret-}a => 1
   """, """
      \data List | nil | \infixr 3 :: Nat List

      \func f (a : List) : Nat
        | nil => 1
        | n :: a => 1
   """)

    fun testInfix3() = typedQuickFixTest("Split", """  
    \data List | nill | \infixr 9 :: List Nat
    
    \func f (a b : List) : Nat \elim a
      | nill => 0
      | {-caret-}e :: a => 0
   """, """
    \data List | nill | \infixr 9 :: List Nat
    
    \func f (a b : List) : Nat \elim a
      | nill => 0
      | nill :: a => 0
      | (e :: n) :: a => 0
   """)

    fun testInfix4() = typedQuickFixTest("Split", """  
    \data List | nill | \infixl 9 :: List Nat
    
    \func f (a b : List) : Nat \elim a
      | nill => 0
      | {-caret-}e :: a => 0
   """, """
    \data List | nill | \infixl 9 :: List Nat
    
    \func f (a b : List) : Nat \elim a
      | nill => 0
      | nill :: a => 0
      | e :: n :: a => 0
   """)

    fun testInfix5() = typedQuickFixTest("Split", """  
    \data List | nill | \infixr 9 :: Nat List
    
    \func f (a b : List) : Nat \elim a
      | nill => 0
      | e :: {-caret-}a => 0
   """, """
    \data List | nill | \infixr 9 :: Nat List
    
    \func f (a b : List) : Nat \elim a
      | nill => 0
      | e :: nill => 0
      | e :: n :: a => 0
   """)

    fun testInfix6() = typedQuickFixTest("Split", """  
    \data List | nill | \infixl 9 :: Nat List 
    
    \func f (a b : List) : Nat \elim a
      | nill => 0
      | e :: {-caret-}a => 0
   """, """
    \data List | nill | \infixl 9 :: Nat List
    
    \func f (a b : List) : Nat \elim a
      | nill => 0
      | e :: nill => 0
      | e :: (n :: a) => 0
   """)

    fun testInfix7() = typedQuickFixTest("Split", """  
    \data List | nill | \infixr 9 :: Nat List 
    
    \func f (a b : List) : Nat \elim a
      | nill => 0
      | e :: e1 :: {-caret-}a => 0
   """, """
    \data List | nill | \infixr 9 :: Nat List
    
    \func f (a b : List) : Nat \elim a
      | nill => 0
      | e :: e1 :: nill => 0
      | e :: e1 :: n :: a => 0
   """)

}