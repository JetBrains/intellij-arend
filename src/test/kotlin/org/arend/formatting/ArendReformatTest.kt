package org.arend.formatting

class ArendReformatTest : ArendFormatterTestBase() {
    // Wrap tests
    fun testWrapAfterComment() = checkReformat(
            "\\func lol --Lol\n => 1",
            "\\func lol --Lol\n  => 1")

    // Indent tests
    fun testFunctionClausesIndent() = checkReformat(
            "\\func pred (x : Nat) : Nat\n| zero => 0\n| suc x' => x'",
            "\\func pred (x : Nat) : Nat\n  | zero => 0\n  | suc x' => x'")

    fun testClassSynonymIndent() = checkReformat(
            "\\class A | f : Nat\n\n\\class Foo => A {\n| f => f101\n}",
            "\\class A | f : Nat\n\n\\class Foo => A {\n  | f => f101\n}")

    fun testExprInClause() = checkReformat(
            "\\func lol2 (a : Nat) \\elim a\n  | _ =>\n  1",
            "\\func lol2 (a : Nat) \\elim a\n  | _ =>\n    1")

    fun testExprOnNewLine() = checkReformat(
            "\\func lol =>\n1",
            "\\func lol =>\n  1")

    // Tests on correct spacing
    fun testStatementSpacing1() = checkReformat(
            "\\open Prelude.TrS \\func lol\n  => 1",
            "\\open Prelude.TrS\n\n\\func lol => 1")

    fun testStatementSpacing2() = checkReformat(
            "\\func lol1 => 1 \\where {\\open Prelude.TrS \\func lol => 1}",
            "\\func lol1 => 1 \\where {\n  \\open Prelude.TrS\n\n  \\func lol => 1\n}")

    // Tests on correctness of block subdivision
    fun testArgAppExpr1() = checkReformat(
            "\\func lol2 => Nat --aa\n-bb",
            "\\func lol2 => Nat --aa\n    -bb")

    fun testArgAppExpr2() = checkReformat(
            "\\class C {\n  | foo : Nat \\case foo\n}",
            "\\class C {\n  | foo : Nat \\case foo\n}")

    fun testClassImplement() = checkReformat(
            "\\class A (n : Nat)\n\n\\class B \\extends A\n| n => 0",
            "\\class A (n : Nat)\n\n\\class B \\extends A\n  | n => 0")

    fun testClassImplement2() = checkReformat(
            "\\class A (n : Nat)\n\n\\class B \\extends A\n  | n =>\n\\let x => 0\n\\in x",
            "\\class A (n : Nat)\n\n\\class B \\extends A\n  | n =>\n    \\let x => 0\n    \\in x")
}