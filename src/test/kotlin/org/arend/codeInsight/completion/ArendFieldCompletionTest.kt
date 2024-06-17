package org.arend.codeInsight.completion


class ArendFieldCompletionTest : ArendCompletionTestBase() {
    fun `test local variable`() =
        checkCompletionVariants(
            "\\class C { | f : Nat | g : Nat }\n" +
            "\\func test (c : C) => c.{-caret-}",
            listOf("f", "g"))

    fun `test iterated`() =
        checkCompletionVariants(
            "\\class A { | f : Nat }\n" +
            "\\class B { | g : A }\n" +
            "\\func test (b : B) => b.g.{-caret-}",
            listOf("f"))

    fun `test field parameter`() =
        checkCompletionVariants(
            "\\class A (g : Nat) { | f : Nat }\n" +
            "\\func test (a : A) => a.{-caret-}",
            listOf("g", "f"))

    fun `test function`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func func : A\n" +
            "\\func test => func.{-caret-}",
            listOf("f", "g"))

    fun `test instance`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\instance inst : A\n" +
            "\\func test => inst.{-caret-}",
            listOf("f", "g"))

    fun `test extended`() =
        checkCompletionVariants(
            "\\class A { | f : Nat }\n" +
            "\\class B \\extends A { | g : Nat }\n" +
            "\\func test (b : B) => b.{-caret-}",
            listOf("f", "g"))

    fun `test function with parameters`() =
        checkNoCompletion(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func func (x : Nat) : A\n" +
            "\\func test => func.{-caret-}")

    fun `test function with implicit parameters`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func func {x y : Nat} {z : Nat -> Nat} : A\n" +
            "\\func test => func.{-caret-}",
            listOf("f", "g"))


    fun `test complex`() =
        checkCompletionVariants(
            "\\class A (f : Nat) { | g : Nat }\n" +
            "\\class B \\extends A { | h : Nat }\n" +
            "\\class C { | a : A | b : B }\n" +
            "\\func test (c : C) => c.b.{-caret-}",
            listOf("f", "g", "h"))

    fun `test function reference`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func B => A\n" +
            "\\func test (a : B) => a.{-caret-}",
            listOf("f", "g"))

    fun `test iterated function reference`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func C => B\n" +
            "\\func B => A\n" +
            "\\func test (a : C) => a.{-caret-}",
            listOf("f", "g"))

    fun `test recursive function reference`() =
        checkNoCompletion(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func C => B\n" +
            "\\func B => C\n" +
            "\\func test (a : C) => a.{-caret-}")

    fun `test function with parameters reference`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func B (x : Nat) => A\n" +
            "\\func test (a : B 0) => a.{-caret-}",
            listOf("f", "g"))

    fun `test function with no parameters reference`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func B (x : Nat) => A\n" +
            "\\func test (a : B) => a.{-caret-}",
            listOf("f", "g"))

    fun `test infix function with parameters reference`() =
        checkCompletionVariants(
            "\\class A { | f : Nat | g : Nat }\n" +
            "\\func \\infixr 3 + (x y : Nat) => A\n" +
            "\\func test (a : 0 + 1) => a.{-caret-}",
            listOf("f", "g"))

    fun `test class field`() =
        doSingleCompletionMultifile("""
        -- ! A.ard
        \class Foo {
          | <=-transitive \alias <=o : Nat -> Nat
        }
        -- ! Main.ard
        \func lol => <=-tran{-caret-}
            """, """
        \import A
        
        \func lol => <=o
            """)

    fun `_test pattern`() =
        checkCompletionVariants(
            "\\class A (f g : Nat)\n" +
            "\\data D | con A\n" +
            "\\func test (d : D) : Nat | con (a : A) => a.{-caret-}",
            listOf("f", "g"))
}