package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import org.arend.*
import org.arend.codeInsight.ArendImportOptimizer
import org.intellij.lang.annotations.Language

class OptimizeImportsTest : ArendTestBase() {

    private fun FileTree.prepareFileSystem(): TestProject {
        val testProject = create(myFixture.project, myFixture.findFileInTempDir("."))
        myFixture.configureFromTempProjectFile("Main.ard")
        return testProject
    }

    private fun doTest(
        @Language("Arend") before: String,
        @Language("Arend") after: String
    ) {
        fileTreeFromText(before).prepareFileSystem()
        val optimizer = ArendImportOptimizer()
        WriteCommandAction.runWriteCommandAction(myFixture.project, optimizer.processFile(myFixture.file))
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    fun `test prelude`() {
        doTest("""
            --! Main.ard
            \func foo : Nat => 1
            """, """
            \func foo : Nat => 1
            """)
    }

    fun `test constructor`() {
        doTest("""
            --! Main.ard
            \import Foo
            
            \func foo : Bar => bar
            --! Foo.ard
            \data Bar | bar
            """, """
            \import Foo (Bar, bar)
            
            \func foo : Bar => bar
            """
        )
    }

    fun `test partially qualified name`() {
        doTest("""
            --! Bar.ard
            \module A \where {
              \func bar : Nat => {?}            
            }
            
            --! Main.ard
            \import Bar
            
            \func foo : Nat => A.bar
            """, """
            \import Bar (A)
            
            \func foo : Nat => A.bar
            """
        )
    }

    fun `test import func`() {
        doTest("""
            --! Bar.ard
            \func f : Nat => 1
            
            --! Main.ard
            \import Bar
            
            \func foo : Nat => f
            """, """
            \import Bar (f)
            
            \func foo : Nat => f
            """
        )
    }

    fun `test alphabetic order`() {
        doTest("""
            --! ZZZ.ard
            \func z : Nat => 1
            
            --! AAA.ard
            \func a : Nat => 1
            
            --! Main.ard
            \import ZZZ
            \import AAA
            
            \func foo : z = a => idp
            """, """
            \import AAA (a)
            \import ZZZ (z)
            
            \func foo : z = a => idp
            """,
        )
    }

    fun `test same-package modularized usage`() {
        doTest("""
            --! Main.ard
            \data Bar \where {
              \data R \where {
                \func f : Nat => 1
              }
            }
            
            \func g => Bar.R.f
            """, """
            \data Bar \where {
              \data R \where {
                \func f : Nat => 1
              }
            }
            
            \func g => Bar.R.f
            """,
        )
    }

    fun `test same-package in-module usage`() {
        doTest("""
            --! Main.ard
            \data Bar \where {
              \func foo : Nat => 1
              
              \func bar : Nat => foo
            }
            """, """
            \data Bar \where {
              \func foo : Nat => 1
              
              \func bar : Nat => foo
            }
            """,
        )
    }

    fun `test in-prelude import`() {
        doTest("""
            \open Nat

            \func xx : 1 + 1 = 1 + 1 => idp
            """, """
            \open Nat (+)

            \func xx : 1 + 1 = 1 + 1 => idp
            """,
        )
    }

    private val collidedDefinitions = """
        --! Foo.ard
        \func foo => () \where \func apply => ()

        \func bar => () \where \func apply => ()
        
    """

    fun `test collision 1`() {
        doTest("""
            $collidedDefinitions
            --! Main.ard
            \import Foo
            
            \func f : foo.apply = bar.apply => {?}
            """, """
            \import Foo (bar, foo)
            
            \func f : foo.apply = bar.apply => {?}
            """,
        )
    }

    fun `test collision 2`() {
        doTest("""
            $collidedDefinitions
            --! Main.ard
            \import Foo
            \open foo
            
            \func f : apply = bar.apply => {?}
            """, """
            \import Foo (bar, foo)
            \open foo (apply)
            
            \func f : apply = bar.apply => {?}
            """,
        )
    }

    fun `test collision 3`() {
        doTest("""
            $collidedDefinitions
            --! Main.ard
            \import Foo

            \module A \where {
              \open foo
            
              \func f => apply
            }
            
            \module B \where {
              \open bar
            
              \func f => apply
            }
            """, """
            \import Foo (bar, foo)
            
            \module A \where {
              \open foo (apply)
            
              \func f => apply
            }
            
            \module B \where {
              \open bar (apply)
            
              \func f => apply
            }
            """,
        )
    }

    fun `test single import`() {
        doTest("""
            --! Foo.ard
            \data Bar
            
            --! Main.ard
            \import Foo
            \func f => 1 \where \func g : Bar => {?}
            """, """
            \import Foo (Bar)

            \func f => 1 \where \func g : Bar => {?}
            """,
        )
    }

    fun `test local module`() {
        doTest("""
            --! Main.ard
            \module M \where { \func x => 1 }
            \open M
            
            \func f => x
            """, """
            \open M (x)

            \module M \where { \func x => 1 }
            
            \func f => x
            """,
        )
    }

    fun `test self-contained datatype`() {
        doTest(
            """
            --! Main.ard
            \data D (a : Nat)
              | d (D a)
        """, """
            \data D (a : Nat)
              | d (D a)
        """
        )
    }

    fun `test self-contained function`() {
        doTest(
            """
            --! Main.ard
            \func foo => bar
              \where \func bar => 1
        """, """
            \func foo => bar
              \where \func bar => 1
        """
        )
    }

    fun `test constructor in pattern`() {
        doTest(
            """
            --! Foo.ard
            \data D | d Nat
            --! Main.ard
            \import Foo
            
            \func foo (dd : D) : Nat \elim dd
              | d n => 1
        """, """
            \import Foo (D, d)
            
            \func foo (dd : D) : Nat \elim dd
              | d n => 1
        """
        )
    }

    fun `test record`() {
        doTest(
            """
            --! Foo.ard
            \record R {
              | rr : Nat
            }
            --! Main.ard
            \import Foo
            
            \func f : R \cowith {
              | rr => 1
            }
        """, """
            \import Foo (R)

            \func f : R \cowith {
              | rr => 1
            }
        """
        )
    }

    fun `test array`() {
        doTest(
            """
            --! Main.ard
            \func f => \new Array { | A => \lam _ => Nat
                                    | len => 1
                                    | at (0) => 1  }
        """, """
            \func f => \new Array { | A => \lam _ => Nat
                                    | len => 1
                                    | at (0) => 1  }
        """
        )
    }

    fun `test dynamic definition`() {
        doTest(
            """
            --! Main.ard
            \record R {
              | r : Nat
            
              \func rrr : Fin r => {?}
            }
        """, """
            \record R {
              | r : Nat
            
              \func rrr : Fin r => {?}
            }
        """
        )
    }

    fun `test instance`() {
        doTest(
            """
            --! Foo.ard
            \class R (rr : Nat)
            --! Main.ard
            \import Foo (R)
            \open R
            
            \func f {r : R} : Fin rr => {?}
        """, """
            \import Foo (R)
            \open R (rr)
            
            \func f {r : R} : Fin rr => {?}
        """
        )
    }

    fun `test dynamic subgroup`() {
        doTest(
            """
            --! Foo.ard
            \class A {
              | a : Nat
            }
            --! Main.ard
            \import Foo

            \class C {
              \data D \where {
                \func g {x : A} : Fin a => {?}
              }
            }
        """, """
            \import Foo (A)

            \class C {
              \data D \where {
                \open A (a)
            
                \func g {x : A} : Fin a => {?}
              }
            }
        """
        )
    }
}