package org.arend.quickfix

class ExpectedConstructorQuickFixTest : QuickFixTestBase() {
    private val data1 = """
      \data Vec (A : \Type) (n : Nat) \elim n
        | 0 => nil
        | suc n => cons A (Vec A n)
    """

    private val data1S = """
      \data Vec' (b : \Sigma \Type Nat) \elim b
       | (A, 0) => nil2
       | (A, suc n) => cons2 A (Vec' (A, n)) 
    """

    private val data2 = """
      \data D (n : Nat) \with | 1 => con1 | 2 => con2 | 3 => con3 
    """

    private val data3 = """
      \data D (n m : Nat) \with
        | 0, 0 => con1
        | suc _, suc _ => con2
        | suc (suc _), suc (suc _) => con3
        | _, _ => con4
    """

    fun test69_1() = simpleQuickFixTest("Do", data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim xs
        | nil{-caret-} => 0
        | cons x xs => 1 
    """, data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim n, xs
        | 0, nil => 0
        | suc n, cons x xs => 1
    """)

    fun test68_1() = simpleQuickFixTest("Do", data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim xs
        | (){-caret-} 
    """, data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim n, xs
        | 0, nil => {?}
        | suc n, cons a xs => {?} 
    """)

    fun test68_1b() = simpleQuickFixTest("Do", data1 + """
       \func test2 {A : \Type} (n : Nat) (xs : Vec A n) : Nat => \case n, xs \with {
         | n, (){-caret-} => 101
       } 
    """, data1 + """
       \func test2 {A : \Type} (n : Nat) (xs : Vec A n) : Nat => \case n, xs \with {
         | 0, nil => 101
         | suc n, cons x xs => 101
       } 
    """)

    fun test69_2() = simpleQuickFixTest("Do", data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | nil{-caret-} => 0
        | cons x xs => 1
    """, data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | {_}, {0}, nil => 0
        | {_}, {suc n}, cons x xs => 1 
    """)

    fun test69_2b() = simpleQuickFixTest("Do", data1S + """
      \func foo' (A : \Type) (n : Nat) (xs : Vec' (A, n)) \elim xs
        | nil2{-caret-} => 0
        | cons2 x xs => 1 
    """, data1S + """
      \func foo' (A : \Type) (n : Nat) (xs : Vec' (A, n)) \elim n, xs
        | 0, nil2 => 0
        | suc n, cons2 x xs => 1  
    """)

    fun test69_2c() = simpleQuickFixTest("Do", data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat => 1 Nat.+ (\case xs \with {
        | nil{-caret-} => 0
        | cons x xs => 1  
      })
    """, data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat => 1 Nat.+ (\case n, xs \with { 
        | 0, nil => 0
        | suc n, cons x xs => 1
      })
    """)

    fun test68_2() = simpleQuickFixTest("Do", data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | (){-caret-}
    """, data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | {_}, {0}, nil => {?}
        | {_}, {suc n}, cons a xs => {?}
    """)

    fun test69_3() = simpleQuickFixTest("Do", data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim d
        | con1{-caret-} => 101 
    """, data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim n, d
    """)

    fun test69_4() = simpleQuickFixTest("Do", data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim n, d
        | n, con2{-caret-} => 101 
    """, data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim n, d
        | 0, con2 => 101
    """)

    fun test69_5() = simpleQuickFixTest("Do", data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim d
        | con2{-caret-} => 101 
    """, data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim n, d
        | 0, con2 => 101
    """)

    fun test68_3() = simpleQuickFixTest("Do", data3 + """
        \func foo (n m : Nat) (d : D n m) : Nat
          | 0, 0, con1 => 0
          | suc (suc n), 1, con2 => 1
          | suc (suc n), suc (suc m), con2 => 2
          | suc (suc n), suc (suc m), con3 => 3
          | {-caret-}n, m, con4 => 4
    """, data3 + """
        \func foo (n m : Nat) (d : D n m) : Nat
          | 0, 0, con1 => 0
          | suc (suc n), 1, con2 => 1
          | suc (suc n), suc (suc m), con2 => 2
          | suc (suc n), suc (suc m), con3 => 3
          | n, 0, con4 => 4
          | n, suc m, con4 => 4
    """)
}