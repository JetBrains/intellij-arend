package org.arend.refactoring

class ArendMoveStaticMemberTest : ArendMoveTestBase() {

    fun testSimpleMove1() =
            testMoveRefactoring("""
             --! Main.ard
            {- | Block
             - Doc -}
            \func abc{-caret-} => 1
            \module def \where {}
            """, """
            \open def (abc)

            \module def \where {
              {- | Block
               - Doc -}
              \func abc => 1
            }
            """, "Main", "def")

    fun testSimpleMove2() =
            testMoveRefactoring("""
             --! Main.ard
             -- | LineDoc 1
            \func abc{-caret-} => 1
            \func foo => 2 \where
              \func bar => 3
            """, """
            \open foo (abc)

            \func foo => 2 \where {
              \func bar => 3

              -- | LineDoc 1
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
            """, """
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
            \open Foo (MyNat, myCons)

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
            """, """
                \import Main
                \open Nat
                \open goo (Foo)

                \module Bar \where {
                  \open Foo

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
            """, """
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
            testMoveRefactoring("""
               --! Foo.ard
               \import Main
               \data D1 {-caret-}
                 | A
                 | B

               \data D2
                 | A
                 | B

               --! Main.ard
               -- Empty File

            """, """
               \import Main
            """, "Main", "", "Foo", "D1", "D2")

    fun testMultipleMove3() =
            testMoveRefactoring("""
               --! Foo.ard
               \import Main ()
               \data D1 {-caret-}
                 | A
                 | B

               \data D2
                 | A
                 | B

               --! Main.ard
               -- Empty File

            """, """
               \import Main (D1, D2)
            """, "Main", "", "Foo", "D1", "D2")

    fun testMultipleMove4() =
            testMoveRefactoring("""
               --! Foo.ard
               \data D1 {-caret-}
                 | A
                 | B

               \data D2
                 | A
                 | B

               \func foo (d1 : D1) (d2 : D2) \elim d1, d2
                 | D1.A, D2.A => 1
                 | D1.B, D2.B => 0

               --! Main.ard
               -- Empty File

            """, """
               \import Main

               \func foo (d1 : D1) (d2 : D2) \elim d1, d2
                 | A, D2.A => 1
                 | B, D2.B => 0
            """, "Main", "", "Foo", "D1", "D2")

    fun testMultipleRenaming1() =
            testMoveRefactoring("""
               --! Main.ard
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
                 \open Bar (foo)
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
            testMoveRefactoring("""
               --! Foo.ard
               \func foo => 1

               --! Bar.ard
               {- Empty file -}
               --! Main.ard
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
            testMoveRefactoring("""
               --! Main.ard
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
            testMoveRefactoring("""
                --! Main.ard
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
               \open FooBar (C, foo, D, c1, c2)

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
            testMoveRefactoring("""
               --! A.ard
               \func foo => 1
               --! B.ard
               -- Empty file
               --! Main.ard
               \import A{-caret-}

               \func lol => foo
        """, """
               \import A
               \import B

               \func lol => foo
        """, "B", "", "A", "foo")

    fun testMinimalImport1() =
            testMoveRefactoring("""
               --! A.ard
               \func foo => 1

               \func bar => 2
               --! B.ard
               \func bar => 3
               --! Main.ard
               \import A{-caret-}

               \func lol => foo
        """, """
               \import A
               \import B (foo)

               \func lol => foo
        """, "B", "", "A", "foo")

    fun testPlainImportFix2() =
            testMoveRefactoring("""
               --! A.ard
               \func foo => 1

               \func bar => 2
               --! B.ard
               \func bar => 3
               --! Main.ard
               \import A \hiding (bar){-caret-}

               \func lol => foo
        """, """
               \import A \hiding (bar)
               \import B

               \func lol => foo
        """, "B", "", "A", "foo")

    fun testMoveOutside() =
            testMoveRefactoring("""
               --! A.ard
               \module Foo \where {
                 \open Bar (fooBar)
               }

               \module Bar \where {
                 \func fooBar{-caret-} => 1
               }
            ""","""
               \import A

               \module Foo \where {
                 \open A (fooBar)
               }

               \module Bar \where {
               }

               \func fooBar => 1
            """, "A", "")

    fun testClassField() =
            testMoveRefactoring("""
               --! A.ard
               \class Foo{-caret-} (U : Nat) {
                 \func foo => U
               } \where {
                 \func bar => U
               }

               \module Bar \where {}
            """, """
               \open Bar (Foo)

               \module Bar \where {
                 \class Foo (U : Nat) {
                   \func foo => U
                 } \where {
                   \func bar => U
                 }
               }
            """, "A", "Bar")

    fun testInfixArguments1() =
            testMoveRefactoring("""
               --! A.ard
               \module Bar \where {
                 \func foo{-caret-} (a b : Nat) => 101
 
                 \module FooBar
               }

               \func bar : Nat -> Nat => (1 Bar.`foo` 1) Bar.`foo
            """, """
               \module Bar \where {
                 \open FooBar (foo)

                 \module FooBar \where {
                   \func foo (a b : Nat) => 101
                 }
               }

               \func bar : Nat -> Nat => (1 Bar.FooBar.`foo` 1) Bar.FooBar.`foo 
            """, "A", "Bar.FooBar")

    fun testMoveOutOfClass1() =
            testMoveRefactoring(""" 
               \class C {
                 | f : Nat
                 \func foo{-caret-} (a : Nat) (b : a = f) => \lam this => \this
               }

               \func goo (CI : C) => C.foo 101 
            """, """
               \class C {
                 | f : Nat 
               }

               \func goo (CI : C) => foo 101 
               
               \func foo {this1 : C} (a : Nat) (b : a = f) => \lam this => this1
            """, "Main", "")

    fun testMoveOutOfClass2() =
            testMoveRefactoring("""
                \class C {
                  | foo : Nat
                  
                  \data D{-caret-} (p : foo = foo)
                }
                
                \module M
            """, """
                \class C {
                  | foo : Nat 
                } \where {
                  \open M (D)
                }
                
                \module M \where {
                  \data D {this : C} (p : foo = foo)
                }
            """, "Main", "M")

    fun testMoveOutOfClass3() =
            testMoveRefactoring("""
                \class C1 (E : \Type)

                \class C2 \extends C1 {
                  \func foo{-caret-} (e : E) => {?}
                    \where 
                      \func bar (y : E) => {?}
                }

                \module M 
            """, """
                \class C1 (E : \Type)

                \class C2 \extends C1 {} \where {
                  \open M (foo)
                }

                \module M \where {
                  \func foo {this : C2} (e : this) => {?}
                    \where 
                      \func bar {this : C2} (y : this) => {?}
                }
            """, "Main", "M")


    fun testMoveOutOfClass4() =
            testMoveRefactoring("""
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

                 \func foobar => bar
               } \where {
                 \func bar {this : C} (X : carrier) => 101
               } 
            """, "Main", "C")

    fun testMoveOutOfRecord0() =
            testMoveRefactoring("""
               \record R {
                 \func F => 101

                 \func lol{-caret-} : Nat => F
               }

               \module M 
            """, """
               \record R {
                 \func F => 101
               } \where {
                 \open M (lol)
               }

               \module M \where {
                 \func lol {this : R} : Nat => R.F {this}
               } 
            """, "Main", "M")

    fun testMoveOutOfRecord1() =
            testMoveRefactoring("""
               \class C1 (E : \Type)

               \record C2 \extends C1 {
                 | bar : Nat
                 
                 \func foo{-caret-} (e : E) => {?}
                   \where
                     \func goo => bar
               }

               \module M 
            """, """
               \class C1 (E : \Type)

               \record C2 \extends C1 {
                 | bar : Nat 
               } \where {
                 \open M (foo)
               }

               \module M \where {
                 \func foo {this : C2} (e : this.E) => {?}
                   \where
                     \func goo {this : C2} => this.bar
               }  
            """, "Main", "M")

    fun testMoveOutOfRecord2() =
            testMoveRefactoring("""
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

                  \record R \extends C2 {} \where {
                    \open M2 (foo)
                  }
                }

                \module M2 \where {
                  \func foo {this : M.R} (a : C1) => (this.field1, this.field2.field1, this.field2, a.field1)
                }
            """, "Main", "M2")

    fun testMoveOutOfRecord3() =
            testMoveRefactoring("""
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
                 } \where { 
                   \open M2 (foo2)
                 }
               }
               
               \func zoo (f : Nat -> Nat) => f 0

               \module M2 \where {
                 \func foo2 {this : M.S} => 
                   (C1.fubar1 {this} 1, 
                    M.C2.fubar2 {this} 1, 
                    M.S.fubar3 {this} 1,
                    zoo (M.C2.fubar2 {this}),
                    C1.fubar1 {this},
                    C1.fubar1 {this} 1 = (C1.fubar1 {this}) 1)
               } 
            """, "Main", "M2")

    fun testMoveOutOfRecord4() =
            testMoveRefactoring("""
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
                 } \where { 
                   \open M2 (foo2)
                 }
               }

               \module M2 \where {
                 \func foo2 {this : M.S} => (C1.fubar1 {this} 1, M.C2.fubar2 1, M.S.fubar3 1)
               }
            """, "Main", "M2")

    fun testMoveOutOfRecord5() =
            testMoveRefactoring("""
               \record R {
                 \func bar (n m : Nat) => {?}
                 
                 \func lol {A : \Type} (n m : A) => n
                 
                 \func fubar (n : Nat) {X : \Type} => n
                 
                 \func succ (n : Nat) => Nat.suc n

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
                 
                 \func foo11 {-caret-} => 1 `bar 2 `bar` 3
               }
            """, """
               \record R {
                 \func bar (n m : Nat) => {?}
                 
                 \func lol {A : \Type} (n m : A) => n
                 
                 \func fubar (n : Nat) {X : \Type} => n
                 
                 \func succ (n : Nat) => Nat.suc n
               }
               
               \func foo {this : R} : Nat -> Nat => R.`bar` {this} 1
               
               \func foo2 {this : R} : Nat -> Nat => (\lam x => R.bar {this} x 1)
                 
               \func foo3 {this : R} : Nat -> Nat => 1 R.`bar` {this}
               
               \func foo4 {this : R} : Nat -> Nat => R.bar {this} 1
               
               \func foo5 {this : R} : Nat => 1 R.`bar` {this} (R.succ {this}) 2 
                
               \func foo6 {this : R} : Nat => R.bar {this} 1 2
               
               \func foo7 {this : R} : Nat => 1 R.`lol` {this} {Nat} 2
                
               \func foo8 {this : R} : Nat => R.fubar {this} 1 {Nat}
               
               \func foo9 {this : R} : Nat => 1 R.`bar` {this} 2 R.`bar` {this} 3
               
               \func foo10 {this : R} : Nat => 1 R.`bar` {this} (R.bar {this} 2 3)
               
               \func foo11 {this : R} => (R.bar {this} 1 2) R.`bar` {this} 3
            """, "Main", "", "Main", "R.foo", "R.foo2", "R.foo3", "R.foo4", "R.foo5", "R.foo6", "R.foo7", "R.foo8", "R.foo9", "R.foo10", "R.foo11")

    fun testMoveOutOfRecord5b() =
            testMoveRefactoring("""
               \record R {
                 \func bar (n m : Nat) => {?}
                 
                 \func foo{-caret-} => `bar 1
               }
            """, """
               \record R {
                 \func bar (n m : Nat) => {?}
               }
               
               \func foo {this : R} => (lam n => R.bar {this} n 1)
            """, "Main", "", "Main", "R.foo")

    fun testMoveOutOfRecord6() =
            testMoveRefactoring("""
               \record R {
                 \data D{-caret-}
                   | C1
                   | C2 
  
                 \func foo (a : D) : D \with
                   | C1 => C1
                   | C2 => C2  
               }

               \module M
            """, """
               \record R {
                 \func foo (a : D {\this}) : D {\this} \with
                   | C1 => C1
                   | C2 => C2  
               } \where {
                 \open M (D, C1, C2)
               }

               \module M \where {
                 \data D {this : R}
                   | C1
                   | C2
               } 
            """, "Main", "M")

    fun testMoveOutOfRecord7() =
            testMoveRefactoring("""
                \record R {
                  \data D
                    
                  \func foo{-caret-} (a : \Sigma D) : D => a.1   
                }
                
                \module M
            """, """
                \record R {
                  \data D
                } \where {
                  \open M (foo)
                }

                \module M \where {
                  \func foo {this : R} (a : \Sigma (R.D {this})) : R.D {this} => a.1
                }
            """, "Main", "M")

    fun testMoveIntoDynamic1() =
            testMoveRefactoring("""
               \class C1 {
                 | carrier : \Type
               }
               
               \func foo{-caret-} => 1  
            """, """
               \class C1 {
                 | carrier : \Type
                 
                 \func foo => 1
               }
               
               \open C1 (foo)
            """, "Main", "C1", targetIsDynamic = true)

    fun testMoveIntoDynamic2() =
            testMoveRefactoring("""  
               --! Main.ard
               
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
               
               \open C1 (foo, goo)
            """, "Main", "C1", "Main", "foo", "goo", targetIsDynamic = true)

    fun testMoveIntoDynamic3() =
            testMoveRefactoring("""
               \class C1
                 | carrier : \Type
                 
               \func foo{-caret-} => 1  
            """, """
               \class C1 {
                 | carrier : \Type
                 
                 \func foo => 1
               }
               
               \open C1 (foo)
            """, "Main", "C1", targetIsDynamic = true)

    fun testMoveIntoDynamic4() =
            testMoveRefactoring("""
               \class C1
                 
               \func foo{-caret-} => 1  
            """, """
               \class C1 {
                 \func foo => 1
               }
               
               \open C1 (foo)
            """, "Main", "C1", targetIsDynamic = true)

    fun testMoveIntoDynamic5() =
            testMoveRefactoring("""
                \class C1
                  
                -- | fubar
                \func fubar{-caret-} => 101
            """, """
                \class C1 {
                  -- | fubar
                  
                  \func fubar => 101
                }
                
                \open C1 (fubar)
            """, "Main", "C1", targetIsDynamic = true)

    fun testMoveBetweenDynamics1() =
            testMoveRefactoring(""" 
               \record C {
                 | number : Nat
                 
                 \func foo{-caret-} => bar Nat.+ (bar {\this}) Nat.+ number
                 
                 \func bar => 202
                 
                 \func foobar => foo
               }
                
               \class D {
                 \func fubar => C.foo {\new C {| number => 1}}
               }
            """, """
               \record C {
                 | number : Nat               
               
                 \func bar => 202

                 \func foobar => foo {\this}
               } \where {
                 \open D (foo)
               }

               \class D {
                 \func fubar => foo {\new C {| number => 1}}

                 \func foo {this : C} => C.bar {this} Nat.+ (C.bar {this}) Nat.+ this.number 
               }
            """, "Main", "D", targetIsDynamic = true) //Note: There are instance inference errors in the resulting code

    fun testMoveBetweenDynamics2() =
            testMoveRefactoring(""" 
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

                 \func foobar => foo
               } \where {
                 \open D (foo)
               }

               \class D \extends C {
                 \func fubar => foo {\new C {| number => 1}}

                 \func foo => C.bar Nat.+ (C.bar {\this}) Nat.+ number 
               } 
            """, "Main", "D", targetIsDynamic = true) //Note: There are instance inference errors in the resulting code
}