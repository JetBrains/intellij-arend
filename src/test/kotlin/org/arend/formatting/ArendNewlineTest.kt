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
            "\\func lol (a : Nat)\n  {-caret-}")

    fun testAfterFunc5() = checkNewLine(
            "\\func lol (a : Nat){-caret-}\n  ",
            "\\func lol (a : Nat)\n  {-caret-}\n  ")

    fun testAfterFunc6() = checkNewLine(
            "\\func lol (a : Nat){-caret-}",
            "\\func lol (a : Nat)\n  \n  {-caret-}", 2)

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

    fun testInstance4() = checkNewLine(
            "\\class C { a : Nat } \n\n\\instance I : C{-caret-}",
            "\\class C { a : Nat } \n\n\\instance I : C\n  {-caret-}")

    fun testWhere1() = checkNewLine(
            "\\func lol => 1 \\where{-caret-}",
            "\\func lol => 1 \\where\n  {-caret-}")

    fun testWhere2() = checkNewLine(
            "\\func lol => foo \\where \\func foo => 1{-caret-}",
            "\\func lol => foo \\where \\func foo => 1\n{-caret-}")

    fun testWhere4() = checkNewLine(
            "\\func lol => 1 \\where {\n  \\func lol2 (n : Nat) \\elim n\n    | 0 => 1{-caret-}\n}",
            "\\func lol => 1 \\where {\n  \\func lol2 (n : Nat) \\elim n\n    | 0 => 1\n    {-caret-}\n}")

    fun testWhere5() = checkNewLine(
            "\\func lol => 1 \\where {{-caret-}\n  \\func lol2 => 2\n}",
            "\\func lol => 1 \\where {\n  {-caret-}\n  \\func lol2 => 2\n}")

    fun testWhere6() = checkNewLine(
            "\\func bar => 0\n  \\where\n    \\func foo (x : Nat)\n      | 0 => 0{-caret-}",
            "\\func bar => 0\n  \\where\n    \\func foo (x : Nat)\n      | 0 => 0\n      {-caret-}")

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

    fun testAfterDataWith2() = checkNewLine(
            "\\data So (b : Bool) \\with{-caret-}\n  | true => so",
            "\\data So (b : Bool) \\with\n  {-caret-}\n  | true => so")

    fun testAfterDataConsWith() = checkNewLine(
            "\\data D (x : Nat) : \\Prop\n  | cons (y : Nat) \\with {{-caret-}}",
            "\\data D (x : Nat) : \\Prop\n  | cons (y : Nat) \\with {\n    {-caret-}\n  }")

    fun testFirstDataCons() = checkNewLine(
            "\\data Foo{-caret-}\n  |  A (b : Nat)",
            "\\data Foo\n  {-caret-}\n  |  A (b : Nat)")

    fun testAfterDataTele() = checkNewLine(
            "\\data Foo (A : \\Type){-caret-}",
            "\\data Foo (A : \\Type)\n  {-caret-}")

    fun testFirstClassField() = checkNewLine(
            "\\class A1 {}\n\\class A \\extends A1{-caret-}\n  | f (b : Nat) : Nat\n",
            "\\class A1 {}\n\\class A \\extends A1\n  {-caret-}\n  | f (b : Nat) : Nat\n")

    fun testAlignmentInTele() = checkNewLine(
            "\\func lol => \\Sigma (A : Nat){-caret-}",
            "\\func lol => \\Sigma (A : Nat)\n                    {-caret-}")

    fun testCoClauses1() = checkNewLine(
            "\\class A {f : Nat}\n\\func lol2 (n : Nat) : A \\cowith{-caret-}\n  | f => 101",
            "\\class A {f : Nat}\n\\func lol2 (n : Nat) : A \\cowith\n  {-caret-}\n  | f => 101")

    fun testCoClauses2() = checkNewLine(
            "\\class A {f : Nat}\n\\func lol2 (n : Nat) : A \\cowith\n  | f => 101{-caret-}",
            "\\class A {f : Nat}\n\\func lol2 (n : Nat) : A \\cowith\n  | f => 101\n  {-caret-}")

    /* fun testCoClauses3() = checkNewLine(
            "\\class Lol { | foo : Nat }\n\\func bar => \\new Lol {\n{-caret-}  | foo => 1 }\n",
            "\\class Lol { | foo : Nat }\n\\func bar => \\new Lol {\n  {-caret-}\n  | foo => 1 }\n") */ //Fixme

    fun testTele1() = checkNewLine(
            "\\func lol (A : Nat) (B : Nat){-caret-}\n (C : Nat)",
            "\\func lol (A : Nat) (B : Nat)\n          {-caret-}\n (C : Nat)")

    fun testArgAppExpr() = checkNewLine(
            "\\func lol => (=) 1{-caret-} 2",
            "\\func lol => (=) 1\n                 {-caret-}2")

    fun testArgAppExpr2() = checkNewLine(
            "\\func foobar (A : \\Type) => (=) A 101{-caret-}",
            "\\func foobar (A : \\Type) => (=) A 101\n    {-caret-}")

    fun testArgAppExpr3() = checkNewLine(
            "\\func foobar (A : \\Type) => (=) A\n                                101{-caret-}",
            "\\func foobar (A : \\Type) => (=) A\n                                101\n                                {-caret-}")

    fun testPi1() = checkNewLine(
            "\\func foo => \\Pi (A : Nat) ->{-caret-}",
            "\\func foo => \\Pi (A : Nat) ->\n    {-caret-}") //continuation

    fun testPi2() = checkNewLine(
            "\\func foo => \\Pi (A : Nat){-caret-}",
            "\\func foo => \\Pi (A : Nat)\n                 {-caret-}")

    fun testPi3() = checkNewLine(
            "\\func foo => \\Pi (A : Nat) {-caret-}\n-> A",
            "\\func foo => \\Pi (A : Nat) \n                 {-caret-}\n-> A")

    fun testNoIndentAfterWhere1() = checkNewLine(
            "\\module Foo \\where {}{-caret-}",
            "\\module Foo \\where {}\n{-caret-}")

    fun testNoIndentAfterWhere2() = checkNewLine(
            "\\func foo \\where {}{-caret-}",
            "\\func foo \\where {}\n{-caret-}")
}
