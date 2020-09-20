package org.arend.quickfix

class ExpectedConstructorQuickFixTest : QuickFixTestBase() {
    private val data1 = """
      \data Vec (A : \Type) (n : Nat) \elim n
        | 0 => nil
        | suc n => cons A (Vec A n)
    """

    private val data2 = """
      \data D (n : Nat) \with | 1 => con1 | 2 => con2 | 3 => con3 
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

    fun test69_2() = simpleQuickFixTest("Do", data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | nil{-caret-} => 0
        | cons x xs => 1
    """, data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | {_}, {0}, nil => 0
        | {_}, {suc n}, cons x xs => 1 
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
}