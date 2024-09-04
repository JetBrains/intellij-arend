package org.arend.quickfix

import org.arend.util.ArendBundle

class FunctionArgInferenceQuickFixTest : QuickFixTestBase() {

    fun testImplicitArgument() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \func f {A : Nat} (a : Nat) => A

        \func f1 => f{-caret-} 0
    """, """
        \func f {A : Nat} (a : Nat) => A

        \func f1 => f {{?}}{-caret-} 0
    """)

    fun testFunctionArgInference1() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \func f2 {A B C : \Type0} (a : A) (b : B) => a

        \func f3 => f2{-caret-} 0 0
    """, """
        \func f2 {A B C : \Type0} (a : A) (b : B) => a

        \func f3 => f2 {_} {_} {{?}}{-caret-} 0 0
    """)

    fun testFunctionArgInference2() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \func f4 {A B : \Type0} (a : A) {C : \Type1} (b : B) => a

        \func f5 => f4{-caret-} 0 0
    """, """
        \func f4 {A B : \Type0} (a : A) {C : \Type1} (b : B) => a

        \func f5 => f4 0 {{?}}{-caret-} 0
    """)

    fun testFixFunction1() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \func \infixl 6 f6 {A B : \Type0} (a : A) => a

        \func f7 => f6{-caret-} 0
    """, """
        \func \infixl 6 f6 {A B : \Type0} (a : A) => a

        \func f7 => f6 {_} {{?}}{-caret-} 0
    """)

    fun testFixFunction2() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \func \infixl 6 f6 {A B : \Type0} (a : A) (b : A) => a
        
        \func f7 => 0 f6{-caret-} 1
    """, """
        \func \infixl 6 f6 {A B : \Type0} (a : A) (b : A) => a

        \func f7 => 0 f6 {_} {{?}}{-caret-} 1
    """)

    fun testFixFunction3() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \func \infixl 6 f6 {A B : \Type0} (a : A) (b : A) => a

        \func f7 : Nat -> Nat => 0 `f6{-caret-}
    """, """
        \func \infixl 6 f6 {A B : \Type0} (a : A) (b : A) => a

        \func f7 : Nat -> Nat => 0 `f6 {_} {{?}}{-caret-}
    """)

    fun testList1() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \data List (A : \Type) | nil | cons A (List A)

        \func foo (X : \Type) (l : List X) => l
        
        \func bar => foo{-caret-} _ nil
    """, """
        \data List (A : \Type) | nil | cons A (List A)

        \func foo (X : \Type) (l : List X) => l
        
        \func bar => foo {?}{-caret-} nil
    """)

    fun testList2() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \data List (A : \Type) | nil | cons A (List A)

        \func foo (X : \Type) (l : List X) => l
        
        \func bar => foo _ nil{-caret-}
    """, """
        \data List (A : \Type) | nil | cons A (List A)

        \func foo (X : \Type) (l : List X) => l
        
        \func bar => foo _ (nil {{?}}{-caret-})
    """)

    fun testList3() = typedQuickFixTest(ArendBundle.message("arend.argument.inference.parameter"), """
        \data List (A : \Type) (B : \Type0) | nil | cons A B (List A B)

        \func foo (X : \Type) (Y : \Type0) (l : List X Y) => l
    
        \func bar => foo _ _ (nil {{?}}{-caret-})
    """, """
        \data List (A : \Type) (B : \Type0) | nil | cons A B (List A B)

        \func foo (X : \Type) (Y : \Type0) (l : List X Y) => l
        
        \func bar => foo _ _ (nil {{?}} {{?}}{-caret-})
    """)

    fun testInfix1() = typedQuickFixTest("Infer parameter", """
       \func \infixl 6 f6 {A B : \Type0} (a : A) (b : A) => a

       \func f7 : Nat => 0 f6 {_} {{?}} 1 f6{-caret-} 2
    """, """
       \func \infixl 6 f6 {A B : \Type0} (a : A) (b : A) => a

       \func f7 : Nat => 0 f6 {_} {{?}} 1 f6 {_} {{?}}{-caret-} 2
    """)

    fun testInfix2() = typedQuickFixTest("Infer parameter", """
       \func \infixl 1 f8 {X : \Type} (a b : Nat) : Nat => {?}

       \func f9 : Nat => 2 f8{-caret-} {Nat} _
    """, """
       \func \infixl 1 f8 {X : \Type} (a b : Nat) : Nat => {?}

       \func f9 : Nat => 2 f8 {Nat} {?}{-caret-}
    """)

    fun testInfix3() = typedQuickFixTest("Infer parameter", """
       \func f10 {X : \Type} (a : Nat) {Y Z : \Type} (b : X) => 101

       \func f11 => 101 Nat.+ f10{-caret-} {_} 101 {{?}} 102 
    """, """
       \func f10 {X : \Type} (a : Nat) {Y Z : \Type} (b : X) => 101

       \func f11 => 101 Nat.+ f10 {_} 101 {{?}} {{?}}{-caret-} 102 
    """)

    fun testClass() = typedQuickFixTest("Infer parameter", """
       \class C {
         \func lol (a : Nat) => 101
       }

       \func bar => C.lol{-caret-} _
    """, """
       \class C {
         \func lol (a : Nat) => 101
       }

       \func bar => C.lol {?}{-caret-}
    """)

    fun testClass2() = typedQuickFixTest("Infer parameter", """
       \class C {
         \func lol (a : Nat) => 101
       }

       \func bar => C.lol{-caret-} _
    """, """
       \class C {
         \func lol (a : Nat) => 101
       }

       \func bar => C.lol {?}{-caret-}
    """)

    fun testData() = typedQuickFixTest("Infer parameter", """
       \data D (X Y : \Type)

       \func LOL => D{-caret-} Nat _
    """, """
       \data D (X Y : \Type)

       \func LOL => D Nat {?}{-caret-}
    """)

    fun testLemmaAndRecord() = typedQuickFixTest("Infer parameter", """
        \lemma prop-pi {A : \Prop} {a a' : A} : a = a' => {?}
        
        \record Record {A : \Type}
          | sec : A -> A
        
        \func test => \new Record {
          | A => {?}
          | sec => prop-pi{-caret-}
        }
    """, """
        \lemma prop-pi {A : \Prop} {a a' : A} : a = a' => {?}
        
        \record Record {A : \Type}
          | sec : A -> A
        
        \func test => \new Record {
          | A => {?}
          | sec => prop-pi {_} {{?}{-caret-}}
        }
    """)

    /* doesn't work with lambda because error.definition is null
    * \func f => \lam {A B : \Type0} (a : A) {C : \Type1} (b : B) => a
    *
    * \func f1 => f 0 0
    */
}
