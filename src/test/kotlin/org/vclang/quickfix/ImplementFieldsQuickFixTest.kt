package org.vclang.quickfix

class ImplementFieldsQuickFixTest : QuickFixTestBase() {
    fun `test adding copatterns in instance`() = simpleQuickFixTest("Implement",
            """
                --! A.vc
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
                --! A.vc
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
                --! A.vc
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
               --! A.vc
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
            """
            )

    fun `test removing redundant clause`() = simpleQuickFixTest("Remove",
            """
                --! A.vc
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