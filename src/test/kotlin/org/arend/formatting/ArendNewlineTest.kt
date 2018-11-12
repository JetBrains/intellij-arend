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

    fun testInExpr1() = checkNewLine(
            "\\func lol4 => ({-caret-})",
            "\\func lol4 => (\n  {-caret-}\n)")

    fun testInstance1() = checkNewLine(
            "\\class C { a : Nat } \n\n\\instance I : C\n  | a => 1{-caret-}",
            "\\class C { a : Nat } \n\n\\instance I : C\n  | a => 1\n  {-caret-}")

    fun testInstance2() = checkNewLine(
            "\\class C { a : Nat } \n\n\\instance I : C{-caret-}\n  | a => 1\n",
            "\\class C { a : Nat } \n\n\\instance I : C\n  {-caret-}\n  | a => 1\n")

    fun testInstance3() = checkNewLine(
            "\\class C { a : Nat } \n\n\\instance I : C\n  | a => 1{-caret-}\n\n\\func lol => 1",
            "\\class C { a : Nat } \n\n\\instance I : C\n  | a => 1\n  {-caret-}\n\n\\func lol => 1")

    fun testWhere1() = checkNewLine(
            "\\func lol => 1 \\where{-caret-}",
            "\\func lol => 1 \\where\n  {-caret-}")

    fun testInstance4() = checkNewLine(
            "\\class C { a : Nat } \n\n\\instance I : C\n  | a => 1\n  \\where \\func lol => 1{-caret-}",
            "\\class C { a : Nat } \n\n\\instance I : C\n  | a => 1\n  \\where \\func lol => 1\n{-caret-}")

    fun testExprInClause() = checkNewLine(
            "\\func lol2 (a : Nat) \\elim a\n  | _ =>{-caret-}",
            "\\func lol2 (a : Nat) \\elim a\n  | _ =>\n    {-caret-}")

    fun testInsideCaseBraces() = checkNewLine(
            "\\func lol2 (a : Nat) => \\case a \\with {{-caret-}}",
            "\\func lol2 (a : Nat) => \\case a \\with {\n  {-caret-}\n}")

    fun testAfterLastClassExpr() = checkNewLine(
            "\\class Lol\n  | a : Nat{-caret-}\n\\func lol2 => 1",
            "\\class Lol\n  | a : Nat\n  {-caret-}\n\\func lol2 => 1")

    fun testAfterDataWith() = checkNewLine(
            "\\data D (x : Nat) : \\Prop \\with{-caret-}",
            "\\data D (x : Nat) : \\Prop \\with\n  {-caret-}")

    fun testAfterDataConsWith() = checkNewLine(
            "\\data D (x : Nat) : \\Prop\n  | cons (y : Nat) \\with {{-caret-}}",
            "\\data D (x : Nat) : \\Prop\n  | cons (y : Nat) \\with {\n    {-caret-}\n  }")

}
