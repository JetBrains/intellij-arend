package org.arend.quickfix

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
                \instance Bar : Foo {
                  | A => {?}{-caret-}
                  }
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

    fun `test adding implementation of a new expression`() = simpleQuickFixTest("Implement",
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

    fun `test completing incomplete implementation`() = simpleQuickFixTest("Implement",
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
               \func lol : Bar \cowith {
                 | f => {?}{-caret-}
                 | B => {?}
                 }
            """)


    fun `test removing redundant clause`() = simpleQuickFixTest("Remove",
            """
                --! A.ard
                \class Foo (A : Nat)
                \class Bar \extends Foo
                \instance FooBar : Bar {
                  | Foo { }{-caret-}
                  }
            """,
            """
                \class Foo (A : Nat)
                \class Bar \extends Foo
                \instance FooBar : Bar {{-caret-}
                  }
            """)
}
