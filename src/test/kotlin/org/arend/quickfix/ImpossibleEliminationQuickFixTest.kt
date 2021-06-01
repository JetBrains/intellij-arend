package org.arend.quickfix

import org.arend.util.ArendBundle

class ImpossibleEliminationQuickFixTest: QuickFixTestBase() {
    private fun doTest(contents: String, result: String) = simpleQuickFixTest(ArendBundle.message("arend.pattern.doMatching"), contents, result)
    
    fun test68_Empty01() = doTest(ExpectedConstructorQuickFixTest.data1 + """ 
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim xs
        | (){-caret-}
    """, ExpectedConstructorQuickFixTest.data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim n, xs
      
    """)

    fun test68_Empty02() = doTest(data2 + """ 
      \func f (x y : Nat) (p : Geq x y) : Nat \elim x, p
          | x, (){-caret-}      
          | m, EqBase => zero
          | suc _, EqSuc q => suc zero        
    """, data2 + """
      \func f (x y : Nat) (p : Geq x y) : Nat \elim x, y, p
          | m, y, EqBase => zero
          | suc _, y, EqSuc q => suc zero
    """)

    fun test68_Empty03() = doTest(ExpectedConstructorQuickFixTest.data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | (){-caret-}
    """, ExpectedConstructorQuickFixTest.data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
      
    """)

    fun test68_Empty04() = doTest(ExpectedConstructorQuickFixTest.data3 + """
       \data D2
         | cons1 (c : Nat)
         | cons2 (a b : Nat) (d : D a b) \elim a, d {
           | 0, (){-caret-} => cons1 0
           | suc a, con3 => cons1 a
         } 
    """, ExpectedConstructorQuickFixTest.data3 + """
       \data D2
         | cons1 (c : Nat)
         | cons2 (a b : Nat) (d : D a b) \elim a, b, d {
           | suc a, b, con3 => cons1 a
         } 
    """)

    fun test68_Empty05() = doTest(ExpectedConstructorQuickFixTest.data3 + """
       \data D2 (a b : Nat) (d : D a b) \elim a, d
         | 0, (){-caret-} => cons1
         | suc a, con3 => cons2 (Fin a)
    """, ExpectedConstructorQuickFixTest.data3 + """
       \data D2 (a b : Nat) (d : D a b) \elim a, b, d
         | suc a, b, con3 => cons2 (Fin a) 
    """)

    private val data2 = """
      \data Geq Nat Nat \with
        | m, zero => EqBase
        | suc n, suc m => EqSuc (p : Geq n m)
    """

    fun test68_Empty01C() = doTest(ExpectedConstructorQuickFixTest.data1 + """ 
       \func test2 {A : \Type} (n : Nat) (xs : Vec A n) : Nat => \case n, xs \with {
         | n, (){-caret-} => 101
       } 
    """, ExpectedConstructorQuickFixTest.data1 + """
       \func test2 {A : \Type} (n : Nat) (xs : Vec A n) : Nat => \case n \as n, xs : Vec A n \with {
       } 
    """)

    fun test68_Empty02C() = doTest(data2 + """
      \func f (x y : Nat) (p : Geq x y) : Nat =>
        \case x, y, p \with {
          | m, zero, EqBase => zero
          | zero, suc _, (){-caret-}
          | suc _, suc _, EqSuc q => suc zero
        }
    """, data2 + """
      \func f (x y : Nat) (p : Geq x y) : Nat =>
        \case x \as x, y \as y, p : Geq x y \with {
          | m, zero, EqBase => zero
          | suc _, suc _, EqSuc q => suc zero
        }
    """)

    fun test68_Empty03C() = doTest(ExpectedConstructorQuickFixTest.data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat => 1 Nat.+ (\case xs \with {
        | (){-caret-} => 0
        | cons x xs => 1  
      })
    """, ExpectedConstructorQuickFixTest.data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat => 1 Nat.+ (\case n \as n, xs : Vec A n \with {
        | n, cons x xs => 1
      })
    """)

    fun test68_Empty04C() = doTest(ExpectedConstructorQuickFixTest.data1 + """
      \func test4 {A : \Type} {n : Nat} (xs : Vec A (n Nat.+ n)) : Nat => 1 Nat.+ (\case xs \with {
        | (){-caret-} => 0
        | cons x xs => 1
      })
    """, ExpectedConstructorQuickFixTest.data1 + """
      \func test4 {A : \Type} {n : Nat} (xs : Vec A (n Nat.+ n)) : Nat => 1 Nat.+ (\case n Nat.+ n \as n1, xs : Vec A n1 \with {
        | n1, cons x xs => 1
      })  
    """)

    fun test68_Empty05C() = doTest(ExpectedConstructorQuickFixTest.data1 + """
       \func test5 {A : \Type} {n : Nat} (xs : Vec A (n Nat.+ n Nat.+ n)) : Nat => 1 Nat.+ (\case n Nat.+ n \as n1, xs : Vec A (n1 Nat.+ n) \with {
        | 0, (){-caret-} => 0
        | suc n1, cons x xs => 1
      })
    """, ExpectedConstructorQuickFixTest.data1 + """
       \func test5 {A : \Type} {n : Nat} (xs : Vec A (n Nat.+ n Nat.+ n)) : Nat => 1 Nat.+ (\case n Nat.+ n \as n1, n Nat.+ n Nat.+ n \as n2, xs : Vec A n2 \with {       
         | suc n1, n2, cons x xs => 1
      }) 
    """)

    fun test68_NoClauses01() = doTest(ExpectedConstructorQuickFixTest.data2 + """
        {-caret-}\func f (n : Nat) (d : D n) : Nat \elim d
    """, ExpectedConstructorQuickFixTest.data2 + """
        \func f (n : Nat) (d : D n) : Nat \elim n, d          
    """)

    fun test_Bug0() = doTest("""
       \data D (n m : Nat) \with | 0, 0 => con
       \func foo{-caret-} {n m : Nat} (d : D n m) : Nat \elim d 
    """, """
       \data D (n m : Nat) \with | 0, 0 => con
       \func foo {n m : Nat} (d : D n m) : Nat \elim n, m, d
    """)


    /* TODO: Implement later

    private val data3 = """
        \data D (n : Nat) \with
          | 0 => con1
          | n => con2 
    """

    fun test68_Elim01() = doTest(data3 + """
       \func test (n : Nat) (d : D n) : Nat \elim d
         | con2{-caret-} => 0
    """, data3 + """
       \func test (n : Nat) (d : D n) : Nat \elim n, d
         | 0, con1 => {?} 
         | suc n, con2 => 0
    """)

    fun test68_Elim02() = doTest(data3 + """
       \func test (n : Nat) (d : D n) : Nat \elim n, d
         | n, con2{-caret-} => 0
    """, data3 + """
       \func test (n : Nat) (d : D n) : Nat \elim n, d
         | 0, con1 => {?} 
         | n, con2 => 0
    """)

    fun test68_Elim03() = doTest(ExpectedConstructorQuickFixTest.data3 + """
        \func foo (n m : Nat) (d : D n m) : Nat
          | 0, 0, con1 => 0
          | suc (suc n), 1, con2 => 1
          | suc (suc n), suc (suc m), con2 => 2
          | suc (suc n), suc (suc m), con3 => 3
          | n, m, con4{-caret-} => 4
    """, ExpectedConstructorQuickFixTest.data3 + """
        \func foo (n m : Nat) (d : D n m) : Nat
          | 0, 0, con1 => 0
          | suc (suc n), 1, con2 => 1
          | suc (suc n), suc (suc m), con2 => 2
          | suc (suc n), suc (suc m), con3 => 3
          | suc n, suc m, con2 => 4
          | n, m, con4 => 4          
    """) */
}