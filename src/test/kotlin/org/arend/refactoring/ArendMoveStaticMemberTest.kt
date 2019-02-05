package org.arend.refactoring

class ArendMoveStaticMemberTest: ArendMoveTestBase() {

    fun testSimpleMove1() =
        testMoveRefactoring("""
             --! Main.ard
            \func abc{-caret-} => 1
            \module def \where {}
            """, """
            \module def \where {
              \func abc => 1
            }
            """, "Main", "def")

    fun testSimpleMove2() =
            testMoveRefactoring("""
             --! Main.ard
            \func abc{-caret-} => 1
            \func foo => 2 \where
              \func bar => 3
            """, """
            \func foo => 2 \where {
              \func bar => 3

              \func abc => 1
            }
            """, "Main", "foo")

    fun testForbiddenRefactoring1() =
            testMoveRefactoring("""
             --! Main.ard
            \func foo{-caret-} => 2 \where
              \func bar => 3
            """, null, "Main", "foo.bar")

    fun testSimpleMove3() =
            testMoveRefactoring("""
             --! Main.ard
            \func abc{-caret-} => 1
            \module Foo
            \func bar => abc
            """, """
            \module Foo \where {
              \func abc => 1
            }

            \func bar => Foo.abc
            """, "Main", "Foo")

    fun testForbiddenRefactoring2() =
            testMoveRefactoring("""
             --! Main.ard
            \func foo{-caret-} => 0
            \module Foo \where {
              \func foo => 1
            }
            """, null, "Main", "Foo")

    fun testLongName1() =
            testMoveRefactoring("""
             --! Main.ard
            \module Foo \where {
              \func foo{-caret-} => 1

              \func bar => 2
            }

            \func foobar => Foo.foo
            """, """
            \module Foo \where {
              \func bar => 2 \where {
                \func foo => 1
              }
            }

            \func foobar => Foo.bar.foo
            """, "Main", "Foo.bar")

    fun testMoveModule() =
            testMoveRefactoring("""
             --! Main.ard
            \module abc{-caret-} \where {}
            \module def \where {}
            """, """
            \module def \where {
              \module abc \where {}
            }
            """, "Main", "def")

    fun testMovedContent1() = //Fixme
            testMoveRefactoring("""
                 --! Main.ard
                \module Foo \where {
                  \func foo => 101
                }

                \func foo => 202
                \func bar{-caret-} => foo
            """, """
                \module Foo \where {
                  \func foo => 101

                  \func bar => Main.foo
                }

                \func foo => 202

            """, "Main", "Foo")



}