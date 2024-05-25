package org.arend.refactoring

class ArendMoveMemberTest : ArendMoveTestBase() {

    fun testSimpleMove1() =
            doTestMoveRefactoring("""
             -- ! Main.ard
            {- | Block
             - Doc -}
            \func abc{-caret-} => 1
            \module def \where {}
            """, """
            \module def \where {
              {- | Block
               - Doc -}
              \func abc => 1
            }
            """, "Main", "def")

    fun testSimpleMove2() =
            doTestMoveRefactoring("""
             -- ! Main.ard
             -- | LineDoc 1
            \func abc{-caret-} => 1
            \func foo => 2 \where
              \func bar => 3
            """, """
            \func foo => 2 \where {
              \func bar => 3

              -- | LineDoc 1
              \func abc => 1
            }
            """, "Main", "foo")

    fun testForbiddenRefactoring1() =
            doTestMoveRefactoring("""
             -- ! Main.ard
            \func foo{-caret-} => 2 \where
              \func bar => 3
            """, null, "Main", "foo.bar")

    fun testSimpleMove3() =
            doTestMoveRefactoring("""
             -- ! Main.ard
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
            doTestMoveRefactoring("""
             -- ! Main.ard
            \func foo{-caret-} => 0
            \module Foo \where {
              \func foo => 1
            }
            """, null, "Main", "Foo")

    fun testForbiddenRefactoring3() =
            doTestMoveRefactoring("""
             -- ! Main.ard
            \func foo{-caret-} => 0
            \module Foo \where {
              \func bar \alias foo => 1
            }
            """, null, "Main", "Foo")

    fun testLongName1() =
            doTestMoveRefactoring("""
             -- ! Main.ard
            \module Foo \where {
              \func foo{-caret-} => 1

              \func bar => 2
            }

            \func foobar => Foo.foo
            """, """
            \module Foo \where {
              \func bar => 2 
                \where {
                  \func foo => 1
                }
            }

            \func foobar => Foo.bar.foo
            """, "Main", "Foo.bar")

    fun testMoveModule() =
            doTestMoveRefactoring("""
             -- ! Main.ard
            \module abc{-caret-} \where {}
            \module def \where {}
            """, """
            \module def \where {
              \module abc \where {}
            }
            """, "Main", "def")

    fun testMovedContent1() =
            doTestMoveRefactoring("""
                 -- ! DirB/Main.ard
                \module Foo \where {
                  \func foo => 101
                }

                \func foo => bar
                \func bar{-caret-} => foo
            """, """
                \import DirB.Main

                \module Foo \where {
                  \func foo => 101

                  \func bar => DirB.Main.foo
                }

                \func foo => bar

                \open Foo (bar)
            """, "DirB.Main", "Foo")

    fun testMoveData1() =
            doTestMoveRefactoring("""
                -- ! Main.ard
                \module Foo \where {}

                \data MyNat{-caret-}
                  | myZero
                  | myCons (n : MyNat)

                \func foo => myCons myZero
            """, """
                \module Foo \where {
                  \data MyNat
                    | myZero
                    | myCons (n : MyNat)
                }

                \open Foo (MyNat, myCons, myZero)

                \func foo => myCons myZero
            """, "Main", "Foo")

    fun testClashingNames() =
            doTestMoveRefactoring("""
             -- ! Main.ard
            \data MyNat{-caret-}
              | myZero
              | myCons (n : MyNat)
            \module Foo \where {
              \func myZero => 0
            }

            \func bar => Foo.myZero

            \func lol => myZero
            """, """
            \open Foo (MyNat)

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
            doTestMoveRefactoring("""
                 -- ! Main.ard
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
            """, """
                \import Main
                \open Nat
                \open goo (Foo)

                \module Bar \where {
                  \open Foo

                  \func lol => foo + Foo.foo + bar.foobar + Foo.bar.foobar
                }

                \func goo => 4
                  \where {
                    \module Foo \where {
                      \func foo => bar.foobar + Foo.bar.foobar + bar.foobar
                
                      \func bar => 2 \where {
                        \func foobar => foo + bar
                      }
                    }
                  }
            """, "Main", "goo")

    fun testMovedContent3() =
            doTestMoveRefactoring("""
                 -- ! Main.ard
                \class C{-caret-} {
                  | foo : Nat

                  \func bar (a : Nat) => foobar a
                } \where {
                  \func foobar (a : Nat) => a
                }

                \module Foo \where { }

                \func lol (L : C) => (C.bar (C.foobar (foo {L})))
            """, """
                \open Foo (C, foo)

                \module Foo \where {
                  \class C {
                    | foo : Nat

                    \func bar (a : Nat) => foobar a
                  } \where {
                    \func foobar (a : Nat) => a
                  }
                }

                \func lol (L : C) => (C.bar (C.foobar (foo {L})))
            """, "Main", "Foo")

    fun testMoveData2() =
            doTestMoveRefactoring("""
                -- ! Main.ard
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

                \open Foo (MyNat, myCons, myZero)

                \func foo (m : MyNat) \elim m
                  | myZero => myZero
                  | myCons x => myCons x
                """, "Main", "Foo")

    fun testMoveStatCmds() =
            doTestMoveRefactoring("""
                -- ! Goo.ard
                \module GooM \where {
                  \func lol => 1
                }
                -- ! Foo.ard
                \module FooM \where {
                }
                -- ! Main.ard
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
            doTestMoveRefactoring("""
                -- ! A.ard

                \func lol => 1

                -- ! Main.ard

                \module Bar \where
                  \func bar{-caret-} => 1
            """, """ 
                \module Bar
            """, "A", "lol")

    fun testMoveFromWhereBlock2() =
            doTestMoveRefactoring("""
                -- ! A.ard

                \func lol => 1

                -- ! Main.ard

                \module Bar \where {
                  \func bar{-caret-} => 1
                }
            """, """
                \module Bar \where { 
                }
            """, "A", "lol")

    fun testCleanFromHiding1() =
            doTestMoveRefactoring("""
                -- ! A.ard
                \func foo => 1

                -- ! Main.ard
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
            doTestMoveRefactoring("""
                -- ! A.ard
                \module Foo \where {
                  \func foo => 1
                }

                -- ! Main.ard
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
            doTestMoveRefactoring("""
                -- ! A.ard
                \func foo{-caret-} => 1

                -- ! B.ard
                \module Bar \where {}
            """, """
            """, "B", "Bar")

    fun testRemovingEmptyImportCommand() =
            doTestMoveRefactoring("""
                -- ! Main.ard
                \module Foo \where {
                  \func foo{-caret-} => 1
                }

                \module Bar \where {
                  \open Foo (foo)
                }
            """, """
                \module Foo \where {
                }

                \module Bar \where {
                  \func foo => 1
                }
            """, "Main", "Bar")

    fun testMultipleMove1() =
            doTestMoveRefactoring("""
                -- ! A.ard
                \module Foo \where {
                  \func foo => 1

                  \func lol => 2

                  \func bar => 3

                  \func goo => 4
                }
                -- ! Main.ard
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

                   \func foo => 1

                   \func bar => 3
                 }

                 \module Fubar \where {
                   \open Foo \hiding (goo)
                   \open Bar (foo)

                   \func fubar => foo + lol + Bar.bar + Foo.goo
                 }
            """, "Main", "Bar", "A", "Foo.foo", "Foo.bar")

    fun testObstructedScopes() = //Should refactoring be prohibited in this situation?
            doTestMoveRefactoring("""
            -- ! A.ard
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
            doTestMoveRefactoring("""
                -- ! Main.ard
                \module Bar \where {
                  \data MyNat2{-caret-}
                    | A
                    | B

                  \func lol => A

                  \func lol2 => Foo.A
                }

                \module Foo \where {
                  \data MyNat1
                    | A
                    | B

                  \open Bar.MyNat2 (A \as A')

                  \func lol (a : MyNat1) (b : Bar.MyNat2) \elim a, b
                    | A, A' => 1
                    | B, Bar.B => 0
                }
            """, """
                \module Bar \where {
                  \open Foo (MyNat2)

                  \func lol => MyNat2.A

                  \func lol2 => Foo.A
                }

                \module Foo \where {
                  \data MyNat1
                    | A
                    | B

                  \open MyNat2 (A \as A')

                  \func lol (a : MyNat1) (b : MyNat2) \elim a, b
                    | A, A' => 1
                    | B, MyNat2.B => 0

                  \data MyNat2
                    | A
                    | B
                }
            """, "Main", "Foo")

    fun testMultipleMove2() =
            doTestMoveRefactoring("""
               -- ! Foo.ard
               \import Main
               \data D1 {-caret-}
                 | A
                 | B

               \data D2
                 | A
                 | B

               -- ! Main.ard
               -- Empty File

            """, """
               \import Main
            """, "Main", "", "Foo", "D1", "D2")

    fun testMultipleMove3() =
            doTestMoveRefactoring("""
               -- ! Foo.ard
               \import Main ()
               \data D1 {-caret-}
                 | A
                 | B

               \data D2
                 | A
                 | B
                 
               \func lol (d : D1) (d2 : D2) => 1   

               -- ! Main.ard
               -- Empty File

            """, """
               \import Main (D1, D2)
               
               \func lol (d : D1) (d2 : D2) => 1
            """, "Main", "", "Foo", "D1", "D2")

    fun testMultipleMove4() =
            doTestMoveRefactoring("""
               -- ! Foo.ard
               \data D1 {-caret-}
                 | A
                 | B

               \data D2
                 | A
                 | B

               \func foo (d1 : D1) (d2 : D2) \elim d1, d2
                 | D1.A, D2.A => 1
                 | D1.B, D2.B => 0

               -- ! Main.ard
               -- Empty File

            """, """
               \import Main

               \func foo (d1 : D1) (d2 : D2) \elim d1, d2
                 | D1.A, D2.A => 1
                 | D1.B, D2.B => 0
            """, "Main", "", "Foo", "D1", "D2")

    fun testMultipleRenaming1() =
            doTestMoveRefactoring("""
               -- ! Main.ard
               \open Nat

               \module Foo \where {
                 \func foo{-caret-} => 1
               }

               \module Bar \where {}

               \module FooBar \where {
                 \open Foo (foo \as foo1, foo \as foo2)
                 \func lol => foo1 + foo2
               }
            """, """
               \open Nat

               \module Foo \where {
               }

               \module Bar \where {
                 \func foo => 1
               }

               \module FooBar \where {
                 \open Bar (foo \as foo1, foo \as foo2)

                 \func lol => foo1 + foo2
               }
            """, "Main", "Bar")

    fun testMultipleRenaming2() =
            doTestMoveRefactoring("""
               -- ! Foo.ard
               \func foo => 1

               -- ! Bar.ard
               {- Empty file -}
               -- ! Main.ard
               \import Foo (foo \as foo1, foo \as foo2)
               \open Nat {-caret-}

               \module FooBar \where {
                 \func lol => foo1 + foo2
               }
            """, """
               \import Bar \using (foo \as foo1, foo \as foo2)
               \import Foo ()
               \open Nat

               \module FooBar \where {
                 \func lol => foo1 + foo2
               }
            """, "Bar", "", "Foo", "foo")

    fun testRenaming() =
            doTestMoveRefactoring("""
               -- ! Main.ard
               \module Foo \where {
                 \open Foo (lol \as lol1)

                 \func lol{-caret-} => 1

                 \func bar => lol1
               }

               \module Bar \where {}
                """, """
               \module Foo \where {
                 \open Bar (lol, lol \as lol1)

                 \func bar => lol1
               }

               \module Bar \where {
                 \func lol => 1
               }
                """, "Main", "Bar")

    fun testMultipleMove5() =
            doTestMoveRefactoring("""
                -- ! Main.ard
                \open Nat
                \class C{-caret-} {
                  | foo : Nat

                  \func bar => foo
                } \where {
                  \func foobar => c1

                  \func bar2 => D.lol

                  \func bar3 => f + foobar
                }

                \func f => D.lol + C.bar2

                \func g => c1

                \data D
                 | c1
                 | c2
                \where {
                  \func lol => C.bar

                  \func lol' => C.foobar

                  \func lol'' => lol

                  \func goo => c1
                }

                \module FooBar \where {}
            """, """
               \open Nat
               \open FooBar (C, D, c1)

               \func f => D.lol + C.bar2

               \func g => c1

               \module FooBar \where {
                 \class C {
                   | foo : Nat

                   \func bar => foo
                 } \where {
                   \func foobar => c1

                   \func bar2 => D.lol

                   \func bar3 => f + foobar
                 }

                 \data D
                  | c1
                  | c2
                 \where {
                   \func lol => C.bar

                   \func lol' => C.foobar

                   \func lol'' => lol

                   \func goo => c1
                 }
               }""", "Main", "FooBar", "Main", "C", "D")

    fun testPlainImportFix() =
            doTestMoveRefactoring("""
               -- ! A.ard
               \func foo => 1
               -- ! B.ard
               -- Empty file
               -- ! Main.ard
               \import A{-caret-}

               \func lol => foo
        """, """
               \import A
               \import B

               \func lol => foo
        """, "B", "", "A", "foo")

    fun testMinimalImport1() =
            doTestMoveRefactoring("""
               -- ! A.ard
               \func foo => 1

               \func bar => 2
               -- ! B.ard
               \func bar => 3
               -- ! Main.ard
               \import A{-caret-}

               \func lol => foo
        """, """
               \import A
               \import B (foo)

               \func lol => foo
        """, "B", "", "A", "foo")

    fun testPlainImportFix2() =
            doTestMoveRefactoring("""
               -- ! A.ard
               \func foo => 1

               \func bar => 2
               -- ! B.ard
               \func bar => 3
               -- ! Main.ard
               \import A \hiding (bar){-caret-}

               \func lol => foo
        """, """
               \import A \hiding (bar)
               \import B

               \func lol => foo
        """, "B", "", "A", "foo")

    fun testMoveOutside() =
            doTestMoveRefactoring("""
               -- ! A.ard
               \module Foo \where {
                 \open Bar (fooBar)
               }

               \module Bar \where {
                 \func fooBar{-caret-} => 1
               }
            ""","""
               \module Foo \where {
                 \open A (fooBar)
               }

               \module Bar \where {
               }

               \func fooBar => 1
            """, "A", "")

    fun testClassField() =
            doTestMoveRefactoring("""
               -- ! A.ard
               \class Foo{-caret-} (U : Nat) {
                 \func foo => U
               } \where {
                 \func bar => U
               }

               \module Bar \where {}
            """, """
               \module Bar \where {
                 \class Foo (U : Nat) {
                   \func foo => U
                 } \where {
                   \func bar => U
                 }
               }
            """, "A", "Bar")

    fun testInfixArguments1() =
            doTestMoveRefactoring("""
               -- ! A.ard
               \module Bar \where {
                 \func foo{-caret-} (a b : Nat) => 101
 
                 \module FooBar
               }

               \func bar : Nat -> Nat => (1 Bar.`foo` 1) Bar.`foo
            """, """
               \module Bar \where {
                 \module FooBar \where {
                   \func foo (a b : Nat) => 101
                 }
               }

               \func bar : Nat -> Nat => Bar.FooBar.foo (Bar.FooBar.foo 1 1)               
            """, "A", "Bar.FooBar")

    fun testMoveOutOfClass1() =
            doTestMoveRefactoring(""" 
               \class C {
                 | f : Nat
                 \func foo{-caret-} (a : Nat) (this : a = f) => {?}
               }

               \func goo (CI : C) => C.foo 101 
            """, """
               \class C {
                 | f : Nat
               }

               \func goo (CI : C) => foo 101

               \func foo {this1 : C} (a : Nat) (this : a = f {this1}) => {?}
            """, "Main", "")

    fun testMoveOutOfClass2() =
            doTestMoveRefactoring("""
                \class C {
                  | foo : Nat
                  
                  \data D{-caret-} (p : foo = foo)
                }
                
                \module M
            """, """
                \class C {
                  | foo : Nat 
                }
                
                \module M \where {
                  \data D {this : C} (p : foo {this} = foo {this})
                }
            """, "Main", "M")

    fun testMoveOutOfClass3() =
            doTestMoveRefactoring("""
                \class C1 (E : \Type)

                \class C2 \extends C1 {
                  \func foo{-caret-} (e : E) => {?}
                    \where 
                      \func bar (y : E) => {?}
                }

                \module M
            """, """
                \class C1 (E : \Type)

                \class C2 \extends C1 {}

                \module M \where {
                  \func foo {this : C2} (e : C1.E {this}) => {?}
                    \where 
                      \func bar {this : C2} (y : C1.E {this}) => {?}
                }
            """, "Main", "M")


    fun testMoveOutOfClass4() =
            doTestMoveRefactoring("""
               \class C {
                 | carrier : \Type

                 \func foo : Nat => 101 \where {
                   \func bar{-caret-} (X : carrier) => 101 
                 }
  
                 \func foobar => foo.bar
               } 
            """, """
               \class C {
                 | carrier : \Type

                 \func foo : Nat => 101 \where {
                 }

                 \func foobar => bar {\this}
               } \where {
                 \func bar {this : C} (X : carrier {this}) => 101
               } 
            """, "Main", "C")

    fun testMoveOutOfRecord0() =
            doTestMoveRefactoring("""
               \record R {
                 \func F => 101

                 \func lol{-caret-} : Nat => F
               }

               \module M
            """, """
               \record R {
                 \func F => 101
               }

               \module M \where {
                 \func lol {this : R} : Nat => R.F {this}
               }
            """, "Main", "M")

    fun testMoveOutOfRecord1() =
            doTestMoveRefactoring("""
               \class C1 (E : \Type)

               \record C2 \extends C1 {
                 | bar : Nat
                 
                 \func foo{-caret-} (e : E) => e
                   \where
                     \func goo => bar
               }
               
               \func usage (r : C2 Nat) => r.foo 101 Nat.+ C2.foo.goo {r} 

               \module M
            """, """
               \class C1 (E : \Type)

               \record C2 \extends C1 {
                 | bar : Nat 
               }
               
               \func usage (r : C2 Nat) => M.foo {r} 101 Nat.+ M.foo.goo {r}

               \module M \where {
                 \func foo {this : C2} (e : C1.E {this}) => e
                   \where
                     \func goo {this : C2} => bar {this}
               }  
            """, "Main", "M")

    fun testMoveOutOfRecord2() =
            doTestMoveRefactoring("""
                \class C1 (E : \Type) {
                  | field1 : Nat
                }

                \module M \where {
                  \class C2 \extends C1 {
                    | field2 : C1
                  }

                  \record R \extends C2 {
                    \func foo{-caret-} (a : C1) => (field1, field2.field1, M.C2.field2, a.field1)  
                  }
                }

                \module M2
            """, """
                \class C1 (E : \Type) {
                  | field1 : Nat   
                }

                \module M \where {
                  \class C2 \extends C1 {
                    | field2 : C1
                  }

                  \record R \extends C2 {}
                }

                \module M2 \where {
                  \func foo {this : M.R} (a : C1) => (field1 {this}, field1 {M.field2 {this}}, M.C2.field2 {this}, field1 {a})
                }
            """, "Main", "M2", typecheck = true)

    fun testMoveOutOfRecord3() =
            doTestMoveRefactoring("""
               \record C1 (E : \Type) {
                 \func fubar1 (n : Nat) => 101
               }

               \module M \where {
                 \class C2 \extends C1 {
                   \func fubar2 (n : Nat) => 101
                 }

                 \record S \extends C2 {
                   \func fubar3 (n m : Nat) => 101
                   
                   \func foo2{-caret-} => 
                     (C1.fubar1 1, 
                      M.C2.fubar2 1, 
                      fubar3 1, 
                      zoo M.C2.fubar2,
                      suc (M.C2.fubar2 1),
                      C1.fubar1 {\this}, 
                      C1.fubar1 1 = C1.fubar1 1)
                 }
               }
               
               \func zoo (f : Nat -> Nat) => f 0

               \module M2
            """, """
               \record C1 (E : \Type) {
                 \func fubar1 (n : Nat) => 101
               }

               \module M \where {
                 \class C2 \extends C1 {
                   \func fubar2 (n : Nat) => 101
                 } 

                 \record S \extends C2 {
                   \func fubar3 (n m : Nat) => 101
                 }
               }
               
               \func zoo (f : Nat -> Nat) => f 0

               \module M2 \where {
                 \func foo2 {this : M.S} =>
                   (C1.fubar1 {this} 1,
                    M.C2.fubar2 {this} 1,
                    M.S.fubar3 {this} 1,
                    zoo (M.C2.fubar2 {this}),
                    suc (M.C2.fubar2 {this} 1),
                    C1.fubar1 {this},
                    C1.fubar1 {this} 1 = C1.fubar1 {this} 1)
               }
            """, "Main", "M2")

    fun testMoveOutOfRecord4() =
            doTestMoveRefactoring("""
               \record C1 (E : \Type) {
                 \func fubar1 (n : Nat) => 101
               }

               \module M \where {
                 \class C2 \extends C1 {
                   \func fubar2 (n : Nat) => 101
                 }

                 \class S \extends C2 {
                   \func fubar3 (n : Nat) => 101
                   
                   \func foo2{-caret-} => (C1.fubar1 1, M.C2.fubar2 1, fubar3 1)
                 }
               }

               \module M2 
            """, """
               \record C1 (E : \Type) {
                 \func fubar1 (n : Nat) => 101
               }

               \module M \where {
                 \class C2 \extends C1 {
                   \func fubar2 (n : Nat) => 101
                 }

                 \class S \extends C2 {
                   \func fubar3 (n : Nat) => 101
                 }
               }

               \module M2 \where {
                 \func foo2 {this : M.S} => (C1.fubar1 {this} 1, M.C2.fubar2 {this} 1, M.S.fubar3 {this} 1)
               }
            """, "Main", "M2")

    fun testMoveOutOfRecord5() =
            doTestMoveRefactoring("""
               \record R {
                 \func bar (n m : Nat) => {?}
                 
                 \func lol {A : \Type} (n m : A) => n
                 
                 \func fubar (n : Nat) {X : \Type} => n
                 
                 \func succ (n : Nat) => Nat.suc n
                 
                 \func \infixl 1 ibar (n m : Nat) => n

                 \func foo{-caret-} : Nat -> Nat => `bar` 1
                 
                 \func foo2 : Nat -> Nat => `bar 1
                 
                 \func foo3 : Nat -> Nat => 1 `bar`
                 
                 \func foo4 : Nat -> Nat => 1 `bar
                 
                 \func foo5 : Nat => 1 `bar` succ 2 
                 
                 \func foo6 : Nat => 1 `bar 2
                 
                 \func foo7 : Nat => 1 `lol` {\this} {Nat} 2
                 
                 \func foo8 : Nat => 1 `fubar {Nat}
                 
                 \func foo9 : Nat => 1 `bar` 2 `bar` 3
                 
                 \func foo10 : Nat => 1 `bar` 2 `bar 3
                 
                 \func foo11 => 1 `bar 2 `bar` 3
                 
                 \func foo12 : Nat => succ 2 `bar 3
                 
                 \func foo13 : Nat => succ {- lol -} 2 `bar 3
                 
                 \func foo14 => \lam (n : Nat) => `bar n
                 
                 \func foo15 : Nat -> Nat => 1 ibar
                 
                 \func foo16 : Nat => 1 ibar 2
                 
                 \func foo17 : Nat -> Nat => (`bar 2)
                 
                 \func foo18 : Nat => ((bar)) {\this} 1 2
                 
                 \func foo19 => (`bar 202 : Nat -> Nat)
               }
            """, """
               \record R {
                 \func bar (n m : Nat) => {?}

                 \func lol {A : \Type} (n m : A) => n

                 \func fubar (n : Nat) {X : \Type} => n

                 \func succ (n : Nat) => Nat.suc n

                 \func \infixl 1 ibar (n m : Nat) => n
               }

               \func foo {this : R} : Nat -> Nat => R.bar {this} 1

               \func foo2 {this : R} : Nat -> Nat => \lam n => R.bar {this} n 1

               \func foo3 {this : R} : Nat -> Nat => R.bar {this} 1

               \func foo4 {this : R} : Nat -> Nat => R.bar {this} 1

               \func foo5 {this : R} : Nat => R.bar {this} 1 (R.succ {this} 2)

               \func foo6 {this : R} : Nat => R.bar {this} 1 2

               \func foo7 {this : R} : Nat => R.lol {this} {Nat} 1 2

               \func foo8 {this : R} : Nat => R.fubar {this} 1 {Nat}

               \func foo9 {this : R} : Nat => R.bar {this} 1 (R.bar {this} 2 3)

               \func foo10 {this : R} : Nat => R.bar {this} 1 (R.bar {this} 2 3)

               \func foo11 {this : R} => R.bar {this} (R.bar {this} 1 2) 3

               \func foo12 {this : R} : Nat => R.bar {this} (R.succ {this} 2) 3

               \func foo13 {this : R} : Nat => R.bar {this} (R.succ {this} {- lol -} 2) 3

               \func foo14 {this : R} => \lam (n : Nat) => \lam n1 => R.bar {this} n1 n

               \func foo15 {this : R} : Nat -> Nat => R.ibar {this} 1

               \func foo16 {this : R} : Nat => 1 R.ibar {this} 2

               \func foo17 {this : R} : Nat -> Nat => (\lam n => R.bar {this} n 2)

               \func foo18 {this : R} : Nat => R.bar {this} 1 2

               \func foo19 {this : R} => (\lam n => R.bar {this} n 202 : Nat -> Nat)
            """, "Main", "", "Main",
                    "R.foo", "R.foo2", "R.foo3", "R.foo4", "R.foo5", "R.foo6", "R.foo7", "R.foo8",
                    "R.foo9", "R.foo10", "R.foo11", "R.foo12", "R.foo13", "R.foo14", "R.foo15", "R.foo16",
                    "R.foo17", "R.foo18", "R.foo19")

    fun testMoveOutOfRecord6() =
            doTestMoveRefactoring("""
               \record R {
                 | bar : Nat
                 
                 \data D{-caret-}
                   | C1 (p : bar = bar)
                   | C2 
  
                 \func foo (a : D) : D \with
                   | C1 x => C1 x
                   | C2 => C2  
               }

               \module M
            """, """
               \record R {
                 | bar : Nat

                 \func foo (a : D {\this}) : D {\this} \with
                   | C1 x => C1 {\this} x
                   | C2 => C2 {\this}
               } \where {
                 \open M (C1, C2, D)
               }

               \module M \where {
                 \data D {this : R}
                   | C1 (p : bar {this} = bar {this})
                   | C2
               }
            """, "Main", "M")

    fun testMoveOutOfRecord7() =
            doTestMoveRefactoring("""
                \record R {
                  \data D
                    
                  \func foo{-caret-} (a : \Sigma D Nat) : D => a.1   
                }
                
                \module M
            """, """
                \record R {
                  \data D
                }

                \module M \where {
                  \func foo {this : R} (a : \Sigma (R.D {this}) Nat) : R.D {this} => a.1
                }
            """, "Main", "M")

    fun testMoveIntoDynamic1() =
            doTestMoveRefactoring("""
               \class C1 {
                 | carrier : \Type
               }
               
               \func foo{-caret-} => 1  
            """, """
               \class C1 {
                 | carrier : \Type
                 
                 \func foo => 1
               }
            """, "Main", "C1", targetIsDynamic = true)

    fun testMoveIntoDynamic2() =
            doTestMoveRefactoring("""  
               -- ! Main.ard
               
               \class C1 {
                 | carrier : \Type
               }
                 
               \func foo => 1{-caret-}  
               
               \func goo => 2
            """, """
               \class C1 {
                 | carrier : \Type
                 
                 \func foo => 1
                 
                 \func goo => 2
               }
            """, "Main", "C1", "Main", "foo", "goo", targetIsDynamic = true)

    fun testMoveIntoDynamic3() =
            doTestMoveRefactoring("""
               \class C1
                 | carrier : \Type
                 
               \func foo{-caret-} => 1  
            """, """
               \class C1 {
                 | carrier : \Type
                 
                 \func foo => 1
               } 
            """, "Main", "C1", targetIsDynamic = true)

    fun testMoveIntoDynamic4() =
            doTestMoveRefactoring("""
               \class C1
                 
               \func foo{-caret-} => 1  
            """, """
               \class C1 {
                 \func foo => 1
               }
            """, "Main", "C1", targetIsDynamic = true)

    fun testMoveIntoDynamic5() =
            doTestMoveRefactoring("""
                \class C1
                  
                -- | fubar
                \func fubar{-caret-} => 101
            """, """
                \class C1 {
                  -- | fubar
                  
                  \func fubar => 101
                }
            """, "Main", "C1", targetIsDynamic = true)

    fun testMoveIntoUnrelatedClass() =
            doTestMoveRefactoring("""
               \record C {
                 | number : Nat
                 
                 \func foo{-caret-} {n : Nat} => (bar Nat.+ n, bar {\this} Nat.+ n, number Nat.+ n)
                 
                 \func bar => 202
                 
                 \func foobar => foo
               }
               
               \class E \extends C {
                 \func fubar => foo {\this} {101}
               }
                
               \class D {
                 \func fubar => C.foo {\new C {| number => 1}} {101}
               }
            """, """
               \record C {
                 | number : Nat

                 \func bar => 202

                 \func foobar => foo {{?}} {\this}
               } \where {
                 \open D (foo)
               }

               \class E \extends C {
                 \func fubar => D.foo {{?}} {\this} {101}
               }

               \class D {
                 \func fubar => foo {\this} {\new C {| number => 1}} {101}

                 \func foo {this : C} {n : Nat} => (C.bar {this} Nat.+ n, C.bar {this} Nat.+ n, number {this} Nat.+ n)
               }
            """, "Main", "D", targetIsDynamic = true) //Note: There are instance inference errors in the resulting code

    fun testMoveIntoDescendant() =
            doTestMoveRefactoring(""" 
               \record C {
                 | number : Nat
                 
                 \func foo{-caret-} => bar Nat.+ (bar {\this}) Nat.+ number
                 
                 \func bar => 202
                 
                 \func foobar => foo
               }
                
               \class D \extends C {
                 \func fubar => C.foo {\new C {| number => 1}}
               }
            """, """
               \record C {
                 | number : Nat

                 \func bar => 202

                 \func foobar => foo {{?}}
               } \where {
                 \open D (foo)
               }

               \class D \extends C {
                 \func fubar => foo {{?} {-\new C {| number => 1}-}}

                 \func foo => bar Nat.+ (bar {\this}) Nat.+ number
               }
            """, "Main", "D", targetIsDynamic = true) //Note: There are instance inference errors in the resulting code

    fun test456() = doTestMoveRefactoring(
        """
           -- ! A.ard
           \func foo{-caret-} => 42
                       
           -- ! B.ard           
           \import A
           
           -- ! Main.ard
           \import A
           
           \func foobar => foo
        """,
        """
           \import A
        """,
        "Main", "", fileToCheck = "B.ard"
    )

    fun testMoveIntoDescendant2() = doTestMoveRefactoring("""
       \class C {foo : Nat} {
         \func ba{-caret-}r (a : Nat) => a Nat.+ C.foo

         \func usage => bar 101
       }

       \class D \extends C {
         \func lol => bar
       }
    """, """
       \class C {foo : Nat} {
         \func usage => bar {{?}} 101
       } \where {
         \open D (bar)
       }

       \class D \extends C {
         \func lol => bar {\this}

         \func bar (a : Nat) => a Nat.+ C.foo
       }
    """, "Main", "D", targetIsDynamic = true)

    fun testMoveIntoAncestor() = doTestMoveRefactoring("""
       \class C {
         | number : Nat
       } \where {
         \func bar {m : Nat} => D.foo {\new D 42 101} {m}
       }

       \class D \extends C {
         | number2 : Nat
       
         \func foo{-caret-} {n : Nat} => number Nat.+ n Nat.+ number2
       }
    """, """
       \class C {
         | number : Nat
         
         \func foo {n : Nat} => number Nat.+ n Nat.+ number2 {{?}}
       } \where {
         \func bar {m : Nat} => D.foo {\new D 42 101} {m}
       }

       \class D \extends C {
         | number2 : Nat
       }
    """, "Main", "C", targetIsDynamic = true)

    fun testInstanceRefFix() =
            doTestMoveRefactoring("""
               -- ! A.ard
               \record C (f : Nat -> Nat)
                
               \func bar : C => \new C (\lam n => suc n)
                
               \func foo => bar.f 1
               -- ! Main.ard
               \module M{-caret-}
            """, """
               \import A
               
               \module M \where {
                 \func foo => bar.f 1
               } 
            """, "Main", "M", "A", "foo")

    val testMOR8Header =
            """\record C (f : Nat -> Nat)
                
\record P (a b : Nat)
               
\record C2 (f : P)
               
\record C3 (f : \Sigma Nat Nat)
                
\record D {
  \func bar1 : \Sigma Nat Nat => (1, 2)
                 
  \func bar2 : C => \new C (\lam n => suc n)
                 
  \func bar3 : C2 => \new C2 (\new P 1 2)
                 
  \func bar4 : C3 => \new C3 (1, 2)"""

    fun testMoveOutOfRecord8() =
            doTestMoveRefactoring("""
$testMOR8Header

  \func fubar{-caret-} => (bar1.1,
                           suc bar1.1,
                           bar2.f 1, 
                           bar3.f.a,
                           suc bar3.f.a,
                           bar4.f.1,
                           suc bar4.f.1)
}""", """
$testMOR8Header
}  

\func fubar {this : D} => ((D.bar1 {this}).1,
                           suc (D.bar1 {this}).1,
                           C.f {D.bar2 {this}} 1,
                           P.a {C2.f {D.bar3 {this}}},
                           suc (P.a {C2.f {D.bar3 {this}}}),
                           (C3.f {D.bar4 {this}}).1,
                           suc (C3.f {D.bar4 {this}}).1)""", "Main", "")

    fun testMoveOutOfClass5() = doTestMoveRefactoring("""
       \class Foo {
         | n : Nat

         \func {-caret-}succ : Foo => \new Foo (Nat.suc n)

         \func usage3 => succ.succ.succ
       }

       \func foo (x : Foo) => Foo.succ.succ.succ
    """, """
       \class Foo {
         | n : Nat

         \func usage3 => succ {succ {succ {\this}}}
       } \where {
         \open foo (succ)
       }

       \func foo (x : Foo) => succ {succ {succ}} 
         \where {
           \func succ {this : Foo} : Foo => \new Foo (Nat.suc (n {this}))
         }  
    """, "Main", "foo")

    fun testInstances() = doTestMoveRefactoring("""
               \module Foo
               \instance foo{-caret-} => 1 
            """, """ 
               \module Foo \where {
                 \instance foo => 1
               }
               
               \open Foo (foo) 
            """, "Main", "Foo")

    fun testOpenMode() = doTestMoveRefactoring("""
           \module Foo \where {
             \func foo => 101
           }

           \module Zoo \where {
             \open Foo

             \func bar{-caret-} => foo
           }

           \module Bar \where {
             \func foo => 102
           }
        """, """
           \module Foo \where {
             \func foo => 101
           }

           \module Zoo \where {
             \open Foo
           }

           \module Bar \where {
             \func foo => 102
             
             \func bar => Foo.foo
           } 
        """, "Main", "Bar", useOpenCommands = true)

    fun testParentImplicitParams() = doTestMoveRefactoring("""
       \func foo (x : Nat) => x
         \where
           \func bar{-caret-} => x
            
       \module M
    """, """
       \func foo (x : Nat) => x
            
       \module M \where {
         \func bar {x : Nat} => x
       }
    """, "Main", "M", typecheck = true)

    fun testParentImplicitParams2() = doTestMoveRefactoring("""
       \func foo (x : Nat) => x
         \where
           \func fubar (y : Nat) => y
             \where
               \func bar{-caret-} => x Nat.+ y

       \func lol => foo.fubar.bar {101} {42}
    """, """
       \func foo (x : Nat) => x
         \where {
           \func fubar (y : Nat) => y
           
           \func bar {y : Nat} => x Nat.+ y
         }   
       
       \func lol => foo.bar {101} {42}
    """, "Main", "foo", typecheck = true)

    fun testParentImplicitClass() = doTestMoveRefactoring("""
       \class C {
         | foo : Nat
         \func lol (bar : Nat) => bar \where
           \func fubar{-caret-} => foo Nat.+ bar
       }
       
       \module M
    """, """
       \class C {
         | foo : Nat
         \func lol (bar : Nat) => bar \where {}
       }
       
       \module M \where {
         \func fubar {this : C} {bar : Nat} => foo {this} Nat.+ bar
       }
    """, "Main", "M", typecheck = true)

    fun testParentImplicitUsage() = doTestMoveRefactoring("""
       \func foo (x : Nat) => x
         \where {
           \func bar{-caret-} (p : x = x) : Nat => \case x \with {
             | 0 => 0
             | suc n' => test n'
           }

           \func usage => bar idp

           \func test (y : Nat) => x Nat.+ y
         }

       \module M \where {
         \func fubar : Nat => foo.bar {101} idp
       }               
    """, """
       \func foo (x : Nat) => x
         \where {
           \open M (bar)
           
           \func usage => bar {x} idp
           
           \func test (y : Nat) => x Nat.+ y
         }

       \module M \where {
         \func fubar : Nat => bar {101} idp
         
         \func bar {x : Nat} (p : x = x) : Nat => \case x \with {
           | 0 => 0
           | suc n' => foo.test {x} n'
         }
       }
    """, "Main", "M", typecheck = true)

    fun testComplicatedUsage() = doTestMoveRefactoring("""
       \func foo (x : Nat) => x
         \where {
           \func bar {y : Nat} => y \where {
             \func foobar (z : Nat) => z \where {
               \func fubar{-caret-} (w : Nat) =>
                 usage_x 0 Nat.+ usage_y 0 Nat.+ usage_z Nat.+ w

               \func usage_z => z
               \func usage_fubar1 => fubar (fubar 1)
             }

           \func usage_y (p : Nat) => y Nat.+ p

           \func usage_fubar2 => foobar.fubar {x} {y} {0} (foobar.fubar {x} {y} {0} 0)
         }

         \func usage_x (q : Nat) => x Nat.+ q

         \func usage_fubar3 => bar.foobar.fubar {x} {0} {0} (bar.foobar.fubar {x} {0} {0} 0)
       }

       \module M \where {
         \func usage_outer : Nat => foo.bar.foobar.fubar {1} {1} {1} 1
       }
    """,  """
      \func foo (x : Nat) => x
        \where {
          \func bar {y : Nat} => y \where {
            \func foobar (z : Nat) => z \where {
              \open M (fubar)

              \func usage_z => z
              \func usage_fubar1 => fubar {x} {y} {z} (fubar {x} {y} {z} 1)
            }

          \func usage_y (p : Nat) => y Nat.+ p

          \func usage_fubar2 => M.fubar {x} {y} {0} (M.fubar {x} {y} {0} 0)
        }

        \func usage_x (q : Nat) => x Nat.+ q

        \func usage_fubar3 => M.fubar {x} {0} {0} (M.fubar {x} {0} {0} 0)
      }

      \module M \where {
        \func usage_outer : Nat => fubar {1} {1} {1} 1

        \func fubar {x y z : Nat} (w : Nat) =>
          foo.usage_x {x} 0 Nat.+ foo.bar.usage_y {y} 0 Nat.+ foo.bar.foobar.usage_z {z} Nat.+ w
      }
    """, "Main", "M", typecheck = true)

    fun testComplicatedUsage2() = doTestMoveRefactoring("""
       \class Foo {u : Nat} {
         | v : Nat

         \func foo {w : Nat} => u Nat.+ v Nat.+ w \where {
           \func \infixl 1 {-caret-}+++ (x y : Nat) => u Nat.+ v Nat.+ w Nat.+ x Nat.+ y

           \func usage1 (a b c : Nat) => a +++ b +++ c
         }

         \func usage2 (a b c : Nat) => a foo.+++ {_} {0} b foo.+++ {_} {0} c
       }

       \class Bar \extends Foo {
         \func lol1 => 101 Foo.foo.+++ {_} {0} 102
       } \where {
         \func lol2 => 101 Foo.foo.+++ {\new Foo {101} 102} {0} 102
       }
    """, """
       \class Foo {u : Nat} {
         | v : Nat

         \func foo {w : Nat} => u Nat.+ v Nat.+ w \where {
           \func usage1 (a b c : Nat) => a +++ {{?}} {w} b +++ {{?}} {w} c

           \open Bar (+++)
         }

         \func usage2 (a b c : Nat) => a Bar.+++ {{?}} {0} b Bar.+++ {{?}} {0} c
       }

       \class Bar \extends Foo {
         \func lol1 => 101 +++ {{?}} {0} 102

         \func \infixl 1 +++ {w : Nat} (x y : Nat) => u Nat.+ v Nat.+ w Nat.+ x Nat.+ y
       } \where {
         \func lol2 => 101 +++ {{?} {-\new Foo {101} 102-}} {0} 102
       }       
    """, "Main", "Bar", typecheck = true, targetIsDynamic = true)

    fun testComplicatedUsage3() = doTestMoveRefactoring("""
       \class Foo {u : Nat} {
         | v : Nat

         \func foo (w : Nat) => u Nat.+ v Nat.+ w \where {
           \func \infixl 1 {-caret-}+++ (x y : Nat) => u Nat.+ v Nat.+ w Nat.+ x Nat.+ y

           \func usage1 (a b c : Nat) => a +++ b +++ c
         }

         \func usage2 (a b c : Nat) => foo.+++ {_} {0} (foo.+++ {_} {0} a b) c
       }

       \class Bar \extends Foo {
         \func lol1 => (Foo.foo.+++ {_} {0}) 101 102
       } \where {
         \func lol2 => Foo.foo.+++ {\new Foo {101} 102} {0} 101 102
       }
    """, """
       \class Foo {u : Nat} {
         | v : Nat

         \func foo (w : Nat) => u Nat.+ v Nat.+ w \where {
           \func usage1 (a b c : Nat) => a +++ {\this} {w} b +++ {\this} {w} c

           \open Bar (+++)
         }

         \func usage2 (a b c : Nat) => (a Bar.+++ {\this} {0} b) Bar.+++ {\this} {0} c
       }

       \class Bar \extends Foo {
         \func lol1 => (+++ {\this} {0}) 101 102
       } \where {
         \func lol2 => 101 +++ {\new Foo {101} 102} {0} 102

         \func \infixl 1 +++ {this : Foo} {w : Nat} (x y : Nat) => u {this} Nat.+ v {this} Nat.+ w Nat.+ x Nat.+ y
       }
    """, "Main", "Bar", typecheck = true, targetIsDynamic = false)

    fun testLongName() = doTestMoveRefactoring("""
       \class Foo {
         | n : Nat

         \func {-caret-}succ : Foo => \new Foo (Nat.suc n)

         \func \infix 1 +++ (a b : Nat) => 101

         \func lol => 101 succ.succ.+++ 102
       }
       
       \module M
    """, """
       \class Foo {
         | n : Nat

         \func \infix 1 +++ (a b : Nat) => 101

         \func lol => 101 +++ {succ {succ {\this}}} 102
       } \where {
         \open M (succ)
       }

       \module M \where {
         \func succ {this : Foo} : Foo => \new Foo (Nat.suc (n {this}))
       }
    """, "Main", "M")

    fun testMutuallyRecursive() = doTestMoveRefactoring("""
       \class Foo {
         | n : Nat
         \func foo1{-caret-} (m : Nat) : Nat \with
           | zero => zero
           | suc m => foo2 m Nat.+ n

         \func foo2 (this : Nat) : Nat \with
           | zero => zero
           | suc m => foo1 m Nat.+ n
       } 
    """, """
       \class Foo {
         | n : Nat         
       } \where {
         \func foo1 {this : Foo} (m : Nat) : Nat \with
           | {this}, zero => zero
           | {this}, suc m => foo2 {this} m Nat.+ n {this}

         \func foo2 {this1 : Foo} (this : Nat) : Nat \with
           | {this1}, zero => zero
           | {this1}, suc m => foo1 {this1} m Nat.+ n {this1}
       }
    """, "Main", "Foo", "Main",
        "Foo.foo1", "Foo.foo2", targetIsDynamic = false)

    fun testMoveOutClass6() = doTestMoveRefactoring("""
       \class Foo {
         \data Vec{-caret-} {X : \Type} (n : Nat) \with
           | {X}, zero => nullV
           | {X}, suc n => consV {X} (Vec {_} {X} n)

         \func lol => consV {_} {Nat} {1} {101} (consV {_} {_} {0} {101} nullV)
       }
       
       \class Bar {
       }
    """, """
       \class Foo {
         \func lol => consV {{?}} {\this} {Nat} {1} {101} (consV {{?}} {\this} {_} {0} {101} (nullV {{?}} {\this}))
       } \where {
         \open Bar (Vec, consV, nullV)
       }

       \class Bar {
         \data Vec {this : Foo} {X : \Type} (n : Nat) \with
           | {this}, {X}, zero => nullV
           | {this}, {X}, suc n => consV {X} (Vec {this} {{?}} {X} n)
       } 
    """, "Main", "Bar", targetIsDynamic = true)

    fun testMoveOutOfClassSimple() = doTestMoveRefactoring("""
       \class C {
         | field : Nat
         \func foo{-caret-} => field
       }

       \func test {c : C} => C.foo
    """, """
       \class C {
         | field : Nat
       }

       \func test {c : C} => foo 
       
       \func foo {this : C} => field {this}
    """, "Main", "")

    fun testImportPatterns() = doTestMoveRefactoring("""
       -- ! A.ard
       \func lol => 42
       
       -- ! B.ard
       \import Main
       
       \func lol : Bool => true
       
       -- ! Main.ard
       \import B
       \data Bool | true | false
       
       \func f{-caret-}oobar : Nat => \case lol \with {
         | true => 1
         | false => 0
       }
    """, """
       \import B
       \import Main
       
       \func lol => 42
       
       \func foobar : Nat => \case B.lol \with {
         | true => 1
         | false => 0
       }
    """, "A", "", fileToCheck = "A.ard")

}