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

    /* fun test68_1() = simpleQuickFixTest("Do", data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim xs
        | (){-caret-} 
    """, data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim n, xs
        | 0, nil => {?}
        | suc n, cons a xs => {?} 
    """)

    fun test68_2() = simpleQuickFixTest("Do", data1 + """
       \func test2 {A : \Type} (n : Nat) (xs : Vec A n) : Nat => \case n, xs \with {
         | n, (){-caret-} => 101
       } 
    """, data1 + """
       \func test2 {A : \Type} (n : Nat) (xs : Vec A n) : Nat => \case n, xs \with {
         | 0, nil => 101
         | suc n, cons x xs => 101
       } 
    """)

    fun test68_3() = simpleQuickFixTest("Do", data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | (){-caret-}
    """, data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | {_}, {0}, nil => {?}
        | {_}, {suc n}, cons a xs => {?}
    """)

    fun test68_4() = simpleQuickFixTest("Do", data3 + """
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

    fun test68_5() = simpleQuickFixTest("Do", data2 + """
        {-caret-}\func f (n : Nat) (d : D n) : Nat \elim d
    """, data2 + """
        \func f (n : Nat) (d : D n) : Nat \elim n, d
    """)

    /* Expected Constructor - Case */

    fun test69C_01() = simpleQuickFixTest("Do", data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat => 1 Nat.+ (\case xs \with {
        | nil{-caret-} => 0
        | cons x xs => 1  
      })
    """, data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat => 1 Nat.+ (\case n, xs \with { 
        | 0, nil => 0
        | suc n, cons x xs => 1
      })
    """) */

    /* Expected Constructor Error */

    fun test69_01() = simpleQuickFixTest("Do", data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim xs
        | nil{-caret-} => 0
        | cons x xs => 1 
    """, data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim n, xs
        | 0, nil => 0
        | suc n, cons x xs => 1
    """)

    fun test69_02() = simpleQuickFixTest("Do", data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | nil{-caret-} => 0
        | cons x xs => 1
    """, data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | {_}, {0}, nil => 0
        | {_}, {suc n}, cons x xs => 1 
    """)

    fun test69_03() = simpleQuickFixTest("Do", data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim n, xs
        | n, nil{-caret-} => n
        | n, cons x xs => n
    """, data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim n, xs
        | 0, nil => 0
        | suc n, cons x xs => suc n 
    """)

    fun test69_04() = simpleQuickFixTest("Do", data1S + """
      \func foo' (A : \Type) (n : Nat) (xs : Vec' (A, n)) : Nat \elim xs
        | nil2{-caret-} => 0
        | cons2 x xs => 1 
    """, data1S + """
      \func foo' (A : \Type) (n : Nat) (xs : Vec' (A, n)) : Nat \elim n, xs
        | 0, nil2 => 0
        | suc n, cons2 x xs => 1  
    """)

    fun test69_05() = simpleQuickFixTest("Do", data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim d
        | con1{-caret-} => 101
    """, data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim d
        | con1{-caret-} => 101
    """)

    fun test69_06() = simpleQuickFixTest("Do", data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim n, d
        | n, con2{-caret-} => 101 
    """, data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim n, d
        | 0, con2 => 101
    """)

    fun test69_07() = simpleQuickFixTest("Do", data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim d
        | con2{-caret-} => 101 
    """, data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim n, d
        | 0, con2 => 101
    """)

    fun test69_08() = simpleQuickFixTest("Do", data2 + """
       \func foo {n : Nat} (d : D n) : Nat \with
         | {suc n'}, con1{-caret-} => n'  
    """, data2 + """
       \func foo {n : Nat} (d : D n) : Nat \with
         | {1}, con1 => 0
    """)

    fun test69_09() = simpleQuickFixTest("Do", data3 + """
       \func foo (a b : Nat) (d : D a b) : Nat \elim a, d
         | 0, con1{-caret-} => 1
         | suc a, con2 => 2
    """, data3 + """
       \func foo (a b : Nat) (d : D a b) : Nat \elim a, b, d
         | 0, 0, con1{-caret-} => 1
         | suc a, suc b, con2 => 2
    """)

    fun test69_10() = simpleQuickFixTest("Do", data1 + """
       \func test {A : \Type} (n : Nat) (xs : Vec A n) : Nat \elim n, xs
         | suc n, nil{-caret-} => n
         | n, cons x xs => n 
    """, data1 + """
       \func test {A : \Type} (n : Nat) (xs : Vec A n) : Nat \elim n, xs
         | 0, nil => {?n}
         | suc n, cons x xs => suc n 
    """)

    private val data4 = data3 + """
      
      \data Container (a b1 : Nat)
        | envelope (D a b1) 
    """

    fun test69_11() = simpleQuickFixTest("Do", data4 + """
       \func foo (a b : Nat) (d : Container a b) : Nat \elim a, d
         | 0, envelope con1{-caret-} => 1
         | suc a, envelope con2 => 2
    """, data4 + """
       \func foo (a b : Nat) (d : Container a b) : Nat \elim a, b, d
         | 0, 0, envelope con1{-caret-} => 1
         | suc a, suc b, envelope con2 => 2
    """)

    fun test69_12() = simpleQuickFixTest("Do", data1 + """
       \func foo {A : \Type} (p : \Sigma (n : Nat) (Vec A n)) : Nat \elim p
         | (n, nil{-caret-}) => n
         | (n, cons a v) => n 
    """, data1 + """
       \func foo {A : \Type} (p : \Sigma (n : Nat) (Vec A n)) : Nat \elim p
         | (0, nil) => 0
         | (suc n, cons a v) => suc n 
    """)

    private val data5 = data1 + """
      \data Index (A : \Type) 
        | index {n : Nat} (v : Vec A n)  
    """

    fun test69_13() = simpleQuickFixTest("Do", data5 + """
       \func foo {A : \Type} (p : Index A) : Nat \elim p
         | index nil{-caret-} => {?}
         | index (cons a v) => {?} 
    """, data5 + """
       \func foo {A : \Type} (p : Index A) : Nat \elim p
         | index {0} nil => {?}
         | index {suc n} (cons a v) => {?} 
    """)

    private val data6 = """ 
      \data Id (A : \Type)
        | env {a : A}
        
      \data Vec (A : \Type) (n : Id Nat) \elim n
        | env {0} => nil
        | env {suc n} => cons A (Vec A (env {_} {n}))  
    """

    fun test69_14() = simpleQuickFixTest("Do", data6 + """
       \func foo {A : \Type} {e : Id Nat} (p : Vec A e) : Nat
         | {_}, {env}, nil{-caret-} => {?}
         | cons a v => {?}
    """, data6 + """
       \func foo {A : \Type} {e : Id Nat} (p : Vec A e) : Nat
         | {_}, {env {0}}, nil => {?}
         | {_}, {env {suc n}}, cons a v => {?}
    """)

    private val data7 = """
       \data Vec (A : \Type) (m n k : Nat) \elim m, n, k
         | 0, n, 0 => nil
         | suc m, n, suc k => cons A (Vec A m n k)

       \data Index (A : \Type)
         | index {m n k : Nat}  (v : Vec A m n k) 
    """

    fun test69_15() = simpleQuickFixTest("Do", data7 + """
       \func foo {A : \Type} (p : Index A) : Nat \elim p
         | index nil{-caret-} => {?}
         | index (cons a v) => {?} 
    """, data7 + """
       \func foo {A : \Type} (p : Index A) : Nat \elim p
         | index {0} {_} {0} nil => {?}
         | index {suc n} {_} {suc m} (cons a v) => {?} 
    """)

    private val data8 = """
       \data D1
         | env {a b c : Nat}

       \data Vec (A : \Type) (i : D1) \elim i
         | env {0} {n} {0} => nil
         | env {suc m} {n} {suc k} => cons A (Vec A (env {m} {n} {k}))

       \data D2 (A : \Type) (i : D1)
         | index (v : Vec A i)
    """

    fun test69_16() = simpleQuickFixTest("Do", data8 + """
       \func foo {A : \Type} (i : D1) (p : D2 A i) : Nat \elim i, p
         | env, index nil{-caret-} => {?}
         | env, index (cons a v) => {?}
    """, data8 + """
       \func foo {A : \Type} (i : D1) (p : D2 A i) : Nat \elim i, p
         | env {0} {_} {0}, index nil{-caret-} => {?}
         | env {suc m} {_} {suc k}, index (cons a v) => {?}
    """)

    private val data9 = """
       \data Vec (A : \Type) (n : \Sigma Nat Nat) \elim n
         | (0, m) => nil
         | (suc n, m) => cons A (Vec A (n, m))
    """

    fun test69_17() = simpleQuickFixTest("Do", data9 + """
       \func test {A : \Type} (n : \Sigma Nat Nat) (xs : Vec A n) : Nat \elim n, xs
         | (n, m), nil => n
         | (n, m), cons{-caret-} x xs => n 
    """, data9 + """
       \func test {A : \Type} (n : \Sigma Nat Nat) (xs : Vec A n) : Nat \elim n, xs
         | (0, m), nil => 0
         | (suc n, m), cons x xs => suc n 
    """)

    private val data10 = """
       \data Foo (a : \Sigma Nat Nat) \elim a
         | (0, 0) => cons  
    """
    fun test69_18() = simpleQuickFixTest("Do", data10 + """
       \func foo2 (a : \Sigma Nat Nat) (f : Foo a) : \Sigma Nat Nat \elim f
         | cons{-caret-} => a 
    """, data10 + """
       \func foo2 (a : \Sigma Nat Nat) (f : Foo a) : \Sigma Nat Nat \elim a, f
         | (0, 0) \as a, cons => a
    """)

    fun test69_19() = simpleQuickFixTest("Do", data10 + """
       \func foo (a : \Sigma Nat Nat) (f : Foo a) : \Sigma Nat Nat \elim a, f
         | a \as b, cons{-caret-} => b
    """, data10 + """
       \func foo (a : \Sigma Nat Nat) (f : Foo a) : \Sigma Nat Nat \elim a, f
         | (0, 0) \as b, cons => b
    """)

    private val data11 = """
       \class C {
         | a : Nat
         | b : Nat
       }

       \data Foo (c : C) \elim c
         | (0, 0) => cons 
    """

    fun test69_20() = simpleQuickFixTest("Do", data11 + """
       \func foo {c : C} (f : Foo c): Nat \elim f
         | cons{-caret-} => c.a 
    """, data11 + """
       \func foo {c : C} (f : Foo c): Nat \elim c, f
         | (0, 0) \as c : C, cons => c.a
    """)
}