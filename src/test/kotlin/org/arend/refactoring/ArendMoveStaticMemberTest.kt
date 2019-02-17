package org.arend.refactoring

class ArendMoveStaticMemberTest: ArendMoveTestBase() {

    fun testSimpleMove1() =
        testMoveRefactoring("""
             --! Main.ard
            \func abc{-caret-} => 1
            \module def \where {}
            """, """
            \open def (abc)

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
            \open foo (abc)

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
            \open Foo (abc)

            \module Foo \where {
              \func abc => 1
            }

            \func bar => abc
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
              \open bar (foo)

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
            \open def (abc)

            \module def \where {
              \module abc \where {}
            }
            """, "Main", "def")

    fun testMovedContent1() =
            testMoveRefactoring("""
                 --! DirB/Main.ard
                \module Foo \where {
                  \func foo => 101
                }

                \func foo => 202
                \func bar{-caret-} => foo
            """, """
                \import DirB.Main

                \module Foo \where {
                  \func foo => 101

                  \func bar => DirB.Main.foo
                }

                \func foo => 202

                \open Foo (bar)

            """, "DirB.Main", "Foo")

    fun testMoveData1() =
            testMoveRefactoring("""
                --! Main.ard
                \module Foo \where {}

                \data MyNat{-caret-}
                  | myZero
                  | myCons (n : MyNat)

                \func foo => myCons myZero
            ""","""
                \module Foo \where {
                  \data MyNat
                    | myZero
                    | myCons (n : MyNat)
                }

                \open Foo (MyNat, myZero, myCons)

                \func foo => myCons myZero
            """, "Main", "Foo")

    fun testClashingNames() =
            testMoveRefactoring("""
             --! Main.ard
            \data MyNat{-caret-}
              | myZero
              | myCons (n : MyNat)
            \module Foo \where {
              \func myZero => 0
            }

            \func bar => Foo.myZero

            \func lol => myZero
            """, """
            \open Foo (MyNat, myZero, myCons)

            \module Foo \where {
              \func myZero => 0

              \data MyNat
                | myZero
                | myCons (n : MyNat)
            }

            \func bar => Foo.myZero

            \func lol => MyNat.myZero
            """, "Main", "Foo")

    fun testMovedContent2() =
            testMoveRefactoring("""
                 --! Main.ard
                \import Main
                \open Nat

                \module Foo{-caret-} \where {
                  \func foo => bar.foobar + Foo.bar.foobar + Main.Foo.bar.foobar

                  \func bar => 2 \where {
                    \func foobar => foo + bar
                  }
                }

                \module Bar \where {
                  \open Foo

                  \func lol => foo + Foo.foo + bar.foobar + Foo.bar.foobar
                }

                \func goo => 4
            ""","""
                \import Main
                \open Nat
                \open goo (Foo)

                \module Bar \where {
                  \open goo.Foo

                  \func lol => foo + Foo.foo + bar.foobar + Foo.bar.foobar
                }

                \func goo => 4 \where {
                  \module Foo \where {
                    \func foo => bar.foobar + Foo.bar.foobar + bar.foobar

                    \func bar => 2 \where {
                      \func foobar => foo + bar
                    }
                  }
                }
            """, "Main", "goo")

    fun testMovedContent3() =
            testMoveRefactoring("""
                 --! Main.ard
                \class C{-caret-} {
                  | foo : Nat

                  \func bar(a : Nat) => foobar a
                } \where {
                  \func foobar (a : Nat) => a
                }

                \module Foo \where { }

                \func lol (L : C) => (C.bar (C.foobar (foo {L})))
            ""","""
                \open Foo (C, foo)

                \module Foo \where {
                  \class C {
                    | foo : Nat

                    \func bar(a : Nat) => foobar a
                  } \where {
                    \func foobar (a : Nat) => a
                  }
                }

                \func lol (L : C) => (C.bar (C.foobar (foo {L})))
            """, "Main", "Foo")

    fun testMoveData2() =
            testMoveRefactoring("""
                --! Main.ard
                \module Foo \where {}

                \data MyNat{-caret-}
                  | myZero
                  | myCons (n : MyNat)

                \func foo (m : MyNat) \elim m
                  | myZero => myZero
                  | myCons x => myCons x
                """, """
                \module Foo \where {
                  \data MyNat
                    | myZero
                    | myCons (n : MyNat)
                }

                \open Foo (MyNat, myZero, myCons)

                \func foo (m : MyNat) \elim m
                  | myZero => myZero
                  | myCons x => myCons x
                """, "Main", "Foo")

    fun testMoveStatCmds() =
            testMoveRefactoring("""
                --! Goo.ard
                \module GooM \where {
                  \func lol => 1
                }
                --! Foo.ard
                \module FooM \where {
                }
                --! Main.ard
                \import Goo
                \open GooM (lol \as lol'){-caret-}

                \func foobar => lol'
            """, """
                \import Foo
                \import Goo

                \open FooM (lol \as lol')

                \func foobar => lol'
            """, "Foo", "FooM", "Goo", "GooM.lol")

    fun testMoveFromWhereBlock1() =
            testMoveRefactoring("""
                --! A.ard

                \func lol => 1

                --! Main.ard

                \module Bar \where
                  \func bar{-caret-} => 1
            """, """
                \import A

                \module Bar \where
                  \open lol (bar)

            """, "A", "lol")

    fun testMoveFromWhereBlock2() =
            testMoveRefactoring("""
                --! A.ard

                \func lol => 1

                --! Main.ard

                \module Bar \where {
                  \func bar{-caret-} => 1
                }
            """, """
                \import A

                \module Bar \where {
                  \open lol (bar)
                }
            """, "A", "lol")

    fun testCleanFromHiding1() =
            testMoveRefactoring("""
                --! A.ard
                \func foo => 1

                --! Main.ard
                \import A \hiding (foo)

                \module Bar \where {}

                \func lol => A.foo{-caret-}
            """, """
                \import A

                \module Bar \where {
                  \func foo => 1
                }

                \func lol => Bar.foo
            """, "Main", "Bar", "A", "foo")

    fun testCleanFromHiding2() =
            testMoveRefactoring("""
                --! A.ard
                \module Foo \where {
                  \func foo => 1
                }

                --! Main.ard
                \import A
                \open Foo \hiding (foo)

                \module Bar \where {}

                \func lol => A.Foo.foo{-caret-}
            """, """
                \import A
                \open Foo

                \module Bar \where {
                  \func foo => 1
                }

                \func lol => Bar.foo
            """, "Main", "Bar", "A", "Foo.foo")

    fun testRemainderInEmptyFile() =
            testMoveRefactoring("""
                --! A.ard
                \func foo{-caret-} => 1

                --! B.ard
                \module Bar \where {}
            """, """
                \import B
                \open Bar (foo)
            """, "B", "Bar")

    fun testRemovingEmptyImportCommand() =
            testMoveRefactoring("""
                --! Main.ard
                \module Foo \where {
                  \func foo{-caret-} => 1
                }

                \module Bar \where {
                  \open Foo (foo)
                }
            """, """
                \module Foo \where {
                  \open Bar (foo)
                }

                \module Bar \where {
                  \func foo => 1
                }
            """, "Main", "Bar")

    fun testMultipleMove1() =
            testMoveRefactoring("""
                --! A.ard
                \module Foo \where {
                  \func foo => 1

                  \func lol => 2

                  \func bar => 3

                  \func goo => 4
                }
                --! Main.ard
                 \import A
                 \open Nat{-caret-}

                 \module Bar \where {
                   \open Foo (foo, lol, goo)

                   \func foobar => foo + lol + goo
                 }

                 \module Fubar \where {
                   \open Foo \hiding (bar, goo)

                   \func fubar => foo + lol + Foo.bar + Foo.goo
                 }
            """, """
                 \import A
                 \open Nat

                 \module Bar \where {
                   \open Foo (lol, goo)

                   \func foobar => foo + lol + goo

                   \func bar => 3

                   \func foo => 1
                 }

                 \module Fubar \where {
                   \open Foo \hiding (goo)
                   \open Bar (foo)

                   \func fubar => foo + lol + Bar.bar + Foo.goo
                 }
            """, "Main", "Bar", "A", "Foo.foo", "Foo.bar")

     fun testObstructedScopes() = //Should refactoring be prohibited in this situation?
            testMoveRefactoring("""
            --! A.ard
            \open Nat

            \module Foo \where {
              \func lol => 0

              \func foo{-caret-} => lol + Foo.lol + FooBar.lol + A.FooBar.lol
            }

            \module Bar \where {
              \func bar => 2
            }

            \module FooBar \where {
              \open Bar (bar \as foo)

              \func lol => foo + Foo.foo
            }
            """, """
            \open Nat

            \module Foo \where {
              \func lol => 0

              \open FooBar (foo)
            }

            \module Bar \where {
              \func bar => 2
            }

            \module FooBar \where {
              \open Bar (bar \as foo)

              \func lol => foo + foo

              \func foo => Foo.lol + Foo.lol + FooBar.lol + A.FooBar.lol
            }
            """, "A", "FooBar")

    fun testMoveData3() =
            testMoveRefactoring("""
                --! Main.ard
                \module Bar \where {
                  \data MyNat2{-caret-}
                    | myZero
                    | myCons MyNat2

                  \func lol => myZero
                }

                \module Foo \where {
                  \data MyNat1
                    | myZero
                    | myCons MyNat1

                  \open Bar.MyNat2 (myZero \as myZero')

                  \func lol (a : MyNat1) (b : Bar.MyNat2) \elim a, b
                    | myZero, myZero' => 1
                    | _, _ => 0
                }
            """, """
                \module Bar \where {
                  \open Foo (MyNat2)

                  \func lol => MyNat2.myZero
                }

                \module Foo \where {
                  \data MyNat1
                    | myZero
                    | myCons MyNat1

                  \func lol (a : MyNat1) (b : MyNat2) \elim a, b
                    | myZero, MyNat2.myZero => 1
                    | _, _ => 0

                  \data MyNat2
                    | myZero
                    | myCons MyNat2
                }
            """, "Main", "Foo")
 }