package org.arend.quickfix

class MissingClausesQuickFixTest: QuickFixTestBase() {
    private val listDefinition =
        """
        \data List (A : \Type)
          | nil
          | :: (x : A) (xs : List A)
        """

    private val listDefinition2 =
        """
        \data List (A : \Type)
          | nil
          | :: {A} (List A) 
        """

    private val listDefinition3 =
        """
        \data List (A : \Type)
          | nil
          | :: {x : A} {xs : List A} 
        """

    private val fooDefinition =
        """ 
        \data Nat2
          | zero2
          | suc2 (a : Nat2)
            
        \data Foo
          | foo1
          | foo2 {a : Nat2} (b : Nat2)
        """

    private val orDefinition =
        """
        \data Or (A B : \Type) | inl A | inr B 
        """

    fun testBasicPattern() = typedQuickFixTest("Implement",
        """
        --! Main.ard
        \$listDefinition
    
        \func length{-caret-} {A : \Type} (l : List A) : Nat \with
          | nil => 0
          | :: x nil => 1
        """, """ 
        \$listDefinition

        \func length {A : \Type} (l : List A) : Nat \with
          | nil => 0
          | :: x nil => 1
          | :: x (:: x1 xs) => {?}
        """)

    fun testBasicElim() = typedQuickFixTest("Implement",
        """
        \$listDefinition
    
        \func length{-caret-} (A : \Type) (l : List A) (n : Nat) : Nat \elim l
          | nil => n
          | :: x nil => n
        """, """ 
        \$listDefinition

        \func length (A : \Type) (l : List A) (n : Nat) : Nat \elim l
          | nil => n
          | :: x nil => n
          | :: x (:: x1 xs) => {?}
        """)

    fun testImplicitPattern() = typedQuickFixTest("Implement",
        """
        --! Main.ard
        \$listDefinition2
    
        \func length{-caret-} {A : \Type} (l : List A) : Nat \with
          | nil => 0
          | :: {x} nil => 1
        """, """ 
        \$listDefinition2

        \func length {A : \Type} (l : List A) : Nat \with
          | nil => 0
          | :: {x} nil => 1
          | :: (:: _) => {?}
        """)

    fun testImplicitPattern2() = typedQuickFixTest("Implement",
            """
        --! Main.ard
        \$listDefinition3
    
        \func length{-caret-} {A : \Type} {l : List A} : Nat \with
          | {_}, {nil} => 0
          | {_}, {:: {x} {nil}} => 1
        """, """ 
        \$listDefinition3

        \func length {A : \Type} {l : List A} : Nat \with
          | {_}, {nil} => 0
          | {_}, {:: {x} {nil}} => 1
          | {A}, {:: {x} {::}} => {?}
        """)

    fun testBasicTwoPatterns() = typedQuickFixTest("Implement",
        """
        --! Main.ard 
        \$listDefinition

        \func lol{-caret-} {A : \Type} (l : List A) (l2 : List A) : Nat \with
          | nil, nil => 1
        """, """
        \$listDefinition

        \func lol {A : \Type} (l : List A) (l2 : List A) : Nat \with
          | nil, nil => 1
          | :: x xs, l2 => {?}
          | nil, :: x xs => {?} 
        """)

    fun testMixedTwoPatterns() = typedQuickFixTest("Implement",
        """
        --! Main.ard
        \$fooDefinition        
        
        \func bar{-caret-} (a : Foo) {b : Foo} : Nat2 \with
          | foo1, {foo1} => zero2
          | foo2 {zero2} (suc2 zero2), {foo2 {zero2} (suc2 zero2)} => zero2 
        ""","""
        \$fooDefinition
        
        \func bar (a : Foo) {b : Foo} : Nat2 \with
          | foo1, {foo1} => zero2
          | foo2 {zero2} (suc2 zero2), {foo2 {zero2} (suc2 zero2)} => zero2
          | foo1, {foo2 b} => {?}
          | foo2 {suc2 a} b => {?}
          | foo2 {zero2} zero2 => {?}
          | foo2 {zero2} (suc2 (suc2 a)) => {?}
          | foo2 {zero2} (suc2 zero2), {foo1} => {?}
          | foo2 {zero2} (suc2 zero2), {foo2 {suc2 a} b} => {?}
          | foo2 {zero2} (suc2 zero2), {foo2 {zero2} zero2} => {?}
          | foo2 {zero2} (suc2 zero2), {foo2 {zero2} (suc2 (suc2 a))} => {?}    
        """)

    fun testElim() = typedQuickFixTest("Implement",
        """
        --! Main.ard
        \$orDefinition
        
        \func Or-to-||{-caret-} {A B : \Prop} (a-or-b : Or A B) : Or A B \elim a-or-b
          | inl a => inl a
        """, """
        \$orDefinition
            
        \func Or-to-|| {A B : \Prop} (a-or-b : Or A B) : Or A B \elim a-or-b
          | inl a => inl a
          | inr _ => {?}
        """)

    fun testCase() = typedQuickFixTest("Implement",
        """
        --! Main.ard
        \$orDefinition
        
        \func Or-to-|| {A B : \Prop} (a-or-b : Or A B) : Or A B => \case a-or-b \with {
          | inl a => inl a{-caret-}
        }
        """, """
        \$orDefinition
            
        \func Or-to-|| {A B : \Prop} (a-or-b : Or A B) : Or A B => \case a-or-b \with {
          | inl a => inl a
          | inr _ => {?}
        }
        """)

    fun testResolveReference() = typedQuickFixTest("Implement",
        """
        --! Logic.ard 
        \data || (A B : \Type)
          | byLeft A
          | byRight B
 
        --! Main.ard    
        \import Logic ()

        \func byLeft => 101

        \func lol{-caret-} {A B : \Type} (a b : Logic.|| A B) : Nat
          | Logic.byLeft x, Logic.byLeft y => {?} 
        """, """
        \import Logic (byRight, ||)

        \func byLeft => 101

        \func lol {A B : \Type} (a b : Logic.|| A B) : Nat
          | Logic.byLeft x, Logic.byLeft y => {?}
          | byRight _, b => {?}
          | ||.byLeft x, byRight _ => {?} 
        """)

    fun testNaturalNumbers() = typedQuickFixTest("Implement",
        """
        --! Main.ard    
        \func plus{-caret-} (a b : Nat) : Nat
          | 0, 2 => 0        
        """, """
        \func plus (a b : Nat) : Nat
          | 0, 2 => 0
          | suc _, b => {?}
          | 0, 0 => {?}
          | 0, 1 => {?}
          | 0, suc (suc (suc _)) => {?}    
        """)

    fun testIntegralNumbers() = typedQuickFixTest("Implement",
        """
        \func{-caret-} abs (a : Int) : Int
          | -3 => 3
          | 3 => 3 
        """, """
        \func abs (a : Int) : Int
          | -3 => 3
          | 3 => 3
          | 0 => {?}
          | 1 => {?}
          | 2 => {?}
          | pos (suc (suc (suc (suc _)))) => {?}
          | neg 0 => {?}
          | -1 => {?}
          | -2 => {?}
          | neg (suc (suc (suc (suc _)))) => {?}    
        """)

    fun testEmpty1() = typedQuickFixTest("Implement",
            """
                \func foo{-caret-} (x : Nat) : Nat
            """, """
                \func foo (x : Nat) : Nat
                  | 0 => {?}
                  | suc _ => {?}
            """)

    fun testEmpty2() = typedQuickFixTest("Implement",
            """
                \func foo (x : Nat) : Nat => \case x {-caret-}\with {  }
            """, """
                \func foo (x : Nat) : Nat => \case x \with {
                  | 0 => {?}
                  | suc _ => {?}
                }
            """)

    fun testEmpty3() = typedQuickFixTest("Implement",
            """
                \func foo{-caret-} (x : Nat) : Nat \elim x
            """, """
                \func foo (x : Nat) : Nat \elim x
                  | 0 => {?}
                  | suc _ => {?}
            """)
}