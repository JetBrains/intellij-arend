package org.vclang.codeInsight.completion

import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.AS_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.COERCE_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.DATA_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.DATA_OR_EXPRESSION_KW
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.DATA_UNIVERSE_KW
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.ELIM_WITH_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.EXTENDS_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.FAKE_NTYPE_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.FIXITY_KWS
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.LOCAL_STATEMENT_KWS
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.GLOBAL_STATEMENT_KWS
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.HIDING_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.HU_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.IMPORT_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.IN_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.LEVELS_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.LPH_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.LPH_LEVEL_KWS
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.NEW_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.PROP_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.WHERE_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.WITH_KW_LIST

class VcKeywordCompletionTest : VcCompletionTestBase() {
    fun `test fixity completion`() =
            checkKeywordCompletionVariants(FIXITY_KWS, CompletionCondition.SAME_ELEMENTS,
                    "\\func {-caret-}test => 0",
                    "\\func {-caret-}",
                    "\\class {-caret-}testClass {}",
                    "\\class {-caret-}",
                    "\\data {-caret-}MyNat | myzero",
                    "\\data {-caret-}",
                    "\\import B (lol \\as {-caret-}+)",
                    "\\data MyNat | {-caret-}myzero",
                    "\\data Fin (n : Nat) \\with | suc n => {-caret-}fzero | suc n => fsuc (Fin n)",
                    "\\class Monoid (El : \\Set) { | {-caret-}* : El -> El -> El}",
                    "\\class AddMonoid => Monoid { | * => {-caret-}+}")

    fun `test no fixity completion`() =
            checkKeywordCompletionVariants(FIXITY_KWS, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\func foo (n : Nat) \\elim n | {-caret-}zero =>",
                    "\\func foo (n : Nat) => {-caret-}n ")

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
                    "\\import B \\func foo => 0\n {-caret-}\\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ",
                    "\\import B \\func foo => 0 \\data bar | foobar\n {-caret-}\\func f => 0 \\where { \\func g => 1 } ",
                    "\\import B \\hiding (a)\n{-caret-}\\func foo => 0 \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ",
                    "\\func f (xs : Nat) : Nat \\elim xs\n | suc x => \\case x \\with {| zero => 0 | suc _ => 1}\n {-caret-}")

    fun `test root keywords completion 2`() =
            checkKeywordCompletionVariants(LOCAL_STATEMENT_KWS, CompletionCondition.CONTAINS,
                    "\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\n{-caret-}\\func g => 1 } ",
                    "\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n {-caret-}}",
                    "\\func foo => 0 \\where {\n{-caret-} }",
                    "\\class Foo { | A : Nat\n {-caret-} }",
                    "\\class Foo { | A : Nat\n \\func lol => 0\n {-caret-} \n\\func lol2 => 0 }")

    fun `test no root keywords completion`() =
            checkKeywordCompletionVariants(LOCAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\class Foo {| A : Nat\n {-caret-} \n | B : Nat }")

    fun `test root keywords completion 3`() =
            checkKeywordCompletionVariants(GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\class A {| foo : Nat}\n\\func f => \\new A {| foo => 0 |\n{-caret-}=> 1\n}\n")

    fun `test no import in completion 1`() =
            checkKeywordCompletionVariants(IMPORT_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\n{-caret-}\\func g => 1 } ",
                    "\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n {-caret-}}")

    fun `test no coerce in global context`() =
            checkKeywordCompletionVariants(COERCE_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
                    "{-caret-}")

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

    fun `test no keyword completion after instance, open or wrong state`() =
            checkKeywordCompletionVariants(GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\instance {-caret-}",
                    "\\open {-caret-}",
                    "\\func f (a : Nat) : Nat => {-caret-}",
                    "\\func f (a : Nat) : {-caret-}",
                    "\\func f ({-caret-}")

    fun `test no where completion in empty context, after import, open`() =
            checkKeywordCompletionVariants(WHERE_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
                    "{-caret-}",
                    "\\import A\n{-caret-}",
                    "\\open Nat\n{-caret-}")

    fun `test where completion after func, data, class, iterated where`() =
            checkKeywordCompletionVariants(WHERE_KW_LIST, CompletionCondition.CONTAINS,
                    "\\func lol => 0\n{-caret-}",
                    "\\data Lol | lol1 | lol2\n{-caret-}",
                    "\\class Lol {}\n{-caret-}",
                    "\\func foo => 0 \\where \\func bar => 0\n{-caret-}",
                    "\\class foo {-caret-}",
                    "\\module bar {-caret-}")

    fun `test no where completion after iterated where`() =
            checkKeywordCompletionVariants(WHERE_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
                    "\\func foo => 0 \\where {\\func bar => 0}\n{-caret-}",
                    "\\func foo => 0 \\where \n {-caret-}",
                    "\\instance Foo {-caret-}" /* \\where not allowed in incomplete instance */,
                    "\\func foo => \\case {-caret-}" /* where not allowed after case keyword */,
                    "\\func lol => Foo { {-caret-} }",
                    "\\func lol => Foo { | {-caret-} }",
                    "\\func lol => 0 \\where {-caret-}" /* duplicate of where not allowed */,
                    "\\class Foo { | A : Nat {-caret-}}")

    fun `test no keyword completion before crlf`() =
            checkKeywordCompletionVariants(GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN, "\\func foo => 0 {-caret-}\n")

    fun `test extends completion after class name or arguments`() =
            checkKeywordCompletionVariants(EXTENDS_KW_LIST, CompletionCondition.CONTAINS,
                    "\\class Lol {-caret-}",
                    "\\class Lol {-caret-}{}",
                    "\\class Lol (n : Nat){-caret-}",
                    "\\class Lol (n : Nat){-caret-}{}")

    fun `test no extends after class without name or inside class arguments block`() =
            checkKeywordCompletionVariants(EXTENDS_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
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
                    "\\func f (a : Nat) => \\Sigma ({-caret-})",
                    "\\func lol (a : Nat) => \\Pi \\Set -> {-caret-}",
                    "\\func lol (a : Nat) \\elim a | zero => {-caret-}")

    fun `test expression keywords 2`() =
            checkKeywordCompletionVariants(DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST + LPH_LEVEL_KWS, CompletionCondition.SAME_KEYWORDS,
                    "\\func f (a : Nat) => f({-caret-})")

    fun `test expression keywords in teles`() =
            checkKeywordCompletionVariants(DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS,
                    "\\func f (a : {-caret-}) => 0")

    fun `test only new & universes in application expression or after new expr`() =
            checkKeywordCompletionVariants(DATA_UNIVERSE_KW + NEW_KW_LIST + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS,
                    "\\func lol (a : Nat) => (\\new () {-caret-})")

    fun `test only new & universes in application expression or after new expr 2`() =
            checkKeywordCompletionVariants(DATA_UNIVERSE_KW + NEW_KW_LIST + FAKE_NTYPE_LIST + LPH_KW_LIST + LEVELS_KW_LIST + WHERE_KW_LIST, CompletionCondition.SAME_KEYWORDS,
                    "\\func f (a : Nat) => f {-caret-}")

    fun `test no keyword completion after with`() = checkKeywordCompletionVariants(emptyList(), CompletionCondition.SAME_ELEMENTS,
            "\\func lol (a : Nat) => \\case a \\with {-caret-}",
            "\\func lol (a : Nat) => \\case a \\with {-caret-} { | zero => 0 | suc a' => a'}",
            "\\func lol (a : Nat) \\elim a {-caret-} | zero => zero | suc _ => zero")

    fun `test no keyword completion after with 2`() = checkKeywordCompletionVariants(WHERE_KW_LIST, CompletionCondition.SAME_ELEMENTS,
            "\\func lol (a : Nat) => \\case a \\with { | zero => 0 | suc a' => a'}{-caret-}",
            "\\class Foo \\extends Bar {-caret-}" /* test no extends */)

    fun `test only universe keywords after Sigma or Pi`() =
            checkKeywordCompletionVariants(DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS,
                    "\\func lol (a : Nat) => \\Sigma {-caret-} -> \\Type",
                    "\\func lol (a : Nat) => \\Sigma \\Type {-caret-} -> \\Type",
                    "\\func lol (a : Nat) => \\Pi {-caret-} -> \\Type",
                    "\\func lol (a : Nat) => \\Pi \\Type {-caret-} -> \\Type")

    fun `test no keywords after new, let or lam`() =
            checkKeywordCompletionVariants(emptyList(), CompletionCondition.SAME_KEYWORDS,
                    "\\func lol (a : Nat) => (\\new {-caret-})",
                    "\\func lol (a : Nat) => (\\let {-caret-})",
                    "\\func lol (a : Nat) => (\\lam {-caret-})")

    fun `test no keywords in class extenstions after pipe`() =
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
                    "\\func lol (a : Nat) => (\\Prop {-caret-})" ,
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

    fun `test no expression keywords after universe literal 10`() = // only levels
            checkKeywordCompletionVariants(DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.DOES_NOT_CONTAIN, "\\func lol (a : Nat) => \\Sigma (\\Set {-caret-})")

    fun `test universe keywords as typed tele`() =
            checkKeywordCompletionVariants(DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.CONTAINS,
                    "\\data lol {-caret-}",
                    "\\data DDD | ccc {-caret-}",
                    "\\data DDDD (x : Nat) \\with\n   | zero => cccc {-caret-}",
                    "\\class X {\n   | xxxx {-caret-} : xxxx\n}")

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

    fun `test with keyword completion`() = checkKeywordCompletionVariants(WITH_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol (a : Nat) => \\case a {-caret-}",
            "\\func lol (a : Nat) => \\case a + 2 {-caret-}",
            "\\func lol (a : Nat) => \\case (a + 2) {-caret-}",
            "\\func lol (a : Nat) => \\case \\case a {-caret-}",
            "\\func lol (a : Nat) => (\\case a + 2 {-caret-})")

    fun `test with keyword completion 2`() = checkKeywordCompletionVariants(WITH_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol (a : Nat) => \\case \\case a \\with {} {-caret-}")

    fun `test absence of with keyword completion`() = checkKeywordCompletionVariants(WITH_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
            "\\func lol (a : Nat) => \\case a {-caret-} \\with {}",
            "\\func lol (a : Nat) => \\case \\case a \\with {-caret-}",
            "\\func lol (a : Nat) => \\case (a {-caret-}) \\with {}")

    fun `test elim completion 1`() = checkKeywordCompletionVariants(ELIM_WITH_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol (a : Nat) {-caret-}",
            "\\func lol (a : Nat) : Nat {-caret-}",
            "\\func lol (a : Nat) : \\Type \\lp \\lh {-caret-}",
            "\\data lol (a : Nat) {-caret-}",
            "\\data lol (a : Nat) : \\Type \\lp \\lh {-caret-}",
            "\\data lol | south (a : Nat) {-caret-}",
            "\\data lol | south I {-caret-}")

    fun `test elim completion 2`() = checkKeywordCompletionVariants(ELIM_WITH_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN,
            "\\func {-caret-}",
            "\\func lol (a : Nat): {-caret-}",
            "\\func lol (a : Nat) {-caret-} (b : Nat) : \\Type",
            "\\func lol (a : Nat) : \\Set ({-caret-})",
            "\\func lol (a : Nat) {-caret-} =>", // No elim if already there is a fat arrow
            "\\data lol {-caret-}",
            "\\data lol (A : \\Type) | south {-caret-}",
            "\\data lol (a : Nat) {-caret-} \\elim a"
    )

    fun `test no elim and no fixity completion`() = checkKeywordCompletionVariants(WHERE_KW_LIST, CompletionCondition.SAME_KEYWORDS,
     "\\func lol {-caret-}" /* No elim if there are no arguments; No fixity */)

    fun `test leveled application expression`() = checkKeywordCompletionVariants(LPH_KW_LIST + LEVELS_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol (a : Nat) => lol {-caret-} a",
            "\\func lol (a : Nat) => lol {-caret-} a 1 2"
            )

    fun `test leveled application expression 2`() = checkKeywordCompletionVariants(LPH_KW_LIST + PROP_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol => lol \\levels {-caret-}")

    fun `test leveled application expression 3`() = checkKeywordCompletionVariants(LPH_KW_LIST, CompletionCondition.CONTAINS,
            "\\func lol => lol \\levels \\lp {-caret-}",
            "\\func lol => lol \\lp {-caret-}",
            "\\func lol (a : Nat) => lol \\lp {-caret-} a 1 2"
            //,"\\func lol => lol \\levels (\\suc \\lp) {-caret-}" //Fixme: Better parser recovery needed to fix this
            )

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


}