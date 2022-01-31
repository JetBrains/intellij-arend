package org.arend.intention

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import org.arend.*
import org.arend.codeInsight.ArendImportOptimizer
import org.arend.settings.ArendCustomCodeStyleSettings
import org.intellij.lang.annotations.Language

class OptimizeImportsTest : ArendTestBase() {

    private fun FileTree.prepareFileSystem(): TestProject {
        val testProject = create(myFixture.project, myFixture.findFileInTempDir("."))
        myFixture.configureFromTempProjectFile("Main.ard")
        return testProject
    }

    private inline fun doWithSettings(useImplicitImports : Boolean, explicitLimit : Int, action : () -> Unit) {
        val settings = CodeStyle.createTestSettings()
        val arendSettings = settings.getCustomSettings(ArendCustomCodeStyleSettings::class.java)
        arendSettings.USE_IMPLICIT_IMPORTS = useImplicitImports
        arendSettings.EXPLICIT_IMPORTS_LIMIT = explicitLimit
        CodeStyle.setTemporarySettings(myFixture.project, settings)
        try {
            action()
        } finally {
            CodeStyle.dropTemporarySettings(myFixture.project)
        }
    }

    private fun doTest(
        @Language("Arend") before: String,
        @Language("Arend") after: String,
        typecheck: Boolean = false
    ) {
        val fileTree = fileTreeFromText(before)
        fileTree.prepareFileSystem()
        if (typecheck) {
            typecheck(fileTree.fileNames)
        }
        val optimizer = ArendImportOptimizer()
        WriteCommandAction.runWriteCommandAction(myFixture.project, optimizer.processFile(myFixture.file))
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    private fun doExplicitTest(
        @Language("Arend") before: String,
        @Language("Arend") after: String,
        typecheck: Boolean = false
    ) = doWithSettings(false, 5000) { doTest(before, after, typecheck) }

    private fun doImplicitTest(
        @Language("Arend") before: String,
        @Language("Arend") after: String,
        typecheck: Boolean = false) = doWithSettings(true, 0) { doTest(before, after, typecheck) }

    fun `test prelude`() {
        doExplicitTest("""
            -- ! Main.ard
            \func foo : Nat => 1
            """, """
            \func foo : Nat => 1
            """)
    }

    fun `test constructor`() {
        doExplicitTest("""
            -- ! Main.ard
            \import Foo
            
            \func foo : Bar => bar
            -- ! Foo.ard
            \data Bar | bar
            """, """
            \import Foo (Bar, bar)
            
            \func foo : Bar => bar
            """
        )
    }

    fun `test partially qualified name`() {
        doExplicitTest("""
            -- ! Bar.ard
            \module A \where {
              \func bar : Nat => {?}            
            }
            
            -- ! Main.ard
            \import Bar
            
            \func foo : Nat => A.bar
            """, """
            \import Bar (A)
            
            \func foo : Nat => A.bar
            """
        )
    }

    fun `test import func`() {
        doExplicitTest("""
            -- ! Bar.ard
            \func f : Nat => 1
            
            -- ! Main.ard
            \import Bar
            
            \func foo : Nat => f
            """, """
            \import Bar (f)
            
            \func foo : Nat => f
            """
        )
    }

    fun `test alphabetic order`() {
        doExplicitTest("""
            -- ! ZZZ.ard
            \func z : Nat => 1
            
            -- ! AAA.ard
            \func a : Nat => 1
            
            -- ! Main.ard
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
        doExplicitTest("""
            -- ! Main.ard
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
        doExplicitTest("""
            -- ! Main.ard
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
        doExplicitTest("""
            \open Nat

            \func xx : 1 + 1 = 1 + 1 => idp
            """, """
            \open Nat (+)

            \func xx : 1 + 1 = 1 + 1 => idp
            """,
        )
    }

    private val collidedDefinitions = """
        -- ! Foo.ard
        \func foo => () \where \func apply => ()

        \func bar => () \where \func apply => ()
        
    """

    fun `test collision 1`() {
        doExplicitTest("""
            $collidedDefinitions
            -- ! Main.ard
            \import Foo
            
            \func f : foo.apply = bar.apply => {?}
            """, """
            \import Foo (bar, foo)
            
            \func f : foo.apply = bar.apply => {?}
            """,
        )
    }

    fun `test collision 2`() {
        doExplicitTest("""
            $collidedDefinitions
            -- ! Main.ard
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
        doExplicitTest("""
            $collidedDefinitions
            -- ! Main.ard
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
        doExplicitTest("""
            -- ! Foo.ard
            \data Bar
            
            -- ! Main.ard
            \import Foo
            \func f => 1 \where \func g : Bar => {?}
            """, """
            \import Foo (Bar)

            \func f => 1 \where \func g : Bar => {?}
            """,
        )
    }

    fun `test local module`() {
        doExplicitTest("""
            -- ! Main.ard
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
        doExplicitTest(
            """
            -- ! Main.ard
            \data D (a : Nat)
              | d (D a)
        """, """
            \data D (a : Nat)
              | d (D a)
        """
        )
    }

    fun `test self-contained function`() {
        doExplicitTest(
            """
            -- ! Main.ard
            \func foo => bar
              \where \func bar => 1
        """, """
            \func foo => bar
              \where \func bar => 1
        """
        )
    }

    fun `test constructor in pattern`() {
        doExplicitTest(
            """
            -- ! Foo.ard
            \data D | d Nat
            -- ! Main.ard
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
        doExplicitTest(
            """
            -- ! Foo.ard
            \record R {
              | rr : Nat
            }
            -- ! Main.ard
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

    fun `test definition in where`() {
        doExplicitTest(
            """
            -- ! Main.ard
            \func f => gg \where
              \data g | gg
        """, """
            \func f => gg \where
              \data g | gg
        """
        )
    }

    fun `test no big space`() {
        doExplicitTest(
            """
            -- ! Main.ard
            \open M

            -- a comment
            
            \module M \where {
              \func f => 1
            }
            
            \func g => f
        """, """
            \open M (f)

            -- a comment
            
            \module M \where {
              \func f => 1
            }
            
            \func g => f
        """
        )
    }

    fun `test array`() {
        doExplicitTest(
            """
            -- ! Main.ard
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
        doExplicitTest(
            """
            -- ! Main.ard
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
        doExplicitTest(
            """
            -- ! Foo.ard
            \class R (rr : Nat)
            -- ! Main.ard
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
        doExplicitTest(
            """
            -- ! Foo.ard
            \class A {
              | a : Nat
            }
            -- ! Main.ard
            \import Foo

            \class C {
              \data D \where {
                \func g {x : A} : Fin a => {?}
              }
            }
        """, """
            \import Foo (A, a)

            \class C {
              \data D \where {
                \func g {x : A} : Fin a => {?}
              }
            }
        """
        )
    }

    fun `test record field`() {
        doExplicitTest(
            """
            -- ! Main.ard
            \record R {
              | rr : Nat
            }
            
            \func f {r : R} => rr
        """, """
            \record R {
              | rr : Nat
            }
            
            \func f {r : R} => rr
        """
        )
    }

    fun `test record parameter`() {
        doExplicitTest(
            """
            -- ! Main.ard
            \open R (rr)
            
            \record R (rr : Nat)

            \func f {r : R} => rr
        """, """
            \open R (rr)
            
            \record R (rr : Nat)
            
            \func f {r : R} => rr
        """
        )
    }

    fun `test two exporting classes`() {
        doExplicitTest(
            """
            -- ! Main.ard
            \class A {
              | n : Nat
              \func f : Nat => n
            }
            
            \class B {
              | n : Nat
              \func f : Nat => n
            }
            
            \func h {a : A} => a.f
            \func g {b : B} => b.f
        """, """
            \class A {
              | n : Nat
              \func f : Nat => n
            }
            
            \class B {
              | n : Nat
              \func f : Nat => n
            }
            
            \func h {a : A} => a.f
            \func g {b : B} => b.f
        """
        )
    }

    fun `test implicit instance import`() {
        doExplicitTest(
            """
            -- ! Foo.ard
            \class A (T : \Type) {
              | t : T
            }
            
            \instance nat : A Nat 1
            -- ! Main.ard
            \import Foo

            \func p : Nat => t
        """, """
            \import Foo (nat, t)

            \func p : Nat => t
        """
        )
    }

    fun `test implicit instance import 2`() {
        doExplicitTest(
            """
            -- ! Foo.ard
            \class A (T : \Type) {
              | t : T
            }
            
            \instance nat : A Nat 1
            
            \data D | d
            \instance dd : A D d
            -- ! Main.ard
            \import Foo

            \func p : Nat => t
        """, """
            \import Foo (nat, t)

            \func p : Nat => t
        """, true
        )
    }

    fun `test file does not appear in import`() {
        doExplicitTest(
            """
            -- ! Foo.ard
            \func foo => 1
            -- ! Main.ard
            \import Foo

            \func p => Foo.foo
        """, """
            \import Foo (foo)

            \func p => Foo.foo
        """
        )
    }

    fun `test extension`() {
        doExplicitTest(
            """
            -- ! Main.ard
            \class R (rr : Nat)

            \class E \extends R
              | ee : Fin rr
        """, """
            \class R (rr : Nat)

            \class E \extends R
              | ee : Fin rr
        """
        )
    }

    fun `test extension3`() {
        doExplicitTest(
            """
            -- ! Main.ard
            \open A (B)

            \class A \where \record B
            
            \class E \extends A {
              | f : B
            }
        """, """
            \open A (B)

            \class A \where \record B
            
            \class E \extends A {
              | f : B
            }
        """
        )
    }


    fun `test shadowed import`() {
        doExplicitTest(
            """
            -- ! Main.ard
            \class A (E : \Type) {
                | + : E -> E -> E
            }
        
            \instance a : A Nat
              | + => +
            \where {
              \open Nat (+)
            } 
        """, """
            \class A (E : \Type) {
                | + : E -> E -> E
            }
        
            \instance a : A Nat
              | + => +
              \where {
                \open Nat (+)
              } 
        """
        )
    }

    fun `test remove where`() {
        doExplicitTest(
            """
            -- ! Main.ard
            \module M \where {}

            \module N
              \open M
        """, """
            \module M
            
            \module N
              
        """
        )
    }

    fun `test remove where 2`() {
        doExplicitTest(
            """
            -- ! Main.ard
            \module M \where {}

            \module N \where {
              \open M            
            }
        """, """
            \module M
            
            \module N 
        """
        )
    }

    fun `test import from file and group`() {
        doExplicitTest(
            """
            -- ! Foo.ard
            \func f => 1
            -- ! Main.ard
            \import Foo
            
            \func g => f
            \module M \where { \func f => 2 }

            \module N \where {
              \open M 
              \func h => f
            }
        """, """
            \import Foo (f)
            
            \func g => f
            \module M \where { \func f => 2 }
            
            \module N \where {
              \open M (f)
            
              \func h => f
            }
        """
        )
    }

    fun `test deep open`() {
        doExplicitTest(
            """
            -- ! Main.ard
            \func f => a \where {
              \class E {
                \func a => 1
              }
              \open E (a)
            }
        """, """
            \open f.E (a)
            
            \func f => a \where {
              \class E {
                \func a => 1
              }
            }
        """
        )
    }

    fun `test can import identifier without opening a class`() {
        doExplicitTest(
            """
            -- ! Foo.ard
            \class A {
              | f : Nat
            }
            -- ! Main.ard
            \import Foo (A, f)

            \func g {a : A} => f
        """, """
            \import Foo (A, f)

            \func g {a : A} => f
        """
        )
    }

    fun `test renamed import`() {
        doExplicitTest(
            """
                -- ! Foo.ard
                \func f => 1
                -- ! Main.ard
                \import Foo (f \as g)
                
                \func h => g
            """, """
                \import Foo (f \as g)
                
                \func h => g
            """
        )
    }

}