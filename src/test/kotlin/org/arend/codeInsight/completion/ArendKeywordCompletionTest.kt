package org.arend.codeInsight.completion

import org.arend.psi.ArendElementTypes

class ArendKeywordCompletionTest : ArendCompletionTestBase() {
    fun `test fixity completion`() =
            checkKeywordCompletionVariants(FIXITY_KWS, CompletionCondition.SAME_ELEMENTS,
                    "\\func {-caret-}test => 0",
                    "\\func {-caret-}",
                    "\\class {-caret-}testClass {}",
                    "\\class {-caret-}",
                    "\\data {-caret-}MyNat | myzero",
                    "\\data {-caret-}",
                    "\\import B (lol \\as {-caret-}+)",
                    "\\data MyNat | \\coerce {-caret-} myzero",
                    "\\class C (foo : Nat)\n  | \\coerce {-caret-}",
                    "\\class C {\n  | \\coerce {-caret-} bar : Nat }",
                    "\\class C {\n  \\field \\coerce {-caret-} bar : Nat\n}",
                    "\\class C (foo : Nat)\n  | \\classifying {-caret-}",
                    "\\class C (foo : Nat) {\n  | \\coerce {-caret-}\n}",
                    "\\class C (foo : Nat) {\n  | \\classifying {-caret-}\n}",
                    "\\class C (foo : Nat)\n   | \\coerce {-caret-} bar : Nat",
                    "\\func f \\alias {-caret-} => {?}")

    fun `test fixity + coerce + visibility completion`() =
            checkKeywordCompletionVariants(FIXITY_KWS + COERCE_KW_LIST + ACCESS_MODIFIERS, CompletionCondition.SAME_KEYWORDS,
                    "\\data MyNat | {-caret-}myzero",
                    "\\data MyNat | {-caret-}",
                    "\\record R\n  | {-caret-} foo : Nat",
                    "\\record R{\n  | {-caret-} foo : Nat\n}"
                )

    fun `test fixity + visibility completion`() =
        checkKeywordCompletionVariants(FIXITY_KWS + ACCESS_MODIFIERS, CompletionCondition.SAME_KEYWORDS,
            "\\data Fin (n : Nat) \\with | suc n => {-caret-}fzero | suc n => fsuc (Fin n)")

    fun `test fixity + coerce completion after access modifiers`() =
        checkKeywordCompletionVariants(FIXITY_KWS + COERCE_KW_LIST, CompletionCondition.SAME_KEYWORDS,
            "\\data D\n | \\protected {-caret-}\n | cons2 "
            ,"\\data D\n  | \\private {-caret-} cons1",
            "\\data D\n | \\private {-caret-}",
            "\\record R\n  | \\private {-caret-} foo : Nat",
            "\\record R{\n  | \\protected {-caret-} foo : Nat\n}")

    fun `test fixity + coerce + classifying + visibility completion`() =
            checkKeywordCompletionVariants(FIXITY_KWS + COERCE_KW_LIST + CLASSIFYING_KW_LIST + ACCESS_MODIFIERS, CompletionCondition.SAME_KEYWORDS,
                    "\\class C\n  | {-caret-} foo : Nat",
                    "\\class Monoid (El : \\Set) { | {-caret-}* : El -> El -> El}",
                    "\\class Monoid { | {-caret-} }",
                    "\\class Monoid | {-caret-}")

    fun `test fixity + coerce + classifying completion`() =
        checkKeywordCompletionVariants(FIXITY_KWS + COERCE_KW_LIST + CLASSIFYING_KW_LIST, CompletionCondition.SAME_KEYWORDS,
            "\\class Monoid (El : \\Set) { \\field {-caret-} }",
            "\\class C\n  | \\protected {-caret-} foo : Nat",
            "\\class Monoid (El : \\Set) { | \\private {-caret-}* : El -> El -> El}",
            "\\class Monoid { | \\protected {-caret-} }",
            "\\class Monoid | \\private {-caret-}")

    fun `test no fixity completion`() =
            checkKeywordCompletionVariants(FIXITY_KWS, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\func foo (n : Nat) \\elim n | {-caret-}zero =>",
                    "\\func foo (n : Nat) => {-caret-}n ",
                    "\\func lol (a : Nat) => \\case a \\as {-caret-} \\with { }",
                    "\\class Lol { } \\where { \\use \\level {-caret-} } ",
                    "\\class Lol { } \\where { \\use \\coerce {-caret-} }")

    fun `test as completion in namespace command`() =
            checkKeywordCompletionVariants(AS_KW_LIST, CompletionCondition.SAME_ELEMENTS, "\\import B (lol {-caret-})")

    fun `test nsCmd completion in namespace command 1`() =
            checkKeywordCompletionVariants(HIDING_KW_LIST, CompletionCondition.SAME_ELEMENTS, "\\import B (lol) {-caret-}")

    fun `test nsCmd completion in namespace command 2`() =
            checkKeywordCompletionVariants(HIDING_KW_LIST, CompletionCondition.CONTAINS, "\\import B (lol)\n{-caret-}")

    fun `test nsCmd completion in namespace command 3`() =
            checkKeywordCompletionVariants(HU_KW_LIST, CompletionCondition.CONTAINS, "\\import B {-caret-}")

    fun `test nsCmd completion in namespace command 4`() =
            checkKeywordCompletionVariants(HU_KW_LIST, CompletionCondition.SAME_ELEMENTS, "\\import B {-caret-}(lol)")

    fun `test nsCmd completion in namespace command 5`() =
            checkKeywordCompletionVariants(emptyList(), CompletionCondition.SAME_ELEMENTS,
                    "\\import B {-caret-}\\using (lol)",
                    "\\import B \\using (lol) {-caret-} \\hiding (lol)",
                    "\\import B \\hiding {-caret-} (lol)",
                    "\\import B \\hiding {-caret-}")

    fun `test nsCmd completion in namespace command 6`() =
            checkKeywordCompletionVariants(GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN, "\\import {-caret-}")

    fun `test root keywords completion 1`() =
            checkKeywordCompletionVariants(GLOBAL_STATEMENT_KWS, CompletionCondition.CONTAINS,
                    "\\import B\n {-caret-}\\func foo => 0 \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ",
                    "\\import B \\func foo => 0 \\data bar | foobar\n {-caret-}\\func f => 0 \\where { \\func g => 1 } ",
                    "\\import B \\hiding (a)\n{-caret-}\\func foo => 0 \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ",
                    "\\func f (xs : Nat) : Nat \\elim xs\n | suc x => \\case x \\with {| zero => 0 | suc _ => 1}\n {-caret-}",
                    "\\class A {}\n {-caret-}",
                    "\\func foo => 0\n  \\where\n    \\func bar => 1\n\n{-caret-}\n\n\\func baz => 2")

    fun `test root keywords completion 2`() =
            checkKeywordCompletionVariants(STATEMENT_WT_KWS + TRUNCATED_KW_LIST + IMPORT_KW_LIST, CompletionCondition.CONTAINS,
                    "\\import B \\func foo => 0\n {-caret-}\\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ")

    fun `test root keywords completion after visibility modifier`() =
        checkKeywordCompletionVariants(STATEMENT_WT_KWS, CompletionCondition.CONTAINS,"" +
                "\\private {-caret-}",
                "\\func lol => 101 \\where {\n  \\protected {-caret-}\n}",
                "\\class C {\n  \\private {-caret-}\n}"
            )
    fun `test truncated`() =
        checkKeywordCompletionVariants(TRUNCATED_KW_LIST, CompletionCondition.CONTAINS,"\\private {-caret-} \\data")

    private val kwSuite1 =
            arrayOf("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\n{-caret-}\\func g => 1 } ",
                    "\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n {-caret-}}",
                    "\\func foo => 0 \\where {\n{-caret-} }",
                    "\\use \\coerce Lol => 101 \\where {\n {-caret-} }")

    fun `test local statement keywords completion in func where block`() =
            checkKeywordCompletionVariants(LOCAL_STATEMENT_KWS, CompletionCondition.CONTAINS, *kwSuite1)

    fun `test local statement keywords completion in func where block 2`() =
            checkKeywordCompletionVariants(USE_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n {-caret-}}")

    fun `test keywords after use in func`() =
            checkKeywordCompletionVariants(LEVEL_KW_LIST, CompletionCondition.SAME_KEYWORDS,
                    "\\func lol => 1 \\where { \\use {-caret-} }")

    fun `test keywords after use in data`() =
            checkKeywordCompletionVariants(COERCE_LEVEL_KWS, CompletionCondition.SAME_KEYWORDS,
                    "\\data Lol \\where {\n \\use {-caret-} }")

    fun `test local statement keywords in data`() =
            checkKeywordCompletionVariants(LOCAL_STATEMENT_KWS, CompletionCondition.CONTAINS,
                    "\\data Lol \\where {\n {-caret-} }")

    private val kwSuiteInsideClass =
            arrayOf("\\class Foo { \n {-caret-} }",
                    "\\class Foo { | A : Nat\n {-caret-} }",
                    "\\class Foo { | A : Nat\n \\func lol => 0\n {-caret-} \n\\func lol2 => 0 }")

    private val kwSuiteInsideClassWhere =
            arrayOf("\\class Foo { } \\where {\n {-caret-}\n}")

    fun `test local statement keywords in class`() =
            checkKeywordCompletionVariants(LOCAL_STATEMENT_KWS + CLASS_MEMBER_KWS, CompletionCondition.CONTAINS, *kwSuiteInsideClass)

    fun `test no use keyword inside class`() =
            checkKeywordCompletionVariants(USE_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN, *kwSuiteInsideClass)

    fun `test use keyword in class where`() =
            checkKeywordCompletionVariants(USE_KW_LIST, CompletionCondition.CONTAINS, *kwSuiteInsideClassWhere + arrayOf(
                    "\\func foo => 1 \\where {\n  {-caret-}\n}"))

    fun `test field and property`() =
            checkKeywordCompletionVariants(CLASS_MEMBER_KWS, CompletionCondition.CONTAINS,
                    "\\record R {\n  {-caret-}\n}",
                    "\\record R {\n  | F : Nat\n  {-caret-}\n}")

    fun `test no field and property`() =
            checkKeywordCompletionVariants(CLASS_MEMBER_KWS, CompletionCondition.DOES_NOT_CONTAIN, *kwSuiteInsideClassWhere + arrayOf(
                    "\\record R\n  {-caret-}",
                    "\\record R\n  | F : Nat\n  {-caret-}"))

    private val kwModuleInsideClass =
            arrayOf("\\class Foo { \\module Bar { {-caret-} } }")

    fun `test no field and property in nested where`() =
            checkKeywordCompletionVariants(CLASS_MEMBER_KWS, CompletionCondition.DOES_NOT_CONTAIN,
                    *(kwModuleInsideClass + arrayOf(
                    "\\class Lol { \\func lol \\where {\n  {-caret-} } }",
                    "\\class Lol { } \\where { \\func lol \\where {\n {-caret-} } }",
                    "\\class Lol { \\func lol \\where\n  {-caret-} }",
                    "\\class Lol { } \\where { \\func lol \\where \n {-caret-} }")) )

    fun `test no use keyword in nested where`() =
            checkKeywordCompletionVariants(USE_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN, *kwModuleInsideClass)

    fun `test no root keywords completion`() =
            checkKeywordCompletionVariants(STATEMENT_WT_KWS + TRUNCATED_DATA_KW_LIST + IMPORT_KW_LIST + USE_KW_LIST,
                    CompletionCondition.DOES_NOT_CONTAIN,
                    "\\class Foo {| A : Nat\n {-caret-} \n | B : Nat }",
                    "\\class Foo {\n {-caret-} \n | A : Nat }",
                    "\\class Foo => Foo' {\n {-caret-} }", /* no statements in completion for class rename */
                    "\\class Foo => Foo' {\n {-caret-} | A => A' }",
                    "\\class Foo => Foo' {| A => A'\n {-caret-} }")

    fun `test root keywords completion 3`() =
            checkKeywordCompletionVariants(GLOBAL_STATEMENT_KWS + ACCESS_MODIFIERS, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\class A {| foo : Nat}\n\\func f => \\new A {| foo => 0 |\n{-caret-}=> 1\n}\n")

    fun `test no import in completion 1`() =
            checkKeywordCompletionVariants(IMPORT_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\n{-caret-}\\func g => 1 } ",
                    "\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n {-caret-}}",
                    "\\class A {\n {-caret-} }")

    fun `test no coerce or class members in global context`() =
            checkKeywordCompletionVariants(USE_KW_LIST + CLASS_MEMBER_KWS, CompletionCondition.DOES_NOT_CONTAIN,
                    "{-caret-}")

    fun `test class members inside class`() = checkKeywordCompletionVariants(CLASS_MEMBER_KWS, CompletionCondition.CONTAINS,
            "\\record R {\n {-caret-} | x : Nat }",
            "\\record R {\n | y : Nat\n {-caret-}\n | x : Nat }")

    fun `test root completion in empty context`() =
            checkKeywordCompletionVariants(GLOBAL_STATEMENT_KWS, CompletionCondition.CONTAINS, "{-caret-}")

    fun `test completion after truncated 2`() =
            checkSingleCompletion("\\tru{-caret-}", "\\data")

    fun `test completion after truncated 3`() =
            checkKeywordCompletionVariants(DATA_KW_LIST, CompletionCondition.SAME_ELEMENTS, "\\truncated {-caret-}")

    fun `test completion after truncated 4`() =
            checkSingleCompletion("\\truncated \\da{-caret-}", DATA_KW_LIST[0])

    fun `test completion after truncated 5`() =
            checkSingleCompletion("\\tru{-caret-}\\func", "\\truncated \\data \\func")

    fun `test completion in resulting type`() =
            checkKeywordCompletionVariants(DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST + LEVEL_KW_LIST, CompletionCondition.SAME_KEYWORDS,
                    "\\func foo (a : Nat) : {-caret-}",
                    "\\class C\n  | f : {-caret-}")

    fun `test completion in resulting type 2`() =
        checkKeywordCompletionVariants(DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST + LEVEL_KW_LIST + THIS_KW_LIST, CompletionCondition.SAME_KEYWORDS,
            "\\class C { | f : {-caret-} }")

    fun `test no keyword completion after instance, open or wrong state`() =
            checkKeywordCompletionVariants(GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\instance {-caret-}",
                    "\\open {-caret-}",
                    "\\func f (a : Nat) : Nat => {-caret-}",
                    "\\func f ({-caret-}")

    fun `test no where completion in empty context, after import, open`() =
            checkKeywordCompletionVariants(WHERE_KW_FULL, CompletionCondition.DOES_NOT_CONTAIN,
                    "{-caret-}",
                    "\\import A\n{-caret-}",
                    "\\open Nat\n{-caret-}",
                    "\\class C {\n  \\private {-caret-} \n}")

    fun `test where completion after func, data, class, iterated where`() =
            checkKeywordCompletionVariants(WHERE_KW_FULL, CompletionCondition.CONTAINS,
                    "\\func lol => 0\n{-caret-}",
                    "\\class Lol {} \\where {\n  \\func lol => 1 {-caret-}\n}",
                    "\\class Lol { \\func lol => 1 {-caret-} }",
                    "\\data Lol | lol1 | lol2\n{-caret-}",
                    "\\class Lol {}\n{-caret-}",
                    "\\func foo => 0 \\where \\func bar => 0\n{-caret-}",
                    "\\class foo {-caret-}",
                    "\\module bar {-caret-}")

    fun `test no where completion after iterated where`() =
            checkKeywordCompletionVariants(WHERE_KW_FULL, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\func foo => 0 \\where {\\func bar => 0}\n{-caret-}",
                    "\\func foo => 0 \\where \n {-caret-}",
                    "\\func foo => \\case {-caret-}" /* where not allowed after case keyword */,
                    "\\func lol => Foo { {-caret-} }",
                    "\\func lol => Foo { | {-caret-} }",
                    "\\func lol => 0 \\where {-caret-}" /* duplicate of where not allowed */,
                    "\\class Foo { | A : Nat {-caret-}}",
                    "\\class Foo => Lol { {-caret-} }",
                    "\\class Foo { {-caret-} }",
                    "\\class C { } \\func lol : C \\cowith | {-caret-}")

    fun `test braces after completing where`() =
            doSingleCompletion("\\func foo => 101 \\where{-caret-}",
                    "\\func foo => 101 \\where {{-caret-}}")

    fun `test no where completion after iterated where 2`() =
            checkKeywordCompletionVariants(ALIAS_KW_LIST + PH_LEVELS_KW_LIST, CompletionCondition.SAME_KEYWORDS,
                    "\\instance Foo {-caret-}" ) /* \\where not allowed in incomplete instance */

    fun `test no keyword completion before crlf`() =
            checkKeywordCompletionVariants(GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN, "\\func foo => 0 {-caret-}\n")

    fun `test extends completion after class name or arguments`() =
            checkKeywordCompletionVariants(EXTENDS_KW_LIST, CompletionCondition.CONTAINS,
                    "\\class Lol {-caret-}",
                    "\\class Lol {-caret-}{}",
                    "\\class Lol (n : Nat){-caret-}",
                    "\\class Lol (n : Nat){-caret-}{}",
                    "\\class Lol {n : Nat}{-caret-}",
                    "\\class Lol {n : Nat}{-caret-}{}",
                    "\\class Lol \\alias Lol1 {-caret-}")

    fun `test absence of extends`() =
            checkKeywordCompletionVariants(EXTENDS_KW_LIST + PH_LEVELS_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\class {-caret-}{}",
                    "\\class Lol (n : Nat) {-caret-} (m : Nat){}")

    fun `test data universe keywords after colon`() =
            checkKeywordCompletionVariants(DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_ELEMENTS, "\\data d1 (n : Nat): {-caret-}")

    fun `test data universe keywords after truncation level 1`() =
            checkCompletionVariants("\\data d1 (n : Nat): \\101{-caret-}", listOf("-Type"))

    fun `test data universe keywords after truncation level 2`() =
            checkCompletionVariants("\\data d1 (n : Nat): \\101-{-caret-}", listOf("Type"))

    fun `test data universe keywords after truncation level 3`() =
            checkSingleCompletion("\\data d1 (n : Nat): \\101-T{-caret-}", "\\101-Type")

    fun `test expression keywords`() =
            checkKeywordCompletionVariants(DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS,
                    "\\func f => {-caret-}",
                    "\\func f (a : Nat) => \\let a => 101 \\in {-caret-}",
                    "\\func f (a : Nat) => \\let a => {-caret-}\\in 101",
                    "\\func f (a : Nat) => \\Pi ({-caret-})",
                    "\\func lol (a : Nat) => \\Pi \\Set -> {-caret-}",
                    "\\func lol (a : Nat) \\elim a | zero => {-caret-}",
                    "\\func g => 101 (\\lam a => {-caret-})",
                    "\\class C (x : Nat)\n\\instance foo : C\n  | x => {-caret-}",
                    "\\class C (A : {-caret-})",
                    "\\class C (A : \\Pi (A : \\Type) -> {-caret-}",
                    "\\func lol : \\level ({-caret-})",
                    "\\func lol : \\level (Nat) ({-caret-})")

    fun `test expression keywords 2`() = checkKeywordCompletionVariants(DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST + THIS_KW_LIST, CompletionCondition.SAME_KEYWORDS,
        "\\class Y { | y : Nat } \\class Z \\extends Y {| y => {-caret-} }")

    fun `test expression keywords 3`() =
        checkKeywordCompletionVariants(DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST + LPH_LEVEL_KWS, CompletionCondition.SAME_KEYWORDS,
            "\\func f (a : Nat) => f({-caret-})")


    fun `test expression keywords for sigma`() = checkKeywordCompletionVariants(DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST + SIGMA_TELE_START_KWS, CompletionCondition.SAME_KEYWORDS,
            "\\func f (a : Nat) => \\Sigma ({-caret-})"
            )

    fun `test absence of keywords after level`() =
            checkKeywordCompletionVariants(emptyList(), CompletionCondition.SAME_KEYWORDS,
                    "\\func lol : \\level {-caret-}",
                    "\\func lol : \\level (Nat) {-caret-}")

    fun `test expression keywords in teles`() =
            checkKeywordCompletionVariants(DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS,
                    "\\func f (a : {-caret-}) => 0",
                    "\\data d (a : {-caret-})",
                    "\\class C (A : {-caret-})",
                    //"\\class C | cons (x : {-caret-})", //TODO: Better parser recovery is needed to fix this; otherwise can't distinguish this from `test completion in resulting type`
                    "\\func foo => \\let x : {-caret-} \\in ")

    fun `test only new & universes in application expression or after new expr`() =
            checkKeywordCompletionVariants(DATA_UNIVERSE_KW + NEW_KW_LIST + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS,
                    "\\func lol (a : Nat) => (\\new () {-caret-})")

    fun `test only new & universes in application expression or after new expr 2`() =
            checkKeywordCompletionVariants(DATA_UNIVERSE_KW + NEW_KW_LIST + FAKE_NTYPE_LIST + LPH_KW_LIST + LEVELS_KW_LIST + WHERE_KW_FULL + WITH_KW_FULL, CompletionCondition.SAME_KEYWORDS,
                    "\\func f (a : Nat) => f {-caret-}")

    fun `test no basic expression kws`() =
            checkKeywordCompletionVariants(BASIC_EXPRESSION_KW, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\func lol (a : Nat) (b : Nat) => lol 1 {-caret-}",
                    "\\data Test | cons (t : Test) {-caret-}",
                    "\\data Test | cons {-caret-}",
                    "\\data Test {-caret-}",
                    "\\data Test (a : Nat) {-caret-}")

    fun `test eval after new`() =
        checkKeywordCompletionVariants(listOf(ArendElementTypes.EVAL_KW.toString()), CompletionCondition.SAME_KEYWORDS,
            "\\func test => \\new {-caret-}")

    fun `test scase after new`() =
        checkKeywordCompletionVariants(listOf(ArendElementTypes.SCASE_KW.toString()), CompletionCondition.SAME_KEYWORDS,
            "\\func test => \\eval {-caret-}",
            "\\func test2 => \\peval {-caret-}")

    fun `test no keyword completion after with`() = checkKeywordCompletionVariants(emptyList(), CompletionCondition.SAME_ELEMENTS,
            "\\func lol (a : Nat) => \\case a \\with {-caret-}",
            "\\func lol (a : Nat) => \\case a \\with {-caret-} { | zero => 0 | suc a' => a'}",
            "\\func lol (a : Nat) \\elim a {-caret-} | zero => zero | suc _ => zero")

    fun `test no keyword completion after with 2`() = checkKeywordCompletionVariants(WHERE_KW_FULL, CompletionCondition.SAME_ELEMENTS,
            "\\func lol (a : Nat) => \\case a \\with { | zero => 0 | suc a' => a'}{-caret-}",
            "\\class Foo \\extends Bar {-caret-}" /* test no extends */)

    fun `test only universe keywords after Sigma or Pi`() =
            checkKeywordCompletionVariants(DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS,
                    "\\func lol (a : Nat) => \\Sigma {-caret-} -> \\Type",
                    "\\func lol (a : Nat) => \\Sigma \\Type {-caret-} -> \\Type",
                    "\\func lol (a : Nat) => \\Pi {-caret-} -> \\Type",
                    "\\func lol (a : Nat) => \\Pi \\Type {-caret-} -> \\Type")

    fun `test no keywords after let or lam`() =
            checkKeywordCompletionVariants(emptyList(), CompletionCondition.SAME_KEYWORDS,
                    "\\func lol (a : Nat) => (\\let {-caret-})",
                    "\\func lol (a : Nat) => (\\lam {-caret-})")

    fun `test no keywords in class extensions after pipe`() =
            checkKeywordCompletionVariants(emptyList(), CompletionCondition.SAME_KEYWORDS,
                    "\\func foo => Bar {| {-caret-} }")

    fun `test universe keywords as typed tele in data (after param)`() =
            checkKeywordCompletionVariants(DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.CONTAINS, "\\data \\fix 10 lol-data (a : \\Type) {-caret-}")

    fun `test levels completion after universe literal and inside level expressions`() =
            checkKeywordCompletionVariants(LPH_KW_LIST, CompletionCondition.CONTAINS,
                    "\\func lol (a : Nat) => (\\Set {-caret-})",
                    "\\func lol (a : Nat) => (\\Type {-caret-})",
                    "\\func lol (a : Nat) => (\\1-Type {-caret-})",
                    "\\func lol (a : Nat) => (\\oo-Type {-caret-})",
                    "\\func lol (a : Nat) => \\Set {-caret-}",
                    "\\func lol (a : Nat) => \\Type {-caret-}",
                    "\\func lol (a : Nat) => \\1-Type {-caret-}",
                    "\\func lol (a : Nat) => \\oo-Type {-caret-}",
                    "\\func lol (a : Nat) => \\Type (\\suc {-caret-})",
                    "\\func lol (a : Nat) => \\Type (\\max {-caret-})",
                    "\\func lol (a : Nat) => \\Type (\\max 1 {-caret-})",
                    "\\func lol (a : Nat) => (\\Type \\lp {-caret-})",
                    "\\func lol (a : Nat) => (\\Type (\\suc \\lp) {-caret-})",
                    "\\func lol (a : Nat) => \\Type \\lp (\\suc {-caret-})",
                    "\\func lol (a : Nat) => \\Type \\lp (\\max {-caret-})",
                    "\\func lol (a : Nat) => \\Type \\lp (\\max 1 {-caret-})")

    fun `test levels completion after universe literal 2`() =
            checkKeywordCompletionVariants(emptyList(), CompletionCondition.SAME_KEYWORDS,
                    "\\func lol (a : Nat) => (\\Prop {-caret-})",
                    "\\func lol (a : Nat) => (\\Set \\lp {-caret-})",
                    "\\func lol (a : Nat) => (\\Set (\\suc \\lp {-caret-}))",
                    "\\func lol (a : Nat) => (\\Set (\\max 1 \\lp {-caret-}))",
                    "\\func lol (a : Nat) => (\\Type \\lp (\\max 1 \\lh {-caret-}))",
                    "\\func lol (a : Nat) => (\\Type \\lp \\lh {-caret-})")

    fun `test levels completion after universe literal 3`() =
            checkKeywordCompletionVariants(LPH_LEVEL_KWS, CompletionCondition.SAME_KEYWORDS,
                    "\\func lol (a : Nat) => (\\Type ({-caret-}))",
                    "\\func lol (a : Nat) => \\Type ({-caret-})",
                    "\\func lol (a : Nat) => \\Type (({-caret-}))",
                    "\\func lol (a : Nat) => (\\Type \\lp ({-caret-}))",
                    "\\func lol (a : Nat) => (\\Type (\\suc \\lp) ({-caret-}))")

    /*fun `test no keywords after new even inside braces`() =
            checkKeywordCompletionVariants(emptyList(), CompletionCondition.SAME_KEYWORDS,
                    "\\func lol (a : Nat) => (\\new ({-caret-}))",
                    "\\func lol (a : Nat) => (\\new (({-caret-})))") */ //Deemed too difficult to implement at the moment

    fun `test absence of expression keywords`() = // only levels
            checkKeywordCompletionVariants(DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\func lol (a : Nat) => \\Sigma (\\Set {-caret-})",
                    "\\class C (a {-caret-})")

    fun `test universe keywords as typed tele`() =
            checkKeywordCompletionVariants(DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.CONTAINS,
                    "\\data lol {-caret-}",
                    "\\data DDD | ccc {-caret-}",
                    "\\data DDDD (x : Nat) \\with\n   | zero => cccc {-caret-}",
                    "\\class X {\n   | xxxx {-caret-} : xxxx\n}")

    fun `test classifying keyword in class parameters`() =
            checkKeywordCompletionVariants(CLASSIFYING_KW_LIST + COERCE_KW_LIST + ACCESS_MODIFIERS, CompletionCondition.SAME_KEYWORDS,
                    "\\class C ({-caret-} a : Nat)",
                    "\\class C (x y : Nat) ({-caret-})")

    fun `test absence of classifying keyword`() =
            checkKeywordCompletionVariants(CLASSIFYING_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\func Lol ({-caret-})")

    fun `test classifying keyword`() =
            checkKeywordCompletionVariants(CLASSIFYING_KW_LIST, CompletionCondition.CONTAINS,
            "\\class C (a : Nat)\n | {-caret-} ")

    fun `test coerce keyword`() = checkKeywordCompletionVariants(COERCE_KW_LIST, CompletionCondition.CONTAINS,
            "\\record R ({-caret-} foo : Nat)\n  | bar : Int\n",
            "\\data D\n   | {-caret-} con Nat")

    fun `test coerce keyword 2`() = checkKeywordCompletionVariants(COERCE_KW_LIST + ACCESS_MODIFIERS, CompletionCondition.SAME_ELEMENTS,
            "\\class C1 (\\classifying x : Nat) ({-caret-})")

    fun `test noclassifying keyword`() =
        checkKeywordCompletionVariants(NO_CLASSIFYING_KW_LIST, CompletionCondition.CONTAINS,
            "\\class D \\class C {-caret-} \\extends D",
            "\\class C {-caret-}")

    fun `test noclassifying keyword before parameter`() =
        checkKeywordCompletionVariants(NO_CLASSIFYING_KW_LIST + ALIAS_KW_LIST + PH_LEVELS_KW_LIST, CompletionCondition.SAME_KEYWORDS,
            "\\class C {-caret-} (a : Nat)")

    fun `test level keyword completion`() =
            checkKeywordCompletionVariants(LEVEL_KW_LIST, CompletionCondition.CONTAINS,
                    "\\func foo => \\case 0 \\return {-caret-} \\with {}",
                    "\\func foo => \\case 0 \\return {-caret-} Nat \\with {}",
                    "\\lemma foo (A : \\Type) (a : A) (p : \\Pi (x y : A) -> x = y) : A {-caret-}")

    fun `test in keyword completion`() = checkKeywordCompletionVariants(IN_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol (a : Nat) => \\let a => 1 + {-caret-}",
            "\\func lol (a : Nat) => \\let a => 1 + 2 {-caret-}",
            "\\func lol (a : Nat) => \\let a => (1 + 2) {-caret-}",
            "\\func lol (a : Nat) => \\let b => \\let a => (1 + 2) {-caret-}",
            "\\func lol (a : Nat) => \\let b => \\let a => (1 + 2) \\in a {-caret-}")

    fun `test absence of in keyword completion`() = checkKeywordCompletionVariants(IN_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
            "\\func lol (a : Nat) => \\let a => (1 + 2) \\in {-caret-}",
            "\\func lol (a : Nat) => \\let b => \\let a => (1 + 2) \\in {-caret-}",
            "\\func lol (a : Nat) => \\let a => (1 + {-caret-})")

    fun `test with keyword completion`() = checkKeywordCompletionVariants(WITH_KW_FULL, CompletionCondition.CONTAINS,
            "\\func lol (a : Nat) => \\case a {-caret-}",
            "\\func lol (a : Nat) => \\case a + 2 {-caret-}",
            "\\func lol (a : Nat) => \\case (a + 2) {-caret-}",
            "\\func lol (a : Nat) => \\case \\case a {-caret-}",
            "\\func lol (a : Nat) => (\\case a + 2 {-caret-})",
            "\\func lol (a : Nat) => \\case \\case a \\with {} {-caret-}",
            "\\func lol (a : Nat) => \\case a \\as a' {-caret-}",
            "\\func lol (a : Nat) => \\case a \\as a' : Nat {-caret-}"
            //, "\\func lol (a : Nat) => \\case a {-caret-} {}" //Fixme
    )

    fun `test as keyword completion`() = checkKeywordCompletionVariants(AS_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol (a : Nat) => \\case a {-caret-}",
            "\\func lol (a : Nat) => \\case a {-caret-}, b \\with {}",
            "\\func lol (a : Nat) => \\case a \\as a1, b {-caret-}\\return Nat \\with {}",
            "\\func lol (a : Nat) => \\case a {-caret-}: Nat")

    fun `test absence of as completion`() = checkKeywordCompletionVariants(AS_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
            "\\func lol (a : Nat) => \\case a \\as a' : Nat {-caret-}",
            "\\func lol (a : Nat) => \\case a \\as a' {-caret-}",
            "\\import A({-caret-})",
            "\\import A(a \\as a', {-caret-})")

    fun `test return keyword completion`() = checkKeywordCompletionVariants(RETURN_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol (a : Nat) => \\case a {-caret-}",
            "\\func lol (a : Nat) => \\case a \\as a1 {-caret-}\\with {}",
            "\\func lol (a : Nat) => \\case a \\as a1, b {-caret-}")

    fun `test absence of return completion`() = checkKeywordCompletionVariants(RETURN_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
            "\\func lol (a : Nat) => \\case a {-caret-}, b \\with {}",
            "\\func lol (a : Nat) => \\case a {-caret-} \\return Nat {}",
            "\\func lol (a : Nat) => \\case 0 \\as x : {-caret-}")

    fun `test absence of with completion`() = checkKeywordCompletionVariants(WITH_KW_LIST + WITH_KW_FULL, CompletionCondition.DOES_NOT_CONTAIN,
            "\\func lol (a : Nat) => \\case \\case a \\with {-caret-}",
            "\\func lol (a : Nat) => \\case 0 \\as x : {-caret-}")

    fun `test elim completion 1`() = checkKeywordCompletionVariants(ELIM_WITH_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol (a : Nat) {-caret-}",
            "\\func lol (a : Nat) : Nat {-caret-}",
            "\\func lol (a : Nat) : \\Type \\lp \\lh {-caret-}",
            "\\func f (n : Nat) : Nat {-caret-}\n -- comment\n",
            "\\data lol (a : Nat) {-caret-}",
            "\\data lol (a : Nat) : \\Type \\lp \\lh {-caret-}",
            "\\data lol | south (a : Nat) {-caret-}",
            "\\data lol | south I {-caret-}")

    fun `test elim completion 2`() = checkKeywordCompletionVariants(WITH_KW_FULL + ELIM_KW_LIST, CompletionCondition.CONTAINS,
            "\\record R (x : Nat) \\record S (r : Nat -> R) \\func foo : S \\cowith | r n : R {-caret-}",
            "\\record R (x : Nat) \\record S (r : Nat -> R) \\func foo : S \\cowith | r n {-caret-}")

    fun `test cowith completion`() = checkKeywordCompletionVariants(COWITH_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol (a : Nat) : Nat {-caret-}",
            "\\func lol (a : Nat) : \\Type \\lp \\lh {-caret-}",
            "\\func f (n : Nat) : Nat {-caret-}\n -- comment\n",
            "\\func lol : Nat {-caret-}",
            "\\func t : Nat {-caret-} \\where {}",
            "\\record R (x : Nat) \\record S (r : Nat -> R) \\func foo : S \\cowith | r n : R {-caret-}")

    fun `test absence of cowith completion`() = checkKeywordCompletionVariants(COWITH_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
            "\\data or (A B : \\Type) : \\Prop\n  | l A {-caret-}\n  | r B")

    fun `test elim completion 3`() = checkKeywordCompletionVariants(ELIM_WITH_KW_LIST + COWITH_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
            "\\func {-caret-}",
            "\\func lol (a : Nat) {-caret-} (b : Nat) : \\Type",
            "\\func lol (a : Nat) : \\Set ({-caret-})",
            "\\func lol (a : Nat) {-caret-} =>", // No elim if already there is a fat arrow
            "\\data lol {-caret-}",
            "\\data lol (A : \\Type) | south {-caret-}",
            "\\data lol (a : Nat) {-caret-} \\elim a",
            "\\func foo (n : Nat)\n  | 0 => 0\n  | suc n => 101 {-caret-}",
            "\\class M | a : Nat\n\\func foo (n : Nat) : M \\cowith | a => 0{-caret-}",
            "\\func foo (n : Nat) {-caret-}\\cowith")

    fun `test elim completion 4`() = checkKeywordCompletionVariants(ELIM_KW_LIST, CompletionCondition.CONTAINS,
            "\\func foo (a : Nat) => \\case {-caret-}",
            "\\func foo (a : Nat) => \\case a, {-caret-}",
            "\\func foo (a : Nat) => \\case a, {-caret-} b \\with")

    fun `test no elim and no fixity completion`() = checkKeywordCompletionVariants(WHERE_KW_FULL, CompletionCondition.SAME_KEYWORDS,
            "\\class C { } \\func lol : C \\cowith {-caret-}")

    fun `test no elim and no fixity completion 2`() = checkKeywordCompletionVariants(WHERE_KW_FULL + ALIAS_KW_LIST + PH_LEVELS_KW_LIST, CompletionCondition.SAME_KEYWORDS,
            "\\func lol {-caret-}") /* No elim if there are no arguments; No fixity */

    fun `test hlevels`() = checkKeywordCompletionVariants(HLEVELS_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol \\plevels x {-caret-}",
            "\\func lol \\plevels {-caret-}")

    fun `test no hlevels`() = checkKeywordCompletionVariants(WHERE_KW_FULL, CompletionCondition.SAME_KEYWORDS,
            "\\func lol \\hlevels {-caret-}",
            "\\func lol \\hlevels x {-caret-}")

    fun `test leveled application expression`() = checkKeywordCompletionVariants(LPH_KW_LIST + LEVELS_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol (a : Nat) => lol {-caret-} a",
            "\\func lol (a : Nat) => lol {-caret-} a 1 2")

    fun `test leveled application expression 2`() = checkKeywordCompletionVariants(LPH_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol => lol \\levels {-caret-}")

    fun `test leveled application expression 3`() = checkKeywordCompletionVariants(LPH_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol => lol \\levels \\lp {-caret-}",
            "\\func lol => lol \\lp {-caret-}",
            "\\func lol (a : Nat) => lol \\lp {-caret-} a 1 2") //,"\\func lol => lol \\levels (\\suc \\lp) {-caret-}" //Fixme: Better parser recovery needed to fix this

    fun `test leveled application expression 4`() = checkKeywordCompletionVariants(LPH_LEVEL_KWS, CompletionCondition.CONTAINS,
            "\\func lol => lol \\levels ({-caret-})",
            "\\func lol => lol \\levels \\lp ({-caret-})",
            "\\func lol => lol \\lp ({-caret-})")

    fun `test no leveled application`() = checkKeywordCompletionVariants(LPH_KW_LIST + LEVELS_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
            "\\func lol => lol \\levels \\lp \\lh {-caret-}",
            "\\func lol => lol \\lp \\lh {-caret-}",
            "\\func lol (a : Nat) => lol a {-caret-}",
            "\\func lol => 1 {-caret-}",
            "\\func lol => 1 ({-caret-})")

    fun `test no leveled application 2`() = checkKeywordCompletionVariants(LEVELS_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
            "\\func lol => lol \\levels \\lp {-caret-}",
            "\\func lol => lol \\lp {-caret-}",
            "\\func lol (a : Nat) => lol \\lp {-caret-} a 1 2",
            "\\func lol (a : Nat) => lol ({-caret-})")

    fun `test absence of completion in comments`() = checkKeywordCompletionVariants(emptyList(), CompletionCondition.SAME_KEYWORDS,
            "\\func f (x : Bool) : Bool\n | true => {?}  -- {-caret-} \n | false => {?}\n",
            "\\data -- {-caret-} \n  D")

    fun `test absence of completion after dot`() = checkKeywordCompletionVariants(emptyList(), CompletionCondition.SAME_KEYWORDS,
            "\\func f => 0\n \\func g (x : Nat) => f x.{-caret-}")

    fun `test strict`() = checkKeywordCompletionVariants(PARAM_ATTR_LIST, CompletionCondition.CONTAINS,
            "\\func foo ({-caret-}a b : Nat)",
            "\\func foo ({-caret-})",
            "\\data D (A : \\Type) | con ({-caret-} a b : A)",
            "\\data D (A : \\Type) | con ({-caret-})")

    // Tests related to #186

    private val kwTest186Sample1 = "\\data d\n  | c1\n\n\\{-caret-}func x"
    private val kwTest186Sample2 = "\\import Prelude\n\n\\{-caret-}func x"
    private val kwTest186Sample3 = "\\func x :\n\n\\{-caret-}lemma l"

    fun test_186_1() = checkCompletionVariants(kwTest186Sample1, FAKE_NTYPE_LIST + DATA_UNIVERSE_KW, CompletionCondition.DOES_NOT_CONTAIN)

    fun test_186_2() = checkCompletionVariants(kwTest186Sample2, HU_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun test_186_3() = checkCompletionVariants(kwTest186Sample3, DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST + DATA_UNIVERSE_KW, CompletionCondition.DOES_NOT_CONTAIN)

    fun test_186_4() = checkCompletionVariants(kwTest186Sample1, STATEMENT_WT_KWS, CompletionCondition.CONTAINS)

    fun test_186_5() = checkCompletionVariants(kwTest186Sample2, STATEMENT_WT_KWS, CompletionCondition.CONTAINS)

    fun test_186_6() = checkCompletionVariants(kwTest186Sample3, STATEMENT_WT_KWS, CompletionCondition.CONTAINS)

    fun test_alias() = checkKeywordCompletionVariants(ALIAS_KW_LIST, CompletionCondition.CONTAINS,
            "\\module M {-caret-}",
            "\\data Unit | unit {-caret-}",
            "\\class C\n  | E {-caret-}",
            "\\class C (E : \\Type)\n  | X {-caret-}: E -> E -> \\Set")

    fun test_alias_plevels() = checkKeywordCompletionVariants(ALIAS_KW_LIST + PH_LEVELS_KW_LIST, CompletionCondition.CONTAINS,
            "\\data Unit {-caret-}",
            "\\class C {-caret-}")

    fun test_meta_with() = checkKeywordCompletionVariants(WITH_KW_FULL, CompletionCondition.CONTAINS,
            "\\func f (a b c : Nat) => 101\n\\func bar (a b c : Nat) => f a b c {-caret-}",
            "\\func f (a b c : Nat) => 101\n\\func bar (a b c : Nat) => f {-caret-}",
            "\\func f (a b c : Nat) => 101\n\\func bar (a b c : Nat) => f \\levels \\lp \\lh {-caret-}",
            "\\func f (a b c : Nat) => 101\n\\func bar (a b c : Nat) => \\new f a b c {-caret-}",
            "\\func f (a b c : Nat) => 101\n\\func bar (a b c : Nat) => \\eval f a b c {-caret-}",
            "\\func f (a b c : Nat) => 101\n\\func bar (a b c : Nat) => \\eval f a b c {} {-caret-}")

    fun test_as_pattern() = checkKeywordCompletionVariants(AS_KW_LIST, CompletionCondition.CONTAINS,
            "\\func f (x : Nat) : Nat\n  | 0 => 0\n  | suc x {-caret-} => x'")

    fun test_this() = checkKeywordCompletionVariants(THIS_KW_LIST, CompletionCondition.CONTAINS,
        "\\class Foo { \\func foo => Path { {-caret-} } }",
        "\\class Foo (X : \\Set) { \\func lol : Foo \\cowith { | X => {-caret-} }}",
        "\\class Foo (X : \\Set) { \\instance Lol : Foo | X => {-caret-} }"
    )


}