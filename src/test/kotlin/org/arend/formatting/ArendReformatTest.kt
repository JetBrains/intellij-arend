package org.arend.formatting

class ArendReformatTest : ArendFormatterTestBase() {
    fun testWrapAfterComment() = checkReformat("\\func lol --Lol\n => 1",
            "\\func lol --Lol\n  => 1")

    fun testFunctionClausesIndent() = checkReformat("\\func pred (x : Nat) : Nat\n| zero => 0\n| suc x' => x'",
            "\\func pred (x : Nat) : Nat\n  | zero => 0\n  | suc x' => x'")

    fun testLineCommentsInArgAppExpr() = checkReformat("\\func lol2 => Nat --aa\n-bb",
            "\\func lol2 => Nat --aa\n    -bb")

    fun testExprInClause() = checkReformat("\\func lol2 (a : Nat) \\elim a\n  | _ =>\n  1",
            "\\func lol2 (a : Nat) \\elim a\n  | _ =>\n    1")

    fun testExprOnNewLine() = checkReformat("\\func lol =>\n1", "\\func lol =>\n  1")
}