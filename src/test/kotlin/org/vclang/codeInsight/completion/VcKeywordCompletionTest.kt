package org.vclang.codeInsight.completion

import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.FIXITY_KWS
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.STATEMENT_KWS
import org.vclang.codeInsight.completion.VclangCompletionContributor.Companion.GLOBAL_STATEMENT_KWS
import java.util.Collections.singletonList

class VcKeywordCompletionTest : VcCompletionTestBase() {
    private val fixityKws = FIXITY_KWS.map { it.toString() }
    private val statementKws = STATEMENT_KWS.map { it.toString() }
    private val globalStatementKws = GLOBAL_STATEMENT_KWS.map { it.toString() }
    private val importKw = singletonList("\\import")
    private val asKw = singletonList("\\as")
    private val hidingKw = singletonList("\\hiding")
    private val huKw = listOf("\\using", "\\hiding")

    fun `test fixity completion after func`() =
            checkCompletionVariants("\\func {-caret-} test => 0", fixityKws)

    fun `test fixity completion after func 2`() =
            checkCompletionVariants("\\func \\{-caret-} test => 0", fixityKws)

    fun `test fixity completion after func 3`() =
            checkCompletionVariants("\\func {-caret-}test => 0", fixityKws)

    fun `test fixity completion after func 4`() =
            checkCompletionVariants("\\func {-caret-}", fixityKws)

    fun `test fixity completion after func 5`() =
            checkCompletionVariants("\\func \\{-caret-}", fixityKws)

    fun `test fixity completion after class`() =
            checkCompletionVariants("\\class {-caret-} testClass {}", fixityKws)

    fun `test fixity completion after class 2`() =
            checkCompletionVariants("\\class \\{-caret-} testClass {}", fixityKws)

    fun `test fixity completion after class 3`() =
            checkCompletionVariants("\\class {-caret-}testClass {}", fixityKws)

    fun `test fixity completion after class 4`() =
            checkCompletionVariants("\\class \\{-caret-}", fixityKws)

    fun `test fixity completion after data`() =
            checkCompletionVariants("\\data {-caret-} MyNat | myzero", fixityKws)

    fun `test fixity completion after data 2`() =
            checkCompletionVariants("\\data \\{-caret-} MyNat | myzero", fixityKws)

    fun `test fixity completion after data 3`() =
            checkCompletionVariants("\\data {-caret-}MyNat | myzero", fixityKws)

    fun `test fixity completion after data 4`() =
            checkCompletionVariants("\\data \\{-caret-}", fixityKws)

    fun `test fixity completion after as`() =
            checkCompletionVariants("\\import B (lol \\as {-caret-} +)", fixityKws)

    fun `test fixity completion after as 2`() =
            checkCompletionVariants("\\import B (lol \\as \\{-caret-}+)", fixityKws)

    fun `test fixity completion after as 3`() =
            checkCompletionVariants("\\import B (lol \\as {-caret-}+)", fixityKws)

    fun `test fixity completion after simple datatype constructor`() =
            checkCompletionVariants("\\data MyNat | {-caret-} myzero", fixityKws)

    fun `test fixity completion after simple datatype constructor 2`() =
            checkCompletionVariants("\\data MyNat | \\{-caret-} myzero", fixityKws)

    fun `test fixity completion after simple datatype constructor 3`() =
            checkCompletionVariants("\\data MyNat | {-caret-}myzero", fixityKws)

    fun `test fixity completion after datatype constructor with a pattern`() =
            checkCompletionVariants("\\data Fin (n : Nat) \\with | suc n => {-caret-} fzero | suc n => fsuc (Fin n)", fixityKws)

    fun `test fixity completion after datatype constructor with a pattern 2`() =
            checkCompletionVariants("\\data Fin (n : Nat) \\with | suc n => \\{-caret-} fzero | suc n => fsuc (Fin n)", fixityKws)

    fun `test fixity completion after datatype constructor with a pattern 3`() =
            checkCompletionVariants("\\data Fin (n : Nat) \\with | suc n => {-caret-}fzero | suc n => fsuc (Fin n)", fixityKws)

    fun `test fixity completion after class field`() =
            checkCompletionVariants("\\class Monoid (El : \\Set) { | {-caret-} * : El -> El -> El}", fixityKws)

    fun `test fixity completion after class field 2`() =
            checkCompletionVariants("\\class Monoid (El : \\Set) { | \\{-caret-} * : El -> El -> El}", fixityKws)

    fun `test fixity completion after class field 3`() =
            checkCompletionVariants("\\class Monoid (El : \\Set) { | {-caret-}* : El -> El -> El}", fixityKws)

    fun `test fixity completion after class field synonym`() =
            checkCompletionVariants("\\class AddMonoid => Monoid { | * => {-caret-} +}", fixityKws)

    fun `test fixity completion after class field synonym 2`() =
            checkCompletionVariants("\\class AddMonoid => Monoid { | * => \\{-caret-} +}", fixityKws)

    fun `test fixity completion after class field synonym 3`() =
            checkCompletionVariants("\\class AddMonoid => Monoid { | * => {-caret-}+}", fixityKws)

    fun `test no fixity completion in pattern matching`() =
            checkCompletionVariants("\\fun foo (n : Nat) \\elim n | {-caret-} zero =>", fixityKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no fixity completion after func fat arrow`() =
            checkCompletionVariants("\\fun foo (n : Nat) => {-caret-} n ", fixityKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test as completion in namespace command`() =
            checkCompletionVariants("\\import B (lol {-caret-})", asKw)

    fun `test as completion in namespace command 2`() =
            checkSingleCompletion("\\import B (lol \\{-caret-})", "\\as")

    fun `test nsCmd completion in namespace command`() =
            checkCompletionVariants("\\import B (lol) {-caret-}", hidingKw, CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 2`() =
            checkCompletionVariants("\\import B (lol)\n \\{-caret-}", hidingKw, CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 3`() =
            checkCompletionVariants("\\import B {-caret-}", huKw, CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 4`() =
            checkCompletionVariants("\\import B \\{-caret-}", huKw, CompletionCondition.CONTAINS)

    fun `test nsCmd completion in namespace command 5`() =
            checkCompletionVariants("\\import B {-caret-} (lol)", huKw)

    fun `test nsCmd completion in namespace command 6`() =
            checkCompletionVariants("\\import B \\{-caret-} (lol)", huKw)

    fun `test nsCmd completion in namespace command 7`() =
            checkCompletionVariants("\\import B {-caret-}(lol)", huKw)

    fun `test nsCmd completion in namespace command 8`() =
            checkNoCompletion("\\import B {-caret-}\\using (lol)")

    fun `test nsCmd completion in namespace command 9`() =
            checkNoCompletion("\\import B \\using (lol) {-caret-} \\hiding (lol)")

    fun `test nsCmd completion in namespace command 10`() =
            checkNoCompletion("\\import B \\hiding {-caret-} (lol)")

    fun `test nsCmd completion in namespace command 11`() =
            checkNoCompletion("\\import B \\hiding {-caret-}")

    fun `test nsCmd completion in namespace command 12`() =
            checkCompletionVariants("\\import {-caret-}", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test root keywords completion 1`() =
            checkCompletionVariants("\\import B\n {-caret-} \\func foo => 0 \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 2`() =
            checkCompletionVariants("\\import B \\func foo => 0\n {-caret-} \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 3`() =
            checkCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar\n {-caret-} \\func f => 0 \\where { \\func g => 1 } ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 4`() =
            checkCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\n{-caret-} \\func g => 1 } ", statementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 5`() =
            checkCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n {-caret-}}", statementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 6`() =
            checkCompletionVariants("\\import B\n\\{-caret-} \\func foo => 0 \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 7`() =
            checkCompletionVariants("\\import B \\func foo => 0\n \\{-caret-} \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 8`() =
            checkCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar\n \\{-caret-} \\func f => 0 \\where { \\func g => 1 } ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 9`() =
            checkCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\n\\{-caret-} \\func g => 1 } ", statementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 10`() =
            checkCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n \\{-caret-}} ", statementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 11`() =
            checkCompletionVariants("\\import B \\hiding (a)\n{-caret-} \\func foo => 0 \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 12`() =
            checkCompletionVariants("\\import B \\hiding (a)\n\\{-caret-} \\func foo => 0 \\data bar | foobar \\func f => 0 \\where { \\func g => 1 } ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 13`() =
            checkCompletionVariants("\\func f (xs : Nat) : Nat \\elim xs\n | suc x => \\case x \\with {| zero => 0 | suc _ => 1}\n \\{-caret-} ", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root keywords completion 14`() =
            checkCompletionVariants("\\class A {| foo : Nat}\n\\func f => \\new A {| foo => 0 |\n{-caret-} => 1\n}\n", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no import in completion 1`() =
            checkCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\n{-caret-} \\func g => 1 } ", importKw, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no import in completion 2`() =
            checkCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n {-caret-}}", importKw, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no import in completion 3`() =
            checkCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\\func g => 1\n \\{-caret-}} ", importKw, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no import in completion 4`() =
            checkCompletionVariants("\\import B \\func foo => 0 \\data bar | foobar  \\func f => 0 \\where {\n\\{-caret-} \\func g => 1 } ", importKw, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test root completion in empty context`() =
            checkCompletionVariants("{-caret-}", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test root completion in empty context 2`() =
            checkCompletionVariants("\\{-caret-}", globalStatementKws, CompletionCondition.CONTAINS)

    fun `test completion after truncated`() =
            checkCompletionVariants("\\truncated {-caret-}", statementKws.minus("\\data"), CompletionCondition.DOES_NOT_CONTAIN)

    fun `test completion after truncated 2`() =
            checkSingleCompletion("\\tru{-caret-}", "\\truncated \\data")

    fun `test completion after truncated 3`() =
            checkCompletionVariants("\\truncated {-caret-}", singletonList("\\data"), CompletionCondition.CONTAINS)

    fun `test completion after truncated 4`() =
            checkSingleCompletion("\\truncated \\{-caret-}", "\\data")

    fun `test completion after truncated 5`() =
            checkSingleCompletion("\\truncated \\da{-caret-}", "\\data")

    fun `test completion after truncated 6`() =
            checkSingleCompletion("\\tru{-caret-}\\func", "\\truncated \\data \\func")

    fun `test no keyword completion after instance` () =
            checkCompletionVariants("\\instance \\{-caret-}", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no keyword completion after open` () =
            checkCompletionVariants("\\open \\{-caret-}", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no root keywords completion after wrong state 1`() =
            checkCompletionVariants("\\func f (a : Nat) : Nat => \\{-caret-}", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no root keywords completion after wrong state 2`() =
            checkCompletionVariants("\\func f (a : Nat) : \\{-caret-}", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

    fun `test no root keywords completion after wrong state 3` () =
            checkCompletionVariants("\\func f (\\{-caret-}", globalStatementKws, CompletionCondition.DOES_NOT_CONTAIN)

}