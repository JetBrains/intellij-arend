package org.vclang.codeInsight.completion


class VcFieldCompletionTest : VcCompletionTestBase() {
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
}