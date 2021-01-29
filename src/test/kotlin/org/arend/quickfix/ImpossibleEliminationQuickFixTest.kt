package org.arend.quickfix

class ImpossibleEliminationQuickFixTest: QuickFixTestBase() {
    // 0
    fun test68_Empty01() = simpleQuickFixTest("Do", ExpectedConstructorQuickFixTest.data1 + """ 
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim xs
        | (){-caret-}
    """, ExpectedConstructorQuickFixTest.data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim n, xs
    """)

    // 0
    fun test68_Empty02() = simpleQuickFixTest("Do", ExpectedConstructorQuickFixTest.data1 + """ 
       \func test2 {A : \Type} (n : Nat) (xs : Vec A n) : Nat => \case n, xs \with {
         | n, (){-caret-} => 101
       } 
    """, ExpectedConstructorQuickFixTest.data1 + """
       \func test2 {A : \Type} (n : Nat) (xs : Vec A n) : Nat => \case n \as n, xs : Vec A n \with {
       } 
    """)

    // 0
    fun test68_Empty03() = simpleQuickFixTest("Do", ExpectedConstructorQuickFixTest.data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | (){-caret-}
    """, ExpectedConstructorQuickFixTest.data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
    """)

    private val data2 = """
      \data Geq Nat Nat \with
        | m, zero => EqBase
        | suc n, suc m => EqSuc (p : Geq n m)
    """

    //0
    fun test68_Empty04() = simpleQuickFixTest("Do", data2 + """
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

    //2
    fun test68_NoClauses01() = simpleQuickFixTest("Do", ExpectedConstructorQuickFixTest.data2 + """
        {-caret-}\func f (n : Nat) (d : D n) : Nat \elim d
    """, ExpectedConstructorQuickFixTest.data2 + """
        \func f (n : Nat) (d : D n) : Nat \elim n, d          
    """)

    /* TODO: Implement later (1)

    private val data3 = """
        \data D (n : Nat) \with
          | 0 => con1
          | n => con2 
    """

    fun test68_Elim01() = simpleQuickFixTest("Do", data3 + """
       \func test (n : Nat) (d : D n) : Nat \elim d
         | con2{-caret-} => 0
    """, data3 + """
       \func test (n : Nat) (d : D n) : Nat \elim n, d
         | 0, con1 => {?} 
         | suc n, con2 => 0
    """)

    fun test68_Elim02() = simpleQuickFixTest("Do", data3 + """
       \func test (n : Nat) (d : D n) : Nat \elim n, d
         | n, con2{-caret-} => 0
    """, data3 + """
       \func test (n : Nat) (d : D n) : Nat \elim n, d
         | 0, con1 => {?} 
         | n, con2 => 0
    """)

    fun test68_Elim03() = simpleQuickFixTest("Do", ExpectedConstructorQuickFixTest.data3 + """
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