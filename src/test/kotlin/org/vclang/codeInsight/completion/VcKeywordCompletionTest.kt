package org.vclang.codeInsight.completion

import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.AS_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.DATA_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.DATA_OR_EXPRESSION_KW
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.DATA_UNIVERSE_KW
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.EXTENDS_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.FAKE_NTYPE_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.FIXITY_KWS
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.STATEMENT_KWS
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.GLOBAL_STATEMENT_KWS
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.HIDING_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.HU_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.IMPORT_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.NEW_KW_LIST
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.WHERE_KW_LIST

class VcKeywordCompletionTest : VcCompletionTestBase() {
    fun `test fixity completion after func 1`() =
            checkKeywordCompletionVariants("\\func {-caret-}test => 0", FIXITY_KWS)

    fun `test fixity completion after func 2`() =
            checkKeywordCompletionVariants("\\func {-caret-}", FIXITY_KWS)

    fun `test fixity completion after class 1`() =
            checkKeywordCompletionVariants("\\class {-caret-}testClass {}", FIXITY_KWS)

    fun `test fixity completion after class 2`() =
            checkKeywordCompletionVariants("\\class {-caret-}", FIXITY_KWS)

    fun `test fixity completion after data 1`() =
            checkKeywordCompletionVariants("\\data {-caret-}MyNat | myzero", FIXITY_KWS)

    fun `test fixity completion after data 2`() =
            checkKeywordCompletionVariants("\\data {-caret-}", FIXITY_KWS)

    fun `test fixity completion after as 1`() =
            checkKeywordCompletionVariants("\\import B (lol \\as {-caret-}+)", FIXITY_KWS)

    fun `test fixity completion after simple datatype constructor 1`() =
            checkKeywordCompletionVariants("\\data MyNat | {-caret-}myzero", FIXITY_KWS)

    fun `test fixity completion after datatype constructor with a pattern 1`() =
            checkKeywordCompletionVariants("\\data Fin (n : Nat) \\with | suc n => {-caret-}fzero | suc n => fsuc (Fin n)", FIXITY_KWS)

    fun `test fixity completion after class field 1`() =
            checkKeywordCompletionVariants("\\class Monoid (El : \\Set) { | {-caret-}* : El -> El -> El}", FIXITY_KWS)

    fun `test fixity completion after class field synonym 1`() =
            checkKeywordCompletionVariants("\\class AddMonoid => Monoid { | * => {-caret-}+}", FIXITY_KWS)

    fun `test no fixity completion in pattern matching`() =
            checkKeywordCompletionVariants("\\func foo (n : Nat) \\elim n | {-caret-}zero =>", FIXITY_KWS, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no fixity completion after func fat arrow`() =
            checkKeywordCompletionVariants("\\func foo (n : Nat) => {-caret-}n ", FIXITY_KWS, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no fixity completion after func if function name starts with f`() =
            checkCompletionVariants("\\func f{-caret-}", FIXITY_KWS, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test as completion in namespace command`() =
            checkCompletionVariants("\\import B (lol {-caret-})", AS_KW_LIST)

    fun `test as completion in namespace command 2`() =
            checkSingleCompletion("\\import B (lol \\{-caret-})", AS_KW_LIST[0])

    fun `test nsCmd completion in namespace command 1`() =
            checkCompletionVariants("\\import B (lol) {-caret-}", HIDING_KW_LIST, CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 2`() =
            checkSingleCompletion("\\import B (lol) \\{-caret-}", HIDING_KW_LIST[0])

    fun `test nsCmd completion in namespace command 3`() =
            checkKeywordCompletionVariants("\\import B (lol)\n{-caret-}", HIDING_KW_LIST, CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 4`() =
            checkKeywordCompletionVariants("\\import B {-caret-}", HU_KW_LIST, CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 5`() =
            checkKeywordCompletionVariants("\\import B {-caret-}(lol)", HU_KW_LIST)

    fun `test nsCmd completion in namespace command 8`() =
            checkNoCompletion("\\import B {-caret-}\\using (lol)")

    fun `test nsCmd completion in namespace command 9`() =
            checkNoCompletion("\\import B \\using (lol) {-caret-} \\hiding (lol)")

    fun `test nsCmd completion in namespace command 10`() =
            checkNoCompletion("\\import B \\hiding {-caret-} (lol)")

    fun `test nsCmd completion in namespace command 11`() =
            checkNoCompletion("\\import B \\hiding {-caret-}")

    fun `test nsCmd completion in namespace command 12`() =
            checkCompletionVariants("\\import {-caret-}", GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test root keywords completion 1`() =
            checkKeywordCompletionVariants("\\import B\n {-caret-}\\func foo => 0 \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ", GLOBAL_STATEMENT_KWS, CompletionCondition.CONTAINS)

    fun `test root keywords completion 2`() =
            checkKeywordCompletionVariants("\\import B \\func foo => 0\n {-caret-}\\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ", GLOBAL_STATEMENT_KWS, CompletionCondition.CONTAINS)

    fun `test root keywords completion 3`() =
            checkKeywordCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar\n {-caret-}\\func f => 0 \\where { \\func g => 1 } ", GLOBAL_STATEMENT_KWS, CompletionCondition.CONTAINS)

    fun `test root keywords completion 4`() =
            checkKeywordCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\n{-caret-}\\func g => 1 } ", STATEMENT_KWS, CompletionCondition.CONTAINS)

    fun `test root keywords completion 5`() =
            checkKeywordCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n {-caret-}}", STATEMENT_KWS, CompletionCondition.CONTAINS)

    fun `test root keywords completion 6`() =
            checkKeywordCompletionVariants("\\import B \\hiding (a)\n{-caret-}\\func foo => 0 \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ", GLOBAL_STATEMENT_KWS, CompletionCondition.CONTAINS)

    fun `test root keywords completion 7`() =
            checkKeywordCompletionVariants("\\func f (xs : Nat) : Nat \\elim xs\n | suc x => \\case x \\with {| zero => 0 | suc _ => 1}\n {-caret-}", GLOBAL_STATEMENT_KWS, CompletionCondition.CONTAINS)

    fun `test root keywords completion 8`() =
            checkKeywordCompletionVariants("\\class A {| foo : Nat}\n\\func f => \\new A {| foo => 0 |\n{-caret-}=> 1\n}\n", GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no import in completion 1`() =
            checkKeywordCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\n{-caret-}\\func g => 1 } ", IMPORT_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no import in completion 2`() =
            checkKeywordCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n {-caret-}}", IMPORT_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test root completion in empty context`() =
            checkKeywordCompletionVariants("{-caret-}", GLOBAL_STATEMENT_KWS, CompletionCondition.CONTAINS)

    fun `test completion after truncated`() =
            checkCompletionVariants("\\truncated {-caret-}", STATEMENT_KWS.minus("\\data"), CompletionCondition.DOES_NOT_CONTAIN)

    fun `test completion after truncated 2`() =
            checkSingleCompletion("\\tru{-caret-}", "\\data")

    fun `test completion after truncated 3`() =
            checkCompletionVariants("\\truncated {-caret-}", DATA_KW_LIST, CompletionCondition.CONTAINS)

    fun `test completion after truncated 4`() =
            checkSingleCompletion("\\truncated \\{-caret-}", DATA_KW_LIST[0])

    fun `test completion after truncated 5`() =
            checkSingleCompletion("\\truncated \\da{-caret-}", DATA_KW_LIST[0])

    fun `test completion after truncated 6`() =
            checkSingleCompletion("\\tru{-caret-}\\func", "\\truncated \\data \\func")

    fun `test no keyword completion after instance` () =
            checkKeywordCompletionVariants("\\instance {-caret-}", GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no keyword completion after open` () =
            checkKeywordCompletionVariants("\\open {-caret-}", GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no root keywords completion after wrong state 1`() =
            checkKeywordCompletionVariants("\\func f (a : Nat) : Nat => {-caret-}", GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no root keywords completion after wrong state 2`() =
            checkKeywordCompletionVariants("\\func f (a : Nat) : {-caret-}", GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no root keywords completion after wrong state 3` () =
            checkKeywordCompletionVariants("\\func f ({-caret-}", GLOBAL_STATEMENT_KWS, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no where completion in empty context`() =
            checkKeywordCompletionVariants("{-caret-}", WHERE_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no where completion after import`() =
            checkKeywordCompletionVariants("\\import A\n{-caret-}", WHERE_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no where completion after open`() =
            checkKeywordCompletionVariants("\\open Nat\n{-caret-}", WHERE_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test where completion after func`() =
            checkKeywordCompletionVariants("\\func lol => 0\n{-caret-}", WHERE_KW_LIST, CompletionCondition.CONTAINS)

    fun `test where completion after data`() =
            checkKeywordCompletionVariants("\\data Lol | lol1 | lol2\n{-caret-}", WHERE_KW_LIST, CompletionCondition.CONTAINS)

    fun `test where completion after class`() =
            checkKeywordCompletionVariants("\\class Lol {}\n{-caret-}", WHERE_KW_LIST, CompletionCondition.CONTAINS)

    fun `test where completion after iterated where`() =
            checkKeywordCompletionVariants("\\func foo => 0 \\where \\func bar => 0\n{-caret-}", WHERE_KW_LIST, CompletionCondition.CONTAINS)

    fun `test no where completion after iterated where`() =
            checkKeywordCompletionVariants("\\func foo => 0 \\where {\\func bar => 0}\n{-caret-}", WHERE_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no keyword completion before crlf`() =
            checkKeywordCompletionVariants("\\func foo => 0 {-caret-}\n", GLOBAL_STATEMENT_KWS + WHERE_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test extends completion after class name`() =
            checkCompletionVariants("\\class Lol {-caret-}", EXTENDS_KW_LIST)

    fun `test extends completion after class name 2`() =
            checkSingleCompletion("\\class Lol \\{-caret-}", EXTENDS_KW_LIST[0])

    fun `test extends completion after class name 3`() =
            checkCompletionVariants("\\class Lol {-caret-}{}", EXTENDS_KW_LIST)

    fun `test extends completion after class name 4`() =
            checkSingleCompletion("\\class Lol \\{-caret-}{}", EXTENDS_KW_LIST[0])

    fun `test extends completion after class arguments`() =
            checkCompletionVariants("\\class Lol (n : Nat){-caret-}", EXTENDS_KW_LIST)

    fun `test extends completion after class arguments 2`() =
            checkSingleCompletion("\\class Lol (n : Nat)\\{-caret-}", EXTENDS_KW_LIST[0])

    fun `test extends completion after class arguments 3`() =
            checkCompletionVariants("\\class Lol (n : Nat){-caret-}{}", EXTENDS_KW_LIST)

    fun `test extends completion after class arguments 4`() =
            checkSingleCompletion("\\class Lol (n : Nat)\\{-caret-}{}", EXTENDS_KW_LIST[0])

    fun `test no extends after class without name`() =
            checkKeywordCompletionVariants("\\class {-caret-}{}", EXTENDS_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no extends inside class arguments block`() =
            checkKeywordCompletionVariants("\\class Lol (n : Nat) {-caret-} (m : Nat){}", EXTENDS_KW_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test data universe keywords after semicolon`() =
            checkKeywordCompletionVariants("\\data d1 (n : Nat): {-caret-}", DATA_UNIVERSE_KW + FAKE_NTYPE_LIST)

    fun `test data universe keywords after truncation level 1`() =
            checkCompletionVariants("\\data d1 (n : Nat): \\101{-caret-}", listOf("-Type"))

    fun `test data universe keywords after truncation level 2`() =
            checkCompletionVariants("\\data d1 (n : Nat): \\101-{-caret-}", listOf("Type"))

    fun `test data universe keywords after truncation level 3`() =
            checkSingleCompletion("\\data d1 (n : Nat): \\101-T{-caret-}", "\\101-Type")

    fun `test expression keywords`() =
            checkKeywordCompletionVariants("\\func f => {-caret-}", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST , CompletionCondition.SAME_KEYWORDS)

    fun `test expression keywords 2`() =
            checkKeywordCompletionVariants("\\func f (a : Nat) => f({-caret-})", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test expression keywords 3`() =
            checkKeywordCompletionVariants("\\func f (a : Nat) => \\let a => 101 \\in {-caret-}", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test expression keywords 4`() =
            checkKeywordCompletionVariants("\\func f (a : Nat) => \\let a => {-caret-}\\in 101", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test expression keywords 5`() =
            checkKeywordCompletionVariants("\\func f (a : Nat) => \\Pi ({-caret-})", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test expression keywords 6`() =
            checkKeywordCompletionVariants("\\func f (a : Nat) => \\Sigma ({-caret-})", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test expression keywords 7`() =
            checkKeywordCompletionVariants("\\func lol (a : Nat) => \\Pi \\Set -> {-caret-}", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test only new & universes in application expression 1`() =
            checkCompletionVariants("\\func f (a : Nat) => f {-caret-}", DATA_UNIVERSE_KW + NEW_KW_LIST + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test only new & universes in application expression 2`() =
            checkCompletionVariants("\\func f (a : Nat) => f \\{-caret-}", DATA_UNIVERSE_KW + NEW_KW_LIST + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test normal application completion variants after new expr`() =
            checkCompletionVariants("\\func lol (a : Nat) => (\\new () {-caret-})", DATA_UNIVERSE_KW + NEW_KW_LIST + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test no keyword completion after with`() = checkNoCompletion("\\func lol (a : Nat) => \\case a \\with {-caret-}")

    fun `test no keyword completion after with 2`() = checkNoCompletion("\\func lol (a : Nat) => \\case a \\with \\{-caret-}")

    fun `test no keyword completion after with 3`() = checkNoCompletion("\\func lol (a : Nat) => \\case a \\with {-caret-} { | zero => 0 | suc a' => a'}")

    fun `test no keyword completion after with 4`() = checkNoCompletion("\\func lol (a : Nat) => \\case a \\with \\{-caret-} { | zero => 0 | suc a' => a'}")

    fun `test no keyword completion after with braces`() = checkNoCompletion("\\func lol (a : Nat) => \\case a \\with { | zero => 0 | suc a' => a'}{-caret-}")

    fun `test no keyword completion after with braces 2`() = checkNoCompletion("\\func lol (a : Nat) => \\case a \\with { | zero => 0 | suc a' => a'}\\{-caret-}")

    fun `test only universe keywords after Sigma`() =
            checkKeywordCompletionVariants("\\func lol (a : Nat) => \\Sigma {-caret-} -> \\Type", DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test only universe keywords after Sigma 2`() =
            checkKeywordCompletionVariants("\\func lol (a : Nat) => \\Sigma \\Type {-caret-} -> \\Type", DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test only universe keywords after Pi`() =
            checkKeywordCompletionVariants("\\func lol (a : Nat) => \\Pi {-caret-} -> \\Type", DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test only universe keywords after Pi 2`() =
            checkKeywordCompletionVariants("\\func lol (a : Nat) => \\Pi \\Type {-caret-} -> \\Type", DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test no keywords after new`() =
            checkKeywordCompletionVariants("\\func lol (a : Nat) => (\\new {-caret-})", emptyList(), CompletionCondition.SAME_KEYWORDS)

    fun `test no keywords after let`() =
            checkKeywordCompletionVariants("\\func lol (a : Nat) => (\\let {-caret-})", emptyList(), CompletionCondition.SAME_KEYWORDS)

    fun `test no keywords after lam`() =
            checkKeywordCompletionVariants("\\func lol (a : Nat) => (\\lam {-caret-})", emptyList(), CompletionCondition.SAME_KEYWORDS)

    fun `test universe keywords as typed tele in data (after param)`() =
            checkKeywordCompletionVariants("\\data \\fix 10 lol-data (a : \\Type) {-caret-}", DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test no expression keywords after universe literal`() = // TODO: Rewrite these tests so that they would allow only levels keywords (via SAME_KEYWORDS)
            checkKeywordCompletionVariants("\\func lol (a : Nat) => (\\Set {-caret-})", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no expression keywords after universe literal 2`() = // only levels
            checkKeywordCompletionVariants("\\func lol (a : Nat) => (\\Prop {-caret-})", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no expression keywords after universe literal 3`() = // only levels
            checkKeywordCompletionVariants("\\func lol (a : Nat) => (\\Type {-caret-})", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no expression keywords after universe literal 4`() = // only levels
            checkKeywordCompletionVariants("\\func lol (a : Nat) => (\\1-Type {-caret-})", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no expression keywords after universe literal 5`() = // only levels
            checkKeywordCompletionVariants("\\func lol (a : Nat) => (\\oo-Type {-caret-})", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no expression keywords after universe literal 6`() = // only levels
            checkKeywordCompletionVariants("\\func lol (a : Nat) => (\\Type ({-caret-}))", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no expression keywords after universe literal 7`() = // only levels
            checkKeywordCompletionVariants("\\func lol (a : Nat) => \\Type ({-caret-})", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no expression keywords after universe literal 8`() = // only levels
            checkKeywordCompletionVariants("\\func lol (a : Nat) => \\Type (({-caret-}))", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    /*fun `test no keywords after new even inside braces`() =
            checkCompletionVariants("\\func lol (a : Nat) => (\\new ({-caret-}))", emptyList(), CompletionCondition.SAME_KEYWORDS)

    fun `test no keywords after new even inside braces 2`() =
            checkCompletionVariants("\\func lol (a : Nat) => (\\new (\\{-caret-}))", emptyList(), CompletionCondition.SAME_KEYWORDS)

    fun `test no keywords after new even inside double braces`() =
            checkCompletionVariants("\\func lol (a : Nat) => (\\new (({-caret-})))", emptyList(), CompletionCondition.SAME_KEYWORDS)

    fun `test no keywords after new even inside double braces 2`() =
            checkCompletionVariants("\\func lol (a : Nat) => (\\new ((\\{-caret-})))", emptyList(), CompletionCondition.SAME_KEYWORDS)*/

    fun `test no expression keywords after universe literal 9`() = // nothing (even levels)
            checkKeywordCompletionVariants("\\func lol (a : Nat) => \\Type (\\lp {-caret-})", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.DOES_NOT_CONTAIN)

    /*fun `test no expression keywords after universe literal 10`() = // only levels
            checkKeywordCompletionVariants("\\func lol (a : Nat) => \\Sigma (\\Set {-caret-})", DATA_OR_EXPRESSION_KW + FAKE_NTYPE_LIST, CompletionCondition.DOES_NOT_CONTAIN)*/ //Fixme

    fun `test universe keywords as typed tele in data`() =
            checkKeywordCompletionVariants("\\data lol {-caret-}", DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test universe keywords as typed tele in constructors`() =
            checkKeywordCompletionVariants("\\data DDD | ccc {-caret-}", DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test universe keywords as typed tele in constructors with patterns`() =
            checkKeywordCompletionVariants("\\data DDDD (x : Nat) \\with\n   | zero => cccc {-caret-}", DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)

    fun `test universe keywords as typed tele in class fields`() =
            checkKeywordCompletionVariants("\\class X {\n   | xxxx {-caret-} : xxxx\n}", DATA_UNIVERSE_KW + FAKE_NTYPE_LIST, CompletionCondition.SAME_KEYWORDS)
}