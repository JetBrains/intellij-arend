package org.arend.formatting

class ArendReformatTest : ArendFormatterTestBase() {
    fun testWrapAfterComment() = checkReformat("\\func lol --Lol\n => 1",
            "\\func lol --Lol\n  => 1")

    fun testFunctionClausesIndent() = checkReformat("\\func pred (x : Nat) : Nat\n| zero => 0\n| suc x' => x'",
            "\\func pred (x : Nat) : Nat\n  | zero => 0\n  | suc x' => x'")
}