package org.arend.formatting

class ArendNewlineTest : ArendFormatterTestBase() {
    fun testAfterFunc1() = checkNewLine(
            "\\func lol (a : Nat) =>{-caret-}",
            "\\func lol (a : Nat) =>\n  {-caret-}")

    fun testAfterFunc2() = checkNewLine(
            "\\func lol (a : Nat) =>{-caret-}\n\\func lol2 => 1",
            "\\func lol (a : Nat) =>\n  {-caret-}\n\\func lol2 => 1")

    fun testAfterFunc3() = checkNewLine(
            "\\func lol (n : Nat) \\elim n{-caret-}",
            "\\func lol (n : Nat) \\elim n\n  {-caret-}")

    fun testAfterFunc4() = checkNewLine(
            "\\func lol (a : Nat){-caret-}",
            "\\func lol (a : Nat)\n{-caret-}")

    fun testAfterFunc5() = checkNewLine(
            "\\func lol (a : Nat){-caret-}\n  ",
            "\\func lol (a : Nat)\n{-caret-}\n  ")

    fun testAfterFunc6() = checkNewLine(
            "\\func lol (a : Nat){-caret-}",
            "\\func lol (a : Nat)\n\n{-caret-}", 2)

    fun testAfterFunc7() = checkNewLine(
            "\\func lol (a : Nat) => 1{-caret-}",
            "\\func lol (a : Nat) => 1\n{-caret-}")

    fun testAfterFunc8() = checkNewLine(
            "\\func lol (a : Nat) \\elim a | _ => 1{-caret-}",
            "\\func lol (a : Nat) \\elim a | _ => 1\n                            {-caret-}")

    fun testAfterFunc9() = checkNewLine(
            "\\func pred (x : Nat) : Nat\n  | zero => 0\n  | suc x' => x'{-caret-}\n\n \\func lol => 1",
            "\\func pred (x : Nat) : Nat\n  | zero => 0\n  | suc x' => x'\n  {-caret-}\n\n \\func lol => 1")
}
