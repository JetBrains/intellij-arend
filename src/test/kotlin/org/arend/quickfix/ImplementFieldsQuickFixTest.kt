package org.arend.quickfix

import org.arend.quickfix.AbstractEWCCAnnotator.Companion.IMPLEMENT_MISSING_FIELDS

class ImplementFieldsQuickFixTest : QuickFixTestBase() {
    fun `test adding copatterns in instance`() = simpleQuickFixTest("Implement",
            """
                --! A.ard
                \class Foo {
                | A : Nat
                | B : Nat
                }
                \instance Bar : Foo{-caret-}
            """,
            """
                \class Foo {
                | A : Nat
                | B : Nat
                }
                \instance Bar : Foo
                  | A => {?}{-caret-}
                  | B => {?}
            """)

    fun `test adding copatterns in instance 2`() = simpleQuickFixTest("Implement",
            """
                --! A.ard
                \class Foo {
                | A : Nat
                }
                \instance Bar : Foo{-caret-} {}
            """,
            """
                \class Foo {
                | A : Nat
                }
                \instance Bar : Foo {}
                  | A => {?}{-caret-}
            """)

    fun `test adding implementation for a field`() = simpleQuickFixTest("Replace",
            """
                --! A.ard
                \class Foo (A B : Nat)
                \class Bar { f : Foo }
                \instance FooBar : Bar {
                  | f => {?}{-caret-}
                  }
            """,
            """
                \class Foo (A B : Nat)
                \class Bar { f : Foo }
                \instance FooBar : Bar {
                  | f {
                    | A => {?}{-caret-}
                    | B => {?}
                  }
                }
            """)

    fun `test adding implementation of a new expression`() = simpleQuickFixTest("Add",
            """
               --! A.ard
               \class Foo (A : Nat)
               \class Bar { | f : Foo | B : Nat }
               \func lol => \new Bar{-caret-}
            """,
            """
               \class Foo (A : Nat)
               \class Bar { | f : Foo | B : Nat }
               \func lol => \new Bar {
                 | f => {?}{-caret-}
                 | B => {?}
               }
            """)

    fun `test completing incomplete implementation`() = simpleQuickFixTest("Add",
            """
               --! A.ard
               \class Bar { | A : Nat | B : Nat }
               \func lol => \new Bar {
                 | A => {?}{-caret-}
                 }
            """,
            """
               \class Bar { | A : Nat | B : Nat }
               \func lol => \new Bar {
                 | A => {?}
                 | B => {?}{-caret-}
               }
            """)

    fun `test adding implementation of cowith expression`() = simpleQuickFixTest("Implement",
            """
               --! A.ard
               \class Foo (A : Nat)
               \class Bar { | f : Foo | B : Nat }
               \func lol : Bar \cowith{-caret-}
            """,
            """
               \class Foo (A : Nat)
               \class Bar { | f : Foo | B : Nat }
               \func lol : Bar \cowith
                 | f => {?}{-caret-}
                 | B => {?}
            """)


    fun `test removing redundant clause`() = simpleQuickFixTest("Remove",
            """
                --! A.ard
                \class Foo (A : Nat)
                \class Bar \extends Foo
                \instance FooBar : Bar {
                  | Foo{-caret-} { }
                  | A => 1
                }
            """,
            """
                \class Foo (A : Nat)
                \class Bar \extends Foo
                \instance FooBar : Bar {
                  {-caret-}| A => 1
                }
            """)

    fun `test adding empty implementations to a clause corresponding to an ancestor class`() = simpleQuickFixTest("Add",
            """
                --! A.ard
                \record A (x y : Nat)
                \record B (z w : Nat) \extends A

                \func foo : B \cowith
                | A{-caret-} {
                  | y => {?}
                }
                | z => 0
                | w => {?}
            """,
            """
                \record A (x y : Nat)
                \record B (z w : Nat) \extends A

                \func foo : B \cowith
                | A {
                  | y => {?}
                  | x => {?}{-caret-}
                }
                | z => 0
                | w => {?}
            """)

    fun `test remove already implemented coclause`() = simpleQuickFixTest("Remove",
            """
                --! A.ard
                \record C | x : Nat
                \record D \extends C | x => 0
                \func f : D \cowith | x => 1{-caret-}
            """,
            """
                \record C | x : Nat
                \record D \extends C | x => 0
                \func f : D \cowith {-caret-}
            """)

    fun `test adding field implementation`() = simpleQuickFixTest("Implement",
            """
               --! A.ard
               \class Foo1 {
                 | j1 : Int
                 | j2 : Int
               }
               \class Foo2 {
                 | c : Foo1
               }
               \class Foo3 \extends  Foo2 {
                 | c{-caret-} {
               }
            """,
            """
               \class Foo1 {
                 | j1 : Int
                 | j2 : Int
               }
               \class Foo2 {
                 | c : Foo1
               }
               \class Foo3 \extends  Foo2 {
                 | c {
                   | j1 => {?}{-caret-}
                   | j2 => {?}
                 }
            """)

    fun `test adding implementation of two fields with clashing names 1`() = simpleQuickFixTest("Add",
            """
            --! A.ard
            \class A {| Z : Nat}
            \class B {| Z : Nat}
            \class C \extends A, B {| A.Z => 0 }
            \func lol => \new C{-caret-} {}
            """,
            """
            \class A {| Z : Nat}
            \class B {| Z : Nat}
            \class C \extends A, B {| A.Z => 0 }
            \func lol => \new C {
              | B.Z => {?}{-caret-}
            }
            """)

    fun `test adding implementation of two fields with clashing names 2`() = simpleQuickFixTest("Add",
            """
            --! A.ard
            \class A {| Z : Nat}
            \class B {| Z : Nat}
            \class C \extends A, B {}
            \func lol => \new C{-caret-} {}
            """,
            """
            \class A {| Z : Nat}
            \class B {| Z : Nat}
            \class C \extends A, B {}
            \func lol => \new C {
              | Z => {?}{-caret-}
              | B.Z => {?}
            }
            """)

    fun `test no implement quickfix in ClassImplement with fat arrow`() = checkNoQuickFixes(IMPLEMENT_MISSING_FIELDS,
            """
            --! A.ard
            \class A { a : Nat }
            \class B { b : A }

            \class C \extends B {
              | c : A
              | {-caret-}b => c
            }
            """)
}
