package org.arend.intention

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbService
import org.arend.*
import org.arend.codeInsight.ArendImportOptimizer
import org.arend.quickfix.QuickFixTestBase
import org.arend.settings.ArendCustomCodeStyleSettings
import org.arend.settings.ArendCustomCodeStyleSettings.*
import org.arend.util.ArendBundle
import org.arend.util.arendModules

class OptimizeImportsTest : QuickFixTestBase() {

    private fun FileTree.prepareFileSystem(): TestProject {
        val testProject = create(myFixture.project, myFixture.findFileInTempDir("."))
        myFixture.configureFromTempProjectFile("Main.ard")
        return testProject
    }

    private inline fun doWithSettings(policy : OptimizeImportsPolicy, action : () -> Unit) {
        val settings = CodeStyle.createTestSettings()
        val arendSettings = settings.getCustomSettings(ArendCustomCodeStyleSettings::class.java)
        arendSettings.OPTIMIZE_IMPORTS_POLICY = policy
        arendSettings.EXPLICIT_IMPORTS_LIMIT = 5000
        CodeStyle.setTemporarySettings(myFixture.project, settings)
        try {
            action()
        } finally {
            CodeStyle.dropTemporarySettings(myFixture.project)
        }
    }

    private fun doTest(before: String, after: String) {
        val fileTree = fileTreeFromText(before)
        fileTree.prepareFileSystem()
        typecheck(fileTree.fileNames) //

        val optimizer = ArendImportOptimizer()
        WriteCommandAction.runWriteCommandAction(myFixture.project, optimizer.processFile(myFixture.file))
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    private fun doExplicitTest(
        before: String,
        after: String) = doWithSettings(OptimizeImportsPolicy.ONLY_EXPLICIT) { doTest(before, after) }

    private fun doImplicitTest(
        before: String,
        after: String) = doWithSettings(OptimizeImportsPolicy.ONLY_IMPLICIT) { doTest(before, after) }

    private fun doSoftTest(
        before: String,
        after: String) = doWithSettings(OptimizeImportsPolicy.SOFT) { doTest(before, after) }

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
        """
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

    fun `test implicit import`() {
        doImplicitTest("""
            -- ! Foo.ard
            \func f => 1
            -- ! Main.ard
            \import Foo
            
            \func g => f
        """, """
            \import Foo
            
            \func g => f
        """)
    }

    fun `test implicit import with module`() {
        doImplicitTest("""
            -- ! Main.ard
            \module M \where {
              \func f : Nat => 4
              \func g => 5
            }
            
            \module K \where {
              \func f : Nat => 3
            }
            
            \module N \where {
              \open M \hiding (f)
              \open K
            
              \func h => f
              \func h' => g
            }
        """, """
            \open K
            \open M \hiding (f)
            
            \module M \where {
              \func f : Nat => 4
              \func g => 5
            }
            
            \module K \where {
              \func f : Nat => 3
            }
            
            \module N \where {
              \func h => f
              \func h' => g
            }
        """)
    }

    fun `test implicit import combined with open`() {
        doImplicitTest("""
            -- ! Foo.ard
            \func f => 2
            \func g2 => 3
            -- ! Main.ard
            \import Foo \hiding (f)
            \open K
            
            \module K \where {
              \func f => 4
            }
            
            \func h => g2
            \func h' => f
        """, """
            \import Foo \hiding (f)
            \open K
            
            \module K \where {
              \func f => 4
            }
            
            \func h => g2
            \func h' => f
        """)
    }

    fun `test nested implicit import`() {
        doImplicitTest("""
            -- ! Foo.ard
            \func f => 2
            -- ! Main.ard
            \import Foo
            \open K
            
            \module K \where {
              \func f => 4
              \func g => 5
            }
            
            \module G \where {
              \open K (g)
              \func h => f
              \func j => g
            }
        """, """
            \import Foo
            \open K \hiding (f)
            
            \module K \where {
              \func f => 4
              \func g => 5
            }
            
            \module G \where {
              \func h => f
              \func j => g
            }
        """)
    }

    fun `test implicit imports with implicit collision`() {
        doImplicitTest("""
            -- ! Foo.ard
            \func f => 2
            \func g => 3
            -- ! Bar.ard
            \func f => 4
            \func h => 5
            -- ! Main.ard
            \import Foo
            \import Bar \hiding (f)
            \func g' => g
            \func h' => h
        """, """
            \import Bar
            \import Foo \hiding (f)
            
            \func g' => g
            \func h' => h
        """)
    }

    fun `test implicit imports with class`() {
        doImplicitTest("""
            -- ! Foo.ard
            \class F {
              | ff : Nat
            } \where {
              \func ff' : Nat => 1
            }
            -- ! Main.ard
            \import Foo
            \open F
            
            \func g {f : F} => ff
            \func h => ff'
        """, """
            \import Foo
            \open F
            
            \func g {f : F} => ff
            \func h => ff'
        """)
    }

    fun `test soft imports with class`() {
        doSoftTest("""
            -- ! Foo.ard
            \class F {
              | ff : Nat
            } \where {
              \func ff' : Nat => 1
            }
            -- ! Main.ard
            \import Foo (F, ff)
            \open F
            
            \func g {f : F} => ff
            \func h => ff'
        """, """
            \import Foo (F, ff)
            \open F
            
            \func g {f : F} => ff
            \func h => ff'
        """)
    }

    fun `test soft imports redundant open`() {
        doSoftTest("""
            -- ! Main.ard
            \module A \where {
              \module X \where {}
            
              \open X
            }
        """, """
           \module A \where {
             \module X \where {}
           }
        """)
    }

    fun `test soft imports`() {
        doSoftTest("""
            -- ! Main.ard
            \module A \where {
              \module B \where {
                \func f => 1
                \where {
                  \func g => {?}
                }
                \open f
            
                \func h => g
              }
            }
        """, """
            \module A \where {
              \module B \where {
                \func f => 1
                \where {
                  \func g => {?}
                }
                \open f
            
                \func h => g
              }
            }
        """)
    }

    fun `test soft imports 2`() {
        doSoftTest("""
            -- ! Main.ard
            \module A \where {
              \module B \where {
                \func f => 1
                \where {
                  \func g => {?}
                }
                \open B.f
            
                \func h => g
              }
            }
        """, """
            \module A \where {
              \module B \where {
                \func f => 1
                \where {
                  \func g => {?}
                }
                \open B.f
            
                \func h => g
              }
            }
        """)
    }

    fun `test soft imports 3`() {
        doSoftTest("""
            -- ! Foo.ard
            \func f => 1
            \func f' => 2
            -- ! Bar.ard
            \func h' => 3
            \func h => 3
            \func h'' => 3
            -- ! Baz.ard
            \func i => 4
            \func i' => 4
            \func i'' => 4
            -- ! Qux.ard
            \func j => 4
            \func j' => 4
            -- ! Main.ard
            \import Foo (f, f')
            \import Bar (h', h, h'')
            \import Baz (i', i, i'')
            \import Qux (j, j')
            \func r => f
            \func r' => h
            \func r'' => i'
            \func r''' => i''
            \func r'''' => j'
        """, """
            \import Bar (h)
            \import Baz (i', i'')
            \import Foo (f)
            \import Qux (j')

            \func r => f
            \func r' => h
            \func r'' => i'
            \func r''' => i''
            \func r'''' => j'
        """)
    }

    fun `test soft imports 4`() {
        doSoftTest("""
            -- ! Aaa.ard
            \func f => 1
            -- ! Bbb.ard
            \func g => 1
            -- ! Ccc.ard
            \func h => 1
            -- ! Main.ard
            \import Ccc
            
            \import Bbb (g)
             
            \import Aaa 
             
             \func p => f + g + h
        """, """
            \import Aaa
            \import Bbb (g)
            \import Ccc
            
            \func p => f + g + h
        """)
    }

    fun `test soft imports 5`() {
        doSoftTest("""
            -- ! Main.ard
            \class R | n : Nat
            
            \func ff {_ : R} => 10
            
            \instance G' : R | n => 2
            
            \module M \where {
              \open Main (G')
            
              \func f : Nat => ff
            }
        """, """
            \class R | n : Nat
            
            \func ff {_ : R} => 10
            
            \instance G' : R | n => 2
            
            \module M \where {
              \open Main (G')
            
              \func f : Nat => ff
            }
        """)
    }

    fun `test soft imports 6`() {
        doSoftTest("""
            -- ! Main.ard
            \module M \where \func f => 1
            
            \func g => 1
            \where { \open M }
        """, """
            \module M \where \func f => 1
            
            \func g => 1
            
        """)
    }

    fun `test soft imports 7`() {
        doSoftTest("""
            -- ! Foo.ard
            \class D { k : Nat }
            -- ! Bar.ard
            \import Foo
           
            \instance x : D \cowith
              | k => 2
            -- ! Main.ard
            \import Foo
            \import Bar
            
            \func e {d : D} => d.k

            \func hh => 1 \where {
              \func h : Nat => e
            }
        """, """
            \import Bar
            \import Foo
            
            \func e {d : D} => d.k

            \func hh => 1 \where {
              \func h : Nat => e
            }
        """)
    }

    fun `test class in module`() {
        doSoftTest("""
            -- ! Main.ard
            \module M \where {
              \record R
                | field : Nat
            }
            
            \func asdzxc => field
              \where
                \open M.R(field)
        """, """
            \module M \where {
              \record R
                | field : Nat
            }
            
            \func asdzxc => field
              \where
                \open M.R(field)
        """)
    }

    fun `test local shadowing`() {
        doSoftTest("""
            -- ! Main.ard
            \open B(foo)

            \module A \where {
              \func foo => 1
            }
            
            \module B \where {
              \func foo => 2
            }
            
            \func r : foo = 2 => idp
            
            \func q : foo = 1 => idp \where \open A
        """, """
            \open B(foo)

            \module A \where {
              \func foo => 1
            }
            
            \module B \where {
              \func foo => 2
            }
            
            \func r : foo = 2 => idp
            
            \func q : foo = 1 => idp \where \open A
        """)
    }

    fun `test class field reimport`() {
        doSoftTest("""
            -- ! Main.ard
            \data Unit | unit
            
            \class Op {
              | f : Unit
            }
            
            \lemma foo {o : Op} : Unit => f'
            \where {
              \open Op(f \as f')
            }
        """, """
            \data Unit | unit
            
            \class Op {
              | f : Unit
            }
            
            \lemma foo {o : Op} : Unit => f'
            \where {
              \open Op(f \as f')
            }
        """)
    }

    fun testOptimizeImports() = checkNoQuickFixes(
        ArendBundle.message("arend.optimize.imports.intention.name"), """
       \module M \where {
         \func foo \alias fu (a : Nat) => a
       }

       \module M1 \where {
         \open {-caret-}M (fu \as foobar)

         \func lol => 1 Nat.+ foobar 2
       }
    """)

    fun testOptimizeImports2() = checkNoQuickFixes(
        ArendBundle.message("arend.optimize.imports.intention.name"), """
       \module N \where \data Bool | true | false

       \module N2 \where {
         \open N (Bool, true{-caret-}, false)

          \func mcas (b : Bool) : Nat => \case b \with {
            | true => 1
            | false => 0
          }
       } 
    """)

    fun testOptimizeImports3() = checkNoQuickFixes(ArendBundle.message("arend.optimize.imports.intention.name"), """
       \module M \where {
         \func foo \alias fu (a : Nat) => a \where
           \func lol => 101
       }

       \module M1 \where {
         \open {-caret-}M (fu \as foobar)

         \func lol => 1 Nat.+ foobar.lol
       }
    """)

    fun testOptimizeImports4() = doSoftTest("""
       \module A \where {
         \func foo => 101

         \func bar => 42
       }

       \func foo => 102

       \module B \where {
         \open A (foo, bar{-caret-})

         \func fubar => foo + bar
       }
    """, """
       \module A \where {
         \func foo => 101

         \func bar => 42
       }

       \func foo => 102

       \module B \where {
         \open A (foo, bar)

         \func fubar => foo + bar
       }
    """)

    fun testOptimizeImports5() = doSoftTest("""
       \open Outer{-caret-} (Inner)

       \module Bar \where {
         \open Inner

         \func bar => foobar
       }

       \module Outer \where {
         \module Inner \where {
           \func foobar => 100
         }
       }
    """, """
       \open Outer (Inner)

       \module Bar \where {
         \open Inner

         \func bar => foobar
       }

       \module Outer \where {
         \module Inner \where {
           \func foobar => 100
         }
       }
    """)

    fun testOptimizeImports6() = checkNoQuickFixesWithMultifile(ArendBundle.message("arend.optimize.imports.intention.name"), """
        -- ! A.ard
        \module M \where {
          \instance I => {?}
        }
        
        -- ! Main.ard
        \import A(M{-caret-})
        \open M
        
        \instance f => M.I
    """)
}