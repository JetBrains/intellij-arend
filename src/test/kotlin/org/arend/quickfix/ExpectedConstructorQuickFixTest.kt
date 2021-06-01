package org.arend.quickfix

import org.arend.util.ArendBundle

class ExpectedConstructorQuickFixTest : QuickFixTestBase() {
    private fun doTest(contents: String, result: String) = simpleQuickFixTest(ArendBundle.message("arend.pattern.doMatching"), contents, result)

    private val data1S = """
      \data Vec' (b : \Sigma \Type Nat) \elim b
       | (A, 0) => nil2
       | (A, suc n) => cons2 A (Vec' (A, n)) 
    """

    /* Expected Constructor - Case */

    fun test69C_01() = doTest(data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat => 1 Nat.+ (\case xs \with {
        | nil{-caret-} => 0
        | cons x xs => 1  
      })
    """, data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat => 1 Nat.+ (\case n \as n, xs : Vec A n \with {
        | 0, nil => 0
        | suc n, cons x xs => 1
      })
    """)

    fun test69C_02() = doTest(data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat => 1 Nat.+ (\case n, xs \with {
        | n, nil{-caret-} => 0
        | n, cons x xs => 1
      }) 
    """, data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat => 1 Nat.+ (\case n \as n, xs : Vec A n \with {
        | 0, nil => 0
        | suc n, cons x xs => 1
      })
    """)

    fun test69C_03() = doTest(data3 + """
      \func test3 {n m : Nat} (d : D n m) : Nat => 1 Nat.+ (\case n, d \with {
        | n, con1{-caret-} => n
        | suc n, con3 => n
      })
    """, data3 + """
      \func test3 {n m : Nat} (d : D n m) : Nat => 1 Nat.+ (\case n \as n, m \as m, d : D n m \with {
        | 0, 0, con1{-caret-} => 0
        | suc (suc n), suc (suc m), con3 => suc n
      })
    """)

    fun test69C_04() = doTest(data1 + """
      \func test4 {A : \Type} {n : Nat} (xs : Vec A (n Nat.+ n)) : Nat => 1 Nat.+ (\case xs \with {
        | nil{-caret-} => 0
        | cons x xs => 1
      })
    """, data1 + """
      \func test4 {A : \Type} {n : Nat} (xs : Vec A (n Nat.+ n)) : Nat => 1 Nat.+ (\case n Nat.+ n \as n1, xs : Vec A n1 \with {
        | 0, nil => 0
        | suc n1, cons x xs => 1
      })  
    """)

    fun test69C_05() = doTest(data1 + """
      \func test5 {A : \Type} {n : Nat} (xs : Vec A (n Nat.+ n Nat.+ n)) : Nat => 1 Nat.+ (\case n Nat.+ n \as n1, xs : Vec A (n1 Nat.+ n) \with {
        | 0, nil{-caret-} => 0
        | suc n1, cons x xs => 1
      })
    """, data1 + """
      \func test5 {A : \Type} {n : Nat} (xs : Vec A (n Nat.+ n Nat.+ n)) : Nat => 1 Nat.+ (\case n Nat.+ n \as n1, n Nat.+ n Nat.+ n \as n2, xs : Vec A n2 \with {
        | 0, 0, nil{-caret-} => 0
        | suc n1, n2, cons x xs => 1
      })
    """)

    fun test69C_06() = doTest(data1 + """
       \func test2 {A : \Type} {n m : Nat} (xs : Vec A n) (ys : Vec A m) : Nat => 1 Nat.+ (\case n \as n, xs : Vec A n, ys \with {
         | 0, nil, nil{-caret-} => 0
         | n, cons x xs, cons y ys => 1
       }) 
    """, data1 + """
       \func test2 {A : \Type} {n m : Nat} (xs : Vec A n) (ys : Vec A m) : Nat => 1 Nat.+ (\case n \as n, xs : Vec A n, m \as m, ys : Vec A m \with {
         | 0, nil, 0, nil => 0
         | suc n, cons x xs, m, cons y ys => 1
       }) 
    """)

    val data12 = data1 + """
      \data Foo {A : \Type} (n : Nat) (v : Vec A n) \elim n, v
        | 0, nil => foo1
        | suc n, cons x xs => foo2
    """

    fun test69C_07() = doTest(data12 + """
       \func test {A : \Type} (n : Nat) (v : Vec A n) (f : Foo n v) : Nat => \case f \with {
         | foo1{-caret-} => 1
         | foo2 => 2
       }
    """, data12 + """
       \func test {A : \Type} (n : Nat) (v : Vec A n) (f : Foo n v) : Nat => \case n \as n, v \as v, f : Foo {A} n v \with {
         | 0, nil, foo1 => 1
         | suc n, cons x v, foo2 => 2
       }""")

    fun test69C_08() = doTest(data12 + """
       \func test {A : \Type} (n : Nat) (v : Vec A n) (f : Foo n v) : Nat => \case n, f : Foo n v \with {
         | 0, foo1{-caret-} => 1
         | suc n, foo2 => 2
       }
    """, data12 + """
       \func test {A : \Type} (n : Nat) (v : Vec A n) (f : Foo n v) : Nat => \case n \as n, v \as v, f : Foo {A} n v \with {
         | 0, nil, foo1 => 1
         | suc n, cons x v, foo2 => 2
       }
    """)

    fun test69C_09() = doTest(data12 + """
       \func test {A : \Type} (n : Nat) (v : Vec A n) (f : Foo n v) : Nat => \case v, f : Foo n v \with {
         | nil{-caret-}, foo1 => 1
         | cons x xs, foo2 => 2
       }
    """, data12 + """
       \func test {A : \Type} (n : Nat) (v : Vec A n) (f : Foo n v) : Nat => \case n \as n, v : Vec A n, f : Foo n v \with {
         | 0, nil{-caret-}, foo1 => 1
         | suc n, cons x xs, foo2 => 2
       }
    """)

    fun test69C_10() = doTest(data12 + """
       \func test {A : \Type} (n : Nat) (v : Vec A n) (f : Foo n v) : Nat => \case n \as n, v : Vec A n, f : Foo n v \with {
         | 0, nil, foo1{-caret-} => 1
         | suc n, cons x xs, foo2 => 2
       } 
    """, data12 + """
       \func test {A : \Type} (n : Nat) (v : Vec A n) (f : Foo n v) : Nat => \case n \as n, v \as v : Vec A n, f : Foo {A} n v \with {
         | 0, nil, foo1 => 1
         | suc n, cons x xs, foo2 => 2
       }
    """)

    fun test69C_11() = doTest(data12 + """
       \func test {A : \Type} (n : Nat) (v : Vec A n) (f : Foo n v) : Nat => \case n \as n, v \as v, f : Foo n v \with {
         | suc m, nil{-caret-}, foo1 => m
         | suc n, cons x xs, foo2 => 2
       }
    """, data12 + """
       \func test {A : \Type} (n : Nat) (v : Vec A n) (f : Foo n v) : Nat => \case n \as n, v \as v : Vec A n, f : Foo n v \with {
         | 0, nil, foo1 => {?m}
         | suc n, cons x xs, foo2 => 2
       } 
    """)

    /* Expected Constructor Error */

    fun test69_01() = doTest(data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim xs
        | nil{-caret-} => 0
        | cons x xs => 1 
    """, data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim n, xs
        | 0, nil => 0
        | suc n, cons x xs => 1
    """)

    fun test69_02() = doTest(data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | nil{-caret-} => 0
        | cons x xs => 1
    """, data1 + """
      \func test2 {A : \Type} {n : Nat} (xs : Vec A n) : Nat
        | {_}, {0}, nil => 0
        | {_}, {suc n}, cons x xs => 1 
    """)

    fun test69_03() = doTest(data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim n, xs
        | n, nil{-caret-} => n
        | n, cons x xs => n
    """, data1 + """
      \func test {A : \Type} {n : Nat} (xs : Vec A n) : Nat \elim n, xs
        | 0, nil => 0
        | suc n, cons x xs => suc n 
    """)

    fun test69_04() = doTest(data1S + """
      \func foo' (A : \Type) (n : Nat) (xs : Vec' (A, n)) : Nat \elim xs
        | nil2{-caret-} => 0
        | cons2 x xs => 1 
    """, data1S + """
      \func foo' (A : \Type) (n : Nat) (xs : Vec' (A, n)) : Nat \elim n, xs
        | 0, nil2 => 0
        | suc n, cons2 x xs => 1  
    """)

    fun test69_05() = doTest(data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim d
        | con1{-caret-} => 101
    """, data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim d
        | con1{-caret-} => 101
    """)

    fun test69_06() = doTest(data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim n, d
        | n, con2{-caret-} => 101 
    """, data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim n, d
        | 0, con2 => 101
    """)

    fun test69_07() = doTest(data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim d
        | con2{-caret-} => 101 
    """, data2 + """
      \func foo (n : Nat) (d : D (suc (suc n))) : Nat \elim n, d
        | 0, con2 => 101
    """)

    fun test69_08() = doTest(data2 + """
       \func foo {n : Nat} (d : D n) : Nat \with
         | {suc n'}, con1{-caret-} => n'  
    """, data2 + """
       \func foo {n : Nat} (d : D n) : Nat \with
         | {1}, con1 => 0
    """)

    fun test69_09() = doTest(data3 + """
       \func foo (a b : Nat) (d : D a b) : Nat \elim a, d
         | 0, con1{-caret-} => b Nat.+ b
         | suc a, con2 => a Nat.+ b
    """, data3 + """
       \func foo (a b : Nat) (d : D a b) : Nat \elim a, b, d
         | 0, 0, con1{-caret-} => 0 Nat.+ 0
         | suc a, suc b, con2 => a Nat.+ (suc b)
    """)

    fun test69_10() = doTest(data1 + """
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

    fun test69_11() = doTest(data4 + """
       \func foo (a b : Nat) (d : Container a b) : Nat \elim a, d
         | 0, envelope con1{-caret-} => 1
         | suc a, envelope con2 => 2
    """, data4 + """
       \func foo (a b : Nat) (d : Container a b) : Nat \elim a, b, d
         | 0, 0, envelope con1{-caret-} => 1
         | suc a, suc b, envelope con2 => 2
    """)

    fun test69_12() = doTest(data1 + """
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

    fun test69_13() = doTest(data5 + """
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

    fun test69_14() = doTest(data6 + """
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

    fun test69_15() = doTest(data7 + """
       \func foo {A : \Type} (p : Index A) : Nat \elim p
         | index nil{-caret-} => {?}
         | index (cons a v) => {?} 
    """, data7 + """
       \func foo {A : \Type} (p : Index A) : Nat \elim p
         | index {0} {_} {0} nil => {?}
         | index {suc m} {_} {suc k} (cons a v) => {?} 
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

    fun test69_16() = doTest(data8 + """
       \func foo {A : \Type} (i : D1) (p : D2 A i) : Nat \elim i, p
         | env, index nil{-caret-} => {?}
         | env, index (cons a v) => {?}
    """, data8 + """
       \func foo {A : \Type} (i : D1) (p : D2 A i) : Nat \elim i, p
         | env {0} {_} {0}, index nil{-caret-} => {?}
         | env {suc a} {_} {suc c}, index (cons a v) => {?}
    """)

    private val data9 = """
       \data Vec (A : \Type) (n : \Sigma Nat Nat) \elim n
         | (0, m) => nil
         | (suc n, m) => cons A (Vec A (n, m))
    """

    fun test69_17() = doTest(data9 + """
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
    fun test69_18() = doTest(data10 + """
       \func foo2 (a : \Sigma Nat Nat) (f : Foo a) : \Sigma Nat Nat \elim f
         | cons{-caret-} => a 
    """, data10 + """
       \func foo2 (a : \Sigma Nat Nat) (f : Foo a) : \Sigma Nat Nat \elim a, f
         | (0, 0) \as a, cons => a
    """)

    fun test69_19() = doTest(data10 + """
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

    fun test69_20() = doTest(data11 + """
       \func foo {c : C} (f : Foo c): Nat \elim f
         | cons{-caret-} => c.a 
    """, data11 + """
       \func foo {c : C} (f : Foo c): Nat \elim c, f
         | (0, 0) \as c : C, cons => c.a
    """)

    fun test69_21() = doTest(data3 + """
        \data D2 (a b : Nat) (d : D a b) \elim a, d
          | 0, con1{-caret-} => cons1
          | suc a, con3 => cons2 (Fin a)
    """, data3 + """
        \data D2 (a b : Nat) (d : D a b) \elim a, b, d
          | 0, 0, con1 => cons1
          | suc (suc a), suc (suc b), con3 => cons2 (Fin (suc a)) 
    """)

    fun test69_22() = doTest(data3 + """
       \data D2
         | cons1 (c : Nat)
         | cons2 (a b : Nat) (d : D a b) \elim a, d {
           | 0, con1{-caret-} => cons1 0
           | suc a, con3 => cons1 a
         } 
    """, data3 + """
       \data D2
         | cons1 (c : Nat)
         | cons2 (a b : Nat) (d : D a b) \elim a, b, d {
           | 0, 0, con1 => cons1 0
           | suc (suc a), suc (suc b), con3 => cons1 (suc a)
         }
    """)

    fun test69_23() = doTest(data3 + """
       \func foo (a b : Nat) (d : D a b) : Nat \elim a, d
         | 0, con1 => b Nat.+ b
         | 1, con2{-caret-} => b Nat.+ b 
    """, data3 + """
       \func foo (a b : Nat) (d : D a b) : Nat \elim a, b, d
         | 0, 0, con1 => 0 Nat.+ 0
         | 1, suc b, con2 => (suc b) Nat.+ (suc b) 
    """)

    companion object {
        const val data1 = """
      \data Vec (A : \Type) (n : Nat) \elim n
        | 0 => nil
        | suc n => cons A (Vec A n)
    """

        const val data2 = """
      \data D (n : Nat) \with | 1 => con1 | 2 => con2 | 3 => con3 
    """

        const val data3 = """
      \data D (n m : Nat) \with
        | 0, 0 => con1
        | suc _, suc _ => con2
        | suc (suc _), suc (suc _) => con3
        | n, m => con4
    """
    }
}