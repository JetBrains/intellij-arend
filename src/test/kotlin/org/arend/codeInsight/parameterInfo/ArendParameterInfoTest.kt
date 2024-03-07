package org.arend.codeInsight.parameterInfo

import com.intellij.codeInsight.hint.ShowParameterInfoHandler
import com.intellij.psi.PsiElement
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import org.arend.ArendLanguage
import org.arend.ArendTestBase
import org.junit.Assert

class ArendParameterInfoTest : ArendTestBase() {

    private fun checkParameterInfo(code: String, expectedHint: String, typecheck: Boolean = false) {
        InlineFile(code).withCaret()

        if (typecheck) {
            typecheck()
        }

        val handler = ShowParameterInfoHandler.getHandlers(project, ArendLanguage.INSTANCE).firstOrNull() ?:
                            error("Could not find parameter info handler")
        val mockCreateParameterInfoContext = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val parameterOwner = handler.findElementForParameterInfo(mockCreateParameterInfoContext) as? PsiElement

        if (parameterOwner == null) {
            Assert.assertEquals(expectedHint, "")
            return
        }

        val updateContext = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)
        updateContext.parameterOwner = parameterOwner
        val elementForUpdating = handler.findElementForUpdatingParameterInfo(updateContext)
        if (elementForUpdating != null) {
            handler.updateParameterInfo(elementForUpdating, updateContext)
        }

        val parameterInfoUIContext = MockParameterInfoUIContext(parameterOwner)
        parameterInfoUIContext.currentParameterIndex = updateContext.currentParameter

        mockCreateParameterInfoContext.itemsToShow?.forEach {
            handler.updateUI(it, parameterInfoUIContext)
        }

        Assert.assertEquals(expectedHint, parameterInfoUIContext.resultText)
    }

    fun `test inner app parameter`() {
        val code = "\\func f (x y : Nat) => 0\n" +
                "\\func g (y : Nat -> Nat) => 0\n" +
                "\\func h => f (g {-caret-})"
        val expectedHint = "<highlight>y : Nat -> Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test inner app function`() {
        val code = "\\func f (x y : Nat) => 0\n" +
                "\\func g (y : Nat -> Nat) => 0\n" +
                "\\func h => f (g{-caret-})"
        val expectedHint = "y : Nat -> Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test outer app parameter`() {
        val code = "\\func f (x y : Nat) => 0\n" +
                "\\func g (y : Nat -> Nat) => 0\n" +
                "\\func h => f g {-caret-}"
        val expectedHint = "x : Nat, <highlight>y : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test new arg position`() {
        val code = "\\func f (x y : Nat) => 0\n" +
                "\\func g => f x {-caret-}"
        val expectedHint = "x : Nat, <highlight>y : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test new arg position implicit`() {
        val code = "\\func f {x y : Nat} => 0\n" +
                "\\func g => f {x} {-caret-}"
        val expectedHint = "{x : Nat}, <highlight>{y : Nat}</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test new arg position implicit variation 1`() {
        val code = "\\func foo {A : \\Type} {f : A -> A} {a : A} => a\n" +
                "\\func bar => foo {Nat} {\\lam x => x} {-caret-}"
        val expectedHint = "{A : \\Type}, {f : A -> A}, <highlight>{a : A}</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    /*fun `test no hint in lam`() {
        val code = "\\func f (x y : Nat) => 0\n" +
                "\\func h => f (\\lam x => {-caret-}x)"
        val expectedHint = ""
        checkParameterInfo(code, expectedHint)
    } */

    fun `test hint in lam`() {
        val code = "\\func f (x y : Nat) => 0\n" +
                "\\func h => f (\\lam x =>{-caret-} x)"
        val expectedHint = "<highlight>x : Nat</highlight>, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test binop seq priority right`() {
        val code = "\\func f => 1 Nat.+ 2{-caret-} Nat.* 3"
        val expectedHint = "<highlight>x : Nat</highlight>, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test binop seq priority left`() {
        val code = "\\func f => 1 Nat.* 2{-caret-} Nat.+ 3"
        val expectedHint = "x : Nat, <highlight>y : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test binop seq no highlighting right`() {
        val code = "\\func f => 1 Nat.+ 2 Nat.*{-caret-} 3"
        val expectedHint = "x : Nat, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test binop seq no highlighting left`() {
        val code = "\\func f => 1 Nat.+ 2 {-caret-}Nat.* 3"
        val expectedHint = "x : Nat, y : Nat"
        checkParameterInfo(code, expectedHint)
    }


    fun `test long names`() {
        val code = "\\class C { x } \n" +
                "\\func f (x y : Nat) => 0\n" +
                "\\func h (c : C) => f (c.x{-caret-})"
        val expectedHint = "<highlight>x : Nat</highlight>, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test implicit params outer`() {
        val code = "\\func f (x y : Nat) {z : Nat} (w : Nat) => 0\n" +
                "\\func h (x : Nat) => f 0 0 {x{-caret-}}"
        val expectedHint = "x : Nat, y : Nat, <highlight>{z : Nat}</highlight>, w : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test implicit params skip implicit`() {
        val code = "\\func f (x y : Nat) {z : Nat} (w : Nat) => 0\n" +
                "\\func h (x : Nat) => f 0 0 x{-caret-}"
        val expectedHint = "x : Nat, y : Nat, {z : Nat}, <highlight>w : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test implicit params inner`() {
        val code = "\\func f (x y : Nat) {z : Nat} (w : Nat) => 0\n" +
                "\\func h (x : Nat) => f 0 0 {f x{-caret-}}"
        val expectedHint = "<highlight>x : Nat</highlight>, y : Nat, {z : Nat}, w : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test implicit params cons`() {
        val code = "\\data D (A : \\Type) (a : A) | con Nat\n" +
                "\\func foo => con {Nat} {-caret-}"
        val expectedHint = "{A : \\Type}, <highlight>{a : A}</highlight>, _ : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test var as cons arg`() {
        val code = "\\data D | cons (n : Nat) | \\infix 5 :: (_ _ : Nat)\n" +
                "\\func foo (d : D) (f : (Nat -> D) -> Nat) : Nat \\elim d\n" +
                "  | :: x y => f (x{-caret-} ::)"
        val expectedHint = "<highlight>_ : Nat</highlight>, _ : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test class fields`() {
        val code = "\\class C { \\field f (x y : Nat) : Nat } \n" +
                "\\func h (c : C) (x : Nat) => c.f {-caret-}x"
        val expectedHint = "{this : C}, <highlight>x : Nat</highlight>, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test param after this`() {
        val code = "\\class C { \\field f (x y : Nat) : Nat } \n" +
                "\\func h (c : C) (x : Nat) => f {c} {-caret-}x"
        val expectedHint = "{this : C}, <highlight>x : Nat</highlight>, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test func param after this`() {
        val code = "\\class C { \\func f (x y : Nat) : Nat => 0 } \n" +
                "\\func h (c : C) (x : Nat) => C.f {c} {-caret-}x"
        val expectedHint = "{this : C}, <highlight>x : Nat</highlight>, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test this`() {
        val code = "\\class C { \\field f (x y : Nat) : Nat } \n" +
                "\\func h (c : C) (x : Nat) => f {{-caret-}c} x"
        val expectedHint = "<highlight>{this : C}</highlight>, x : Nat, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test func this`() {
        val code = "\\class C { \\func f (x y : Nat) : Nat => 0 } \n" +
                "\\func h (c : C) (x : Nat) => C.f {{-caret-}c} x"
        val expectedHint = "<highlight>{this : C}</highlight>, x : Nat, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test data constructors`() {
        val code = "\\data D (x : Nat) \n" +
                    "| cons (y : Nat)\n" +
                "\\func h (z : Nat) => cons {x} {-caret-}z"
        val expectedHint = "{x : Nat}, <highlight>y : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test level`() {
        val code = "\\func f (x : Nat) : \\level (Nat -> Nat) 0\n" +
                "\\func g (x : Nat) => f x {-caret-}"
        val expectedHint = "x : Nat, <highlight>_ : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test infix commas right`() {
        val code = "\\func f (x y : Nat) : Nat\n" +
                "\\func g => 0 `f` 0{-caret-}"
        val expectedHint = "x : Nat, <highlight>y : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test infix commas left`() {
        val code = "\\func f (x y : Nat) : Nat\n" +
                "\\func g => 0{-caret-} `f` 0"
        val expectedHint = "<highlight>x : Nat</highlight>, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test infix commas middle`() {
        val code = "\\func f (x y : Nat) : Nat\n" +
                "\\func g => 0 {-caret-}`f` 0"
        val expectedHint = "x : Nat, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test postfix comma arg`() {
        val code = "\\func f (x : Nat) : Nat\n" +
                "\\func g => 0{-caret-} `f"
        val expectedHint = "<highlight>x : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test postfix comma func`() {
        val code = "\\func f (x : Nat) : Nat\n" +
                "\\func g => 0 {-caret-}`f"
        val expectedHint = "x : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test constructor arguments`() = checkParameterInfo("""
       \data List (A : \Type) \Type
         | nil
         | :: A (List A A)

       \func lol => ::{-caret-} 
    """, "{A : \\Type}, {_ : \\Type}, _ : A, _ : List A A")

    fun `test constructors with patterns`() = checkParameterInfo("""
       \data Vec {X : \Type} (n : Nat) \elim n
         | zero => nullV
         | suc n => consV {X} (Vec {X} n)
  

       \func lol => consV {Nat} {0} {101}{-caret-} nullV 
    """, "{X : \\Type}, {n}, <highlight>{_ : X}</highlight>, _ : Vec {X} n")

    fun `test local coclause`() = checkParameterInfo("""
       \\class C (carrier : \Set) {
         | magma (x y : carrier) : carrier
       }
        
       \\func instance => \new C {
         | magma x y{-caret-} => Nat
       } 
    """, "x : carrier, <highlight>y : carrier</highlight>")

    fun `test patterns`() = checkParameterInfo("""
        \\data List (X : \Type)
          | nil
          | \infixr 1 :: X (List X)
          
        \\func foo {X : \Type} (l : List X) \with
          | l0 :: l{-caret-}s => {?} 
    """, "_ : X, <highlight>_ : List X</highlight>")


    fun `test this 2`() = checkParameterInfo("""
        \\class Pair
          | sum (a b : Nat) : Nat
          
        \func lol (p : Pair) => p{-caret-}.sum 1 2
    """, "<highlight>{this : Pair}</highlight>, a : Nat, b : Nat")

    fun `test this + postfix`() = checkParameterInfo("""
       \class Magma {X : \Set}
         | \infix 1 * (x y : X) : X

       \func usage (M : Magma {Nat}) => (`* {-caret-}1) 2 
    """, "{this : Magma}, x : X, <highlight>y : X</highlight>")

    fun `test this + postfix 2`() = checkParameterInfo("""
       \class Magma {X : \Set}
         | \infix 1 * (x y : X) : X

       \func usage (M : Magma {Nat}) => (M.`* {-caret-}1) 2 
    """, "{this : Magma}, x : X, <highlight>y : X</highlight>")

    fun `test dumbMode hints`() = checkParameterInfo("""
        \class Test {
          \func foo {a b c d : Nat} => a Nat.+ b Nat.+ c Nat.+ d \where {
            \func bar (p : a + b = c + d) : p = p => idp
          }
        }
                        
        \open Test.foo
        
        \func foobar => bar {\new Test} {1} {2} {2} {1}{-caret-} idp
        
    """, "{this : Test}, <highlight>???,</highlight> p : a + b = c + d")

    fun `test smartMode hints`() = checkParameterInfo("""
        \class Test {
          \func foo {a b c d : Nat} => a Nat.+ b Nat.+ c Nat.+ d \where {
            \func bar (p : a + b = c + d) : p = p => idp
          }
        }
                        
        \open Test.foo
        
        \func foobar => bar {\new Test} {1} {2} {2} {1}{-caret-} idp
        
    """, "{this : Test}, {a : Nat}, {b : Nat}, {c : Nat}, <highlight>{d : Nat}</highlight>, p : a + b = c + d", true)

    fun `test external parameters scope detection`() = checkParameterInfo("""
       \class Foo {u : Nat} {
         | v : Nat

       \func foo (w : Nat) => u Nat.+ v Nat.+ w \where {
         \func \infixl 1 +++ (x y : Nat) => u Nat.+ v Nat.+ w Nat.+ x Nat.+ y

         \func usage1 (a b c : Nat) => a +++ b +++ c
       }

       \func usage2 (a b c : Nat) => foo.+++ {_} {0{-caret-}} (foo.+++ {_} {0} a b) c
       } 
    """, "{this : Foo}, <highlight>{w : Nat}</highlight>, x : Nat, y : Nat", true)

    fun `test external parameters 2`() = checkParameterInfo("""
        \func foo (A : \Type) => 101 \where {
          \func bar (a : A) => a \where {
            \func lol (p : a = a) => {?}

            \func usage => lol {-caret-} idp
          }
       }
    """, "{A : \\Type}, {a : A}, <highlight>p : a = a</highlight>", typecheck = true)

}