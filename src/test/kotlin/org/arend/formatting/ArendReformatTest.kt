package org.arend.formatting

class ArendReformatTest : ArendFormatterTestBase() {
    // Wrap tests
    fun testWrapAfterComment() = checkReformat(
            "\\func lol -- Lol\n => 1",
            "\\func lol -- Lol\n  => 1")

    // Tests on correct spacing
    fun testStatementSpacing1() = checkReformat(
            "\\open Prelude.TrS \\func lol\n  => 1",
            "\\open Prelude.TrS\n\n\\func lol => 1")

    fun testStatementSpacing2() = checkReformat(
            "\\func lol1 => 1 \\where {\\open Prelude.TrS \\func lol => 1}",
            "\\func lol1 => 1 \\where {\n  \\open Prelude.TrS\n\n  \\func lol => 1\n}")

    fun testNsCmdSpacing() = checkReformat(
            "\\import Prelude  \\using   ( I , Nat  \\as   Nat' )  \\hiding  ( iso )",
            "\\import Prelude \\using (I, Nat \\as Nat') \\hiding (iso)")

    fun testDocTextSpacing1() = checkReformat("{- | lol\n -foo -}", "{- | lol\n - foo -}")

    fun testDocTextSpacing2() = checkReformat(
            "\\import Foo\n-- | Foo Bar \n\n\\func lol => 1",
            "\\import Foo\n\n-- | Foo Bar \n\\func lol => 1")

    fun testDocTextSpacing3() = checkReformat(
            "\\import Foo\n{- | Foo Bar -}\n\n\\func lol => 1",
            "\\import Foo\n\n{- | Foo Bar -}\n\\func lol => 1")

    // Tests on correctness of block subdivision
    fun testArgAppExpr1() = checkReformat(
            "\\func lol2 => Nat -- aa\n-bb",
            "\\func lol2 => Nat -- aa\n    -bb")

    fun testArgAppExpr2() = checkReformat(
            "\\class C {\n  | foo : Nat \\case foo\n}")

    fun testArgAppExpr3() = checkReformat(
            "\\func foobar (A : \\Type) => (=) 101 Nat.+ 1")

    fun testUnfinishedDocComment() = checkReformat(
            "{- | Doc comment\n - continues\n - but not finishes")

    // Indent tests
    fun testFunctionClausesIndent() = checkReformat(
            "\\func pred (x : Nat) : Nat\n| zero => 0\n| suc x' => x'",
            "\\func pred (x : Nat) : Nat\n  | zero => 0\n  | suc x' => x'")

    fun testExprInClause() = checkReformat(
            "\\func lol2 (a : Nat) \\elim a\n  | _ =>\n  1",
            "\\func lol2 (a : Nat) \\elim a\n  | _ =>\n    1")

    fun testExprOnNewLine() = checkReformat(
            "\\func lol =>\n1",
            "\\func lol =>\n  1")

    fun testClassImplement() = checkReformat(
            "\\class A (n : Nat)\n\n\\class B \\extends A\n| n => 0",
            "\\class A (n : Nat)\n\n\\class B \\extends A\n  | n => 0")

    fun testClassImplement2() = checkReformat(
            "\\class A (n : Nat)\n\n\\class B \\extends A\n  | n =>\n\\let x => 0\n\\in x",
            "\\class A (n : Nat)\n\n\\class B \\extends A\n  | n =>\n    \\let x => 0\n    \\in x")

    fun testCommentsIndent1() = checkReformat(
            "\\func - (n m : Nat) : Nat\n-- comment 1\n  | 0, _ => 0\n-- comment 2\n  | suc n, 0 => suc n\n-- comment 3\n  | suc n, suc m => n - m\n",
            "\\func - (n m : Nat) : Nat\n  -- comment 1\n  | 0, _ => 0\n  -- comment 2\n  | suc n, 0 => suc n\n  -- comment 3\n  | suc n, suc m => n - m\n")

    fun testCommentsIndent2() = checkReformat(
            "\\func - (n m : Nat) : Nat \\elim n, m\n-- comment 1\n  | 0, _ => 0\n-- comment 2\n  | suc n, 0 => suc n\n-- comment 3\n  | suc n, suc m => n - m\n",
            "\\func - (n m : Nat) : Nat \\elim n, m\n  -- comment 1\n  | 0, _ => 0\n  -- comment 2\n  | suc n, 0 => suc n\n  -- comment 3\n  | suc n, suc m => n - m\n")

    fun testCommentsIndent3() = checkReformat(
            "\\data \\fixr 2 Or (A B : \\Type)\n-- foo\n| inl A\n-- bar\n| inr B\n",
            "\\data \\fixr 2 Or (A B : \\Type)\n  -- foo\n  | inl A\n  -- bar\n  | inr B\n")

    fun testLamInTuple() = checkReformat(
            "\\func foo => (\\lam a b =>\n    a)")

    fun testNewInTuple() = checkReformat(
            "\\func lol => 1 + 1 + 1 + (\\new Foo {\n  | foo => 1\n})")

    fun testDocTextIndent() = checkReformat(
            "          {- | Doc Text 1\n          -\n          -  -}\n\n\n\\func foo =>\n  \\let\n           {- | Doc Text 2\n            -\n            -  -}\n    | x => 0\n  \\in x\n",
            "{- | Doc Text 1\n -\n -  -}\n\\func foo =>\n  \\let\n    {- | Doc Text 2\n     -\n     -  -}\n    | x => 0\n  \\in x")

    fun testLetClauseWithoutPipe() = checkReformat(
            "\\func bar =>\n  \\let\n  x => 1\n  \\in x",
            "\\func bar =>\n  \\let\n    x => 1\n  \\in x")

    fun testDocCommentBeforeImport() = checkReformat(
            "{- | Doc\n - Comment -}\\import Prelude",
            "{- | Doc\n - Comment -}\n\n\\import Prelude")

    fun testConstructorsWithPatterns() = checkReformat(
            "\\data D (n : Nat) \\with\n| 0 => {\n| con1\n| con2\n}",
            "\\data D (n : Nat) \\with\n  | 0 => {\n    | con1\n    | con2\n  }")

    fun testUnaryBinOpIndent() = checkReformat(
            "\\func test =>\n+ 1",
            "\\func test =>\n  + 1")

    fun testBinOpIndent() = checkReformat(
            "\\open Nat\n\n\\func test => 1\n+ 2\n+ 3",
            "\\open Nat\n\n\\func test => 1\n  + 2\n  + 3")

    fun testMultilineDocCommentWithSuddenEnd() = checkReformat("{- | a\n -}", "{- | a\n -}")

    fun test246() = checkReformat("\\func f (n : Nat) : Nat \\elim n\n  | _ => {?} -- asda",
        "\\func f (n : Nat) : Nat \\elim n\n  | _ => {?} -- asda")

    fun testLetWithNewlinesAfterFatArrow() = checkReformat(
    """
        \func f : Nat => \let 
          x => 1
           \in 2""".trimIndent(),
    """
        \func f : Nat =>
          \let
            x => 1
          \in 2""".trimIndent())

    fun testLetWithNewlinesAfterCall() = checkReformat(
    """
        \func f : Nat -> Nat => f (\let
         x => 1 
         \in 2)""".trimIndent(),
    """
        \func f : Nat -> Nat => f (
          \let
            x => 1 
          \in 2)""".trimIndent())
}